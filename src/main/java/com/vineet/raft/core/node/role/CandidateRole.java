package com.vineet.raft.core.node.role;

import com.vineet.raft.core.grpc.GrpcClient;
import com.vineet.raft.core.log.Command;
import com.vineet.raft.core.log.LogEntry;
import com.vineet.raft.core.node.state.State;
import com.vineet.raft.exceptions.NoLeaderFoundException;
import com.vineet.raft.grpc.VoteRequest;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CandidateRole implements CurrentRole {
    private static final Logger logger = LoggerFactory.getLogger(CandidateRole.class);

    @Getter
    private final NodeRole role = NodeRole.CANDIDATE;

    private final State currentState;
    private final GrpcClient client;

    public CandidateRole(State currentState, GrpcClient client) {
        this.currentState = currentState;
        this.client = client;
        this.initiateVoteRequest();
        this.tick();
    }

    @Override
    public void executeCommand(Command command) {
        throw new NoLeaderFoundException();
    }

    @Override
    public boolean processLogRequest(String leaderAddress, int leaderTerm, int prefixLength, int prefixTerm, int leaderCommit, List<LogEntry> suffix) {
        logger.info("CANDIDATE received log request from - {}, for term - {}", leaderAddress, leaderTerm);

        if (this.currentState.getCurrentTerm() < leaderTerm) {
            logger.info("CANDIDATE stepping down to follower as current term {} is before received term {}", this.currentState.getCurrentTerm(), leaderTerm);
            this.currentState.becomeFollower(leaderTerm, leaderAddress);
            return this.currentState.getCurrentRole().processLogRequest(leaderAddress, leaderTerm, prefixLength, prefixTerm, leaderCommit, suffix);
        }

        logger.info("CANDIDATE rejected log request from {}", leaderAddress);
        return false;
    }

    @Override
    public void processLogResponse(String followerAddress, int followerTerm, int ackLength, boolean success) {
        logger.info("CANDIDATE rejected log response from {}", followerAddress);
    }

    @Override
    public boolean processVoteRequest(String candidateAddress, int candidateTerm, int candidateLogLength, int candidateLogTerm) {
        if (candidateTerm > this.currentState.getCurrentTerm()) {
            logger.info("CANDIDATE stepping down to FOLLOWER");
            this.currentState.becomeFollower(candidateTerm, candidateAddress);
            return this.currentState.getCurrentRole().processVoteRequest(candidateAddress, candidateTerm, candidateLogLength, candidateLogTerm);
        }

        int lastTerm = 0;
        int currentLogLength = this.currentState.getLogLength();

        if (currentLogLength > 0) {
            lastTerm = this.currentState.getLogAt(currentLogLength - 1).getTerm();
        }

        boolean logIsOkay = ((candidateLogTerm > lastTerm) || (candidateLogTerm == lastTerm && candidateLogLength >= currentLogLength));

        String votedFor = this.currentState.getVotedFor();

        if (this.currentState.getCurrentTerm() == candidateTerm && logIsOkay && (votedFor == null || votedFor.equals(candidateAddress))) {
            this.currentState.updateVotedFor(candidateAddress);
            logger.info("CANDIDATE voted for - {}", candidateAddress);
            return true;
        }

        logger.info("CANDIDATE rejected vote request");
        return false;
    }

    @Override
    public void processVoteResponse(String voterAddress, int voterTerm, boolean success) {
        int currentTerm = this.currentState.getCurrentTerm();

        if (voterTerm == currentTerm && success) {
            this.currentState.updateVotes(voterAddress);

            int totalVotes = this.currentState.getTotalVotes();
            logger.info("CANDIDATE got {} votes", totalVotes);

            if (totalVotes >= this.currentState.getQuorum()) {
                logger.info("CANDIDATE stepping up to LEADER");
                this.currentState.becomeLeader();
            }
        } else if (voterTerm > currentTerm) {
            logger.info("CANDIDATE stepping down to FOLLOWER");
            this.currentState.becomeFollower(voterTerm, voterAddress);
        }
    }

    @Override
    public void tick() {
        this.currentState.resetAndStartElectionTimer(this.currentState::becomeCandidate);
    }


    private void initiateVoteRequest() {
        int logLength = this.currentState.getLogLength();
        int lastTerm = logLength > 0 ? this.currentState.getLogAt(logLength - 1).getTerm() : 0;

        logger.info("Requesting votes from peers - {}", this.currentState.getPeers());
        for (String peer : this.currentState.getPeers()) {
            String currentAddress = this.currentState.getCurrentAddress();
            int term = this.currentState.getCurrentTerm();

            logger.info("Requested vote from peer - {}", peer);
            this.client.sendVoteRequestToFollower(this, peer, VoteRequest.newBuilder().setCandidate(currentAddress).setCandidateTerm(term).setCandidateLogLength(logLength).setCandidateLastTerm(lastTerm).build());

        }
    }

}
