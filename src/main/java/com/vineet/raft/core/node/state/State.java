package com.vineet.raft.core.node.state;

import com.vineet.raft.core.log.LogEntry;
import com.vineet.raft.core.node.role.CurrentRole;
import com.vineet.raft.core.node.role.NodeRole;
import lombok.extern.java.Log;

import java.util.List;

public interface State {
    void init();

    void updateCurrentTerm(int currentTerm);

    void updateCommitLength(int commitLength);

    void updateVotedFor(String votedFor);

    void addLog(LogEntry logEntry);

    void appendLogPersist(LogEntry logEntry);

    void truncateLogs(int start, int end);

    void updateCurrentLeader(String leader);

    void updateStateMachine();

    void updateVotes(String follower);

    void updateSentLength(String follower, Integer sentLength);

    void updateAckedLength(String follower, Integer ackLength);

    void clearVotes();

    void cancelHeartbeatTimer();

    void cancelElectionTimer();

    void resetAndStartHeartbeatTimer(Runnable callback);

    void resetAndStartElectionTimer(Runnable callback);

    String getCurrentAddress();

    Integer getLogLength();

    List<String> getPeers();

    Integer getQuorum();

    String getData();

    Integer getCurrentTerm();

    Integer getCommitLength();

    String getVotedFor();

    String getCurrentLeader();

    Integer getTotalVotes();

    Integer getSentLength(String follower);

    Integer getAckedLength(String follower);

    LogEntry getLogAt(int index);

    CurrentRole getCurrentRole();

    void becomeCandidate();

    void becomeFollower(int term, String leaderAddress);

    void becomeLeader();
}
