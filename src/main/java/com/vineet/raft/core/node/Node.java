package com.vineet.raft.core.node;

import com.vineet.raft.core.log.Command;
import com.vineet.raft.core.log.LogEntry;
import com.vineet.raft.core.node.state.StateImpl;
import com.vineet.raft.exceptions.InvalidRequestException;
import com.vineet.raft.grpc.LogResponse;
import com.vineet.raft.grpc.VoteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class Node {

    private final StateImpl currentState;

    @Autowired
    public Node(StateImpl state) {
        this.currentState = state;
    }

    public String getCurrentLeader() {
        return this.currentState.getCurrentLeader();
    }

    public void submitCommand(Command cmd) throws InvalidRequestException {
        this.currentState.getCurrentRole().executeCommand(cmd);
    }

    public LogResponse processLogRequest(
            String leaderAddress,
            int leaderTerm,
            int prefixLength,
            int prefixTerm,
            int leaderCommit,
            List<com.vineet.raft.grpc.LogEntry> suffix
    ) {

        boolean success = this.currentState.getCurrentRole().processLogRequest(
                leaderAddress,
                leaderTerm,
                prefixLength,
                prefixTerm,
                leaderCommit,
                suffix.stream().map(LogEntry::fromGrpcLogEntry).collect(Collectors.toList())
        );

        return LogResponse.newBuilder()
                .setFollower(this.currentState.getCurrentAddress())
                .setFollowerTerm(this.currentState.getCurrentTerm())
                .setAckLength(success ? prefixLength + suffix.size() : 0)
                .setSuccess(success)
                .build();
    }
    public VoteResponse processVoteRequest(
            String candidateAddress,
            int candidateTerm,
            int candidateLogLength,
            int candidateLogTerm
    ) {
        boolean success = this.currentState.getCurrentRole().processVoteRequest(candidateAddress, candidateTerm, candidateLogLength, candidateLogTerm);
        return VoteResponse.newBuilder()
                .setVoter(this.currentState.getCurrentAddress())
                .setVoterTerm(this.currentState.getCurrentTerm())
                .setSuccess(success).build();
    }

    public String getData() {
        return this.currentState.getData();
    }

    public int getCurrentTerm() {
        return this.currentState.getCurrentTerm();
    }
}
