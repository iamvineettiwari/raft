package com.vineet.raft.core.node.role;

import com.vineet.raft.core.log.Command;
import com.vineet.raft.core.log.LogEntry;
import com.vineet.raft.exceptions.InvalidRequestException;
import com.vineet.raft.exceptions.NoLeaderFoundException;

import java.util.List;

public interface CurrentRole {
    NodeRole getRole();

    void executeCommand(Command command) throws NoLeaderFoundException, InvalidRequestException;

    boolean processLogRequest(
            String leaderAddress,
            int leaderTerm,
            int prefixLength,
            int prefixTerm,
            int leaderCommit,
            List<LogEntry> suffix
    );

    void processLogResponse(
            String followerAddress,
            int followerTerm,
            int ackLength,
            boolean success
    );

    boolean processVoteRequest(
            String candidateAddress,
            int candidateTerm,
            int candidateLogLength,
            int candidateLogTerm
    );

    void processVoteResponse(
            String voterAddress,
            int voterTerm,
            boolean success
    );

    void tick();
}
