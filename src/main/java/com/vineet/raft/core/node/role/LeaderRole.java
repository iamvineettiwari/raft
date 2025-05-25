package com.vineet.raft.core.node.role;

import com.vineet.raft.core.log.Command;
import com.vineet.raft.core.log.CommandInstruction;
import com.vineet.raft.core.log.LogEntry;
import com.vineet.raft.core.node.state.State;
import com.vineet.raft.exceptions.InvalidRequestException;
import com.vineet.raft.grpc.LogRequest;
import com.vineet.raft.core.grpc.GrpcClient;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class LeaderRole implements CurrentRole {
    private static final Logger logger = LoggerFactory.getLogger(LeaderRole.class);

    @Getter
    private final NodeRole role = NodeRole.LEADER;

    private final State currentState;
    private final Executor taskExecutor;
    private final GrpcClient client;

    public LeaderRole(State currentState, Executor taskExecutor, GrpcClient client) {
        this.currentState = currentState;
        this.taskExecutor = taskExecutor;
        this.client = client;

        for (String peer: this.currentState.getPeers()) {
            this.currentState.updateSentLength(peer, this.currentState.getLogLength());
            this.currentState.updateAckedLength(peer, 0);
        }
        this.tick();
        this.sendAppendEntries();
    }

    @Override
    public void executeCommand(Command command) {
        if (command.getInstruction() != CommandInstruction.SET && command.getInstruction() != CommandInstruction.DELETE) {
            throw new InvalidRequestException("Command not supported");
        }

        LogEntry entry = LogEntry.builder().command(command).term(currentState.getCurrentTerm()).build();
        this.currentState.addLog(entry);
        this.currentState.appendLogPersist(entry);

        this.currentState.updateAckedLength(
                this.currentState.getCurrentAddress(),
                this.currentState.getLogLength()
        );

        logger.info("Command processed - {}", command.toString());
        this.sendAppendEntries();
    }

    @Override
    public boolean processLogRequest(String leaderAddress, int leaderTerm, int prefixLength, int prefixTerm, int leaderCommit, List<LogEntry> suffix) {
        logger.info("LEADER received log request from - {}, for term - {}", leaderAddress, leaderTerm);

        if (this.currentState.getCurrentTerm() < leaderTerm) {
            logger.info("LEADER stepping down to follower as current term {} is before received term {}", this.currentState.getCurrentTerm(), leaderTerm);
            this.currentState.becomeFollower(leaderTerm, leaderAddress);
            return this.currentState.getCurrentRole().processLogRequest(leaderAddress, leaderTerm, prefixLength, prefixTerm, leaderCommit, suffix);
        }

        logger.info("LEADER rejected log request from {}", leaderAddress);
        return false;
    }

    @Override
    public void processLogResponse(String followerAddress, int followerTerm, int ackLength, boolean success) {
        logger.info("LEADER received log response from - {} with term - {}, ackLength - {} and success - {}", followerAddress, followerTerm, ackLength, success);

        int currentTerm = this.currentState.getCurrentTerm();

        if (followerTerm > currentTerm) {
            logger.info("LEADER stepping down to follower as current term {} is before received term {}", this.currentState.getCurrentTerm(), followerTerm);
            this.currentState.becomeFollower(followerTerm, followerAddress);
            return;
        }

        if (followerTerm != currentTerm) {
            logger.info("LEADER rejected log response from {}", followerAddress);
            return;
        }

        if (!success) {
            int lastSent = this.currentState.getSentLength(followerAddress);

            if (lastSent > 0) {
                logger.info("LEADER retrying before logs");
                this.currentState.updateSentLength(followerAddress, lastSent - 1);
                taskExecutor.execute(() -> this.sendAppendEntryToFollower(followerAddress));
            }

            return;
        }

        if (ackLength > this.currentState.getAckedLength(followerAddress)) {
            this.currentState.updateSentLength(followerAddress, ackLength);
            this.currentState.updateAckedLength(followerAddress, ackLength);
            this.commitLogEntries();
        }
    }

    @Override
    public boolean processVoteRequest(String candidateAddress, int candidateTerm, int candidateLogLength, int candidateLogTerm) {
        if (candidateTerm <= this.currentState.getCurrentTerm()) {
            logger.info("LEADER rejected vote request from {}", candidateAddress);
            return false;
        }

        logger.info("LEADER stepping down to FOLLOWER");
        this.currentState.becomeFollower(candidateTerm, candidateAddress);
        return this.currentState.getCurrentRole().processVoteRequest(candidateAddress, candidateTerm, candidateLogLength, candidateLogTerm);
    }

    @Override
    public void processVoteResponse(String voterAddress, int voterTerm, boolean success) {
        if (voterTerm > this.currentState.getCurrentTerm()) {
            logger.info("LEADER stepping down to FOLLOWER");
            this.currentState.becomeFollower(voterTerm, voterAddress);
        }
    }

    @Override
    public void tick() {
        this.currentState.resetAndStartHeartbeatTimer(() -> {
            if (!(this.currentState.getCurrentRole().getRole().equals(NodeRole.LEADER))) {
                this.currentState.cancelHeartbeatTimer();
                return;
            }
            this.sendAppendEntries();
        });
    }

    private void sendAppendEntries() {
        for (String follower : this.currentState.getPeers()) {
            taskExecutor.execute(() -> this.sendAppendEntryToFollower(follower));
        }
    }

    private void sendAppendEntryToFollower(String followerAddress) {
        try {
            int prefixLength = this.currentState.getSentLength(followerAddress);

            List<com.vineet.raft.grpc.LogEntry> suffix = new ArrayList<>();

            for (int i = prefixLength; i < this.currentState.getLogLength(); i++) {
                LogEntry entry = this.currentState.getLogAt(i);

                suffix.add(entry.toGrpcLogEntry());
            }

            int prefixTerm = 0;

            if (prefixLength > 0) {
                LogEntry lastLog = this.currentState.getLogAt(prefixLength - 1);

                if (lastLog != null) {
                    prefixTerm = lastLog.getTerm();
                }
            }

            logger.info("Sending log to follower - {}", followerAddress);

            this.client.sendLogToFollower(
                    this,
                    followerAddress,
                    LogRequest
                            .newBuilder()
                            .setLeader(this.currentState.getCurrentAddress())
                            .setLeaderTerm(this.currentState.getCurrentTerm())
                            .setPrefixLength(prefixLength)
                            .setPrefixTerm(prefixTerm)
                            .setCommitLength(this.currentState.getCommitLength())
                            .addAllSuffix(suffix)
                            .build()
            );
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void commitLogEntries() {
        int newCommitLength = this.currentState.getCommitLength();
        int logLength = this.currentState.getLogLength();
        int requiredAcks = this.currentState.getQuorum() - 1;

        while (newCommitLength < logLength) {
            int totalAcks = 0;

            for (String follower : this.currentState.getPeers()) {
                if (this.currentState.getAckedLength(follower) > newCommitLength) {
                    totalAcks += 1;
                }
            }

            LogEntry entryToCommit = this.currentState.getLogAt(newCommitLength);

            if (entryToCommit.getTerm() != this.currentState.getCurrentTerm()){
                logger.info("LEADER skipping prev term commits");
                break;
            }

            if (totalAcks < requiredAcks) {
                logger.info("Acks are {}, required {}", totalAcks, requiredAcks);
                break;
            }

            newCommitLength += 1;
        }

        if (newCommitLength > this.currentState.getCommitLength()) {
            this.currentState.updateCommitLength(newCommitLength);
            taskExecutor.execute(this.currentState::updateStateMachine);
        }
    }

}
