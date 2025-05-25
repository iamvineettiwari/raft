package com.vineet.raft.core.node.role;

import com.vineet.raft.core.log.Command;
import com.vineet.raft.core.log.LogEntry;
import com.vineet.raft.core.node.state.State;
import com.vineet.raft.exceptions.InvalidRequestException;
import com.vineet.raft.exceptions.NoLeaderFoundException;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.Executor;

public class FollowerRole implements CurrentRole {
    private static final Logger logger = LoggerFactory.getLogger(FollowerRole.class);

    @Getter
    private final NodeRole role = NodeRole.FOLLOWER;

    private final State currentState;
    private final Executor taskExecutor;
    private final RestTemplate restTemplate;

    public FollowerRole(State currentState, Executor taskExecutor, RestTemplate restTemplate) {
        this.currentState = currentState;
        this.taskExecutor = taskExecutor;
        this.restTemplate = restTemplate;

        this.tick();
    }

    @Override
    public void executeCommand(Command command) {
        String leader = this.currentState.getCurrentLeader();

        if (leader == null) {
            throw new NoLeaderFoundException();
        }

        try {

            ResponseEntity<String> resp = this.restTemplate.postForEntity("http://" + leader + "/command", command.toClientCommand(), String.class);

            if (resp.getStatusCode() != HttpStatus.OK) {
                throw new InvalidRequestException(resp.getBody());
            }
        } catch (Exception e) {
            throw new InvalidRequestException("Failed to forward request to leader - " + leader);
        }
    }

    @Override
    public boolean processLogRequest(String leaderAddress, int leaderTerm, int prefixLength, int prefixTerm, int leaderCommit, List<LogEntry> suffix) {
        logger.info("FOLLOWER received log request from - {}, for term - {}", leaderAddress, leaderTerm);

        if (leaderTerm < this.currentState.getCurrentTerm()) {
            logger.info("FOLLOWER rejected log request due to stale term");
        }

        if (leaderTerm >= this.currentState.getCurrentTerm()) {
            this.tick();
            this.currentState.updateCurrentTerm(leaderTerm);
            this.currentState.updateCurrentLeader(leaderAddress);
        }

        if (leaderTerm > this.currentState.getCurrentTerm()) {
            this.currentState.updateVotedFor(null);
        }

        boolean isLogOkay = (prefixLength == 0 || this.currentState.getLogLength() >= prefixLength) && (prefixLength == 0 || this.currentState.getLogAt(prefixLength - 1).getTerm() == prefixTerm) && leaderTerm == this.currentState.getCurrentTerm();

        if (!isLogOkay) {
            logger.info("FOLLOWER rejected log request due to invalid log");
            return false;
        }

        this.appendEntries(prefixLength, leaderCommit, suffix);
        return true;
    }

    @Override
    public void processLogResponse(String followerAddress, int followerTerm, int ackLength, boolean success) {
        logger.info("FOLLOWER rejected response from {}", followerAddress);
    }

    @Override
    public boolean processVoteRequest(String candidateAddress, int candidateTerm, int candidateLogLength, int candidateLogTerm) {
        logger.info("FOLLOWER received vote request from - {}", candidateAddress);

        if (candidateTerm < this.currentState.getCurrentTerm()) {
            logger.info("FOLLOWER rejected vote request due to stale term");
        }

        if (candidateTerm > this.currentState.getCurrentTerm()) {
            this.currentState.updateCurrentTerm(candidateTerm);
            this.currentState.updateVotedFor(null);
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
            logger.info("FOLLOWER voted for - {}", candidateAddress);
            return true;
        }

        logger.info("FOLLOWER rejected vote request. LogOkay {}, VotedFor - {}", logIsOkay, votedFor);
        return false;
    }

    @Override
    public void processVoteResponse(String voterAddress, int voterTerm, boolean success) {
        logger.info("FOLLOWER rejected vote response from - {}", voterAddress);
    }

    @Override
    public void tick() {
        this.currentState.resetAndStartElectionTimer(this.currentState::becomeCandidate);
    }

    private void appendEntries(int prefixLength, int leaderCommit, List<LogEntry> suffix) {
        int logLength = this.currentState.getLogLength();

        if (!suffix.isEmpty() && logLength > prefixLength) {
            int lastIndex = Math.min(logLength, prefixLength + suffix.size()) - 1;

            if (this.currentState.getLogAt(lastIndex).getTerm() != suffix.get(lastIndex - prefixLength).getTerm()) {
                this.currentState.truncateLogs(prefixLength, logLength);
            }
        }

        if (prefixLength + suffix.size() > logLength) {
            for (int i = Math.max(0, logLength - prefixLength); i < suffix.size(); i++) {
                this.currentState.addLog(suffix.get(i));
            }
        }

        int curCommitLength = this.currentState.getCommitLength();

        if (leaderCommit > curCommitLength) {
            this.currentState.updateCommitLength(Math.min(leaderCommit, this.currentState.getLogLength()));

            taskExecutor.execute(() -> {
                for (int i = curCommitLength; i < this.currentState.getCommitLength(); i++) {
                    this.currentState.appendLogPersist(this.currentState.getLogAt(i));
                }

                this.currentState.updateStateMachine();
            });
        }
    }

}
