package com.vineet.raft.core.node.state;

import com.vineet.raft.config.RaftProperties;
import com.vineet.raft.core.log.Command;
import com.vineet.raft.core.log.CommandInstruction;
import com.vineet.raft.core.log.LogEntry;
import com.vineet.raft.core.node.Node;
import com.vineet.raft.core.node.role.CandidateRole;
import com.vineet.raft.core.node.role.CurrentRole;
import com.vineet.raft.core.node.role.FollowerRole;
import com.vineet.raft.core.node.role.LeaderRole;
import com.vineet.raft.core.node.timer.Timer;
import com.vineet.raft.core.stateMachine.StateMachine;
import com.vineet.raft.core.grpc.GrpcClient;
import com.vineet.raft.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class StateImpl implements State {
    private static final Logger logger = LoggerFactory.getLogger(StateImpl.class);

    private final ReadWriteLock metaDataLock = new ReentrantReadWriteLock();
    private final ReadWriteLock currentRoleLock = new ReentrantReadWriteLock();
    private final ReadWriteLock currentLeaderLock = new ReentrantReadWriteLock();
    private final StateMachine stateMachine;
    private final ReadWriteLock stateMachineLock = new ReentrantReadWriteLock();
    private final Storage storage;
    private final RaftProperties properties;
    private final Timer timer;
    private final List<LogEntry> logs = new CopyOnWriteArrayList<>();
    private final Set<String> votesReceived = new CopyOnWriteArraySet<>();
    private final Map<String, Integer> sentLength = new ConcurrentHashMap<>();
    private final Map<String, Integer> ackedLength = new ConcurrentHashMap<>();
    private final Executor taskExecutor;
    private final RestTemplate restTemplate;
    private final GrpcClient client;
    private Metadata metadata = new Metadata();
    private CurrentRole currentRole;
    private String currentLeader;

    @Autowired
    public StateImpl(StateMachine stateMachine, Storage storage, RaftProperties properties, Timer timer, @Qualifier("taskExecutor") Executor taskExecutor, RestTemplate restTemplate, GrpcClient client) {
        this.stateMachine = stateMachine;
        this.storage = storage;
        this.properties = properties;
        this.timer = timer;
        this.taskExecutor = taskExecutor;
        this.restTemplate = restTemplate;
        this.client = client;

        this.init();
        this.becomeFollower(this.getCurrentTerm(), this.getCurrentLeader());

        logger.info("Node - {}", properties.getNodeId());
        logger.info("Peers - {}", properties.getPeers());
        logger.info("Storage - {}", properties.getStorageDirectory());
        logger.info("Current Address - {}", properties.getCurrentNodeAddress());
        logger.info("Heartbeat Interval - {}ms", this.timer.getHeartBeatTimerInterval());
        logger.info("Election Timer - {}ms", this.timer.getElectionTimerDelay());
    }

    @Override
    public void init() {
        this.initMetaData();
        this.loadLogs();
        this.updateStateMachine();
    }

    @Override
    public void updateCurrentTerm(int currentTerm) {
        this.metaDataLock.writeLock().lock();

        try {
            this.metadata.setCurrentTerm(currentTerm);
        } finally {
            this.metaDataLock.writeLock().unlock();
            this.persistMetaData();
        }
    }

    @Override
    public void updateCommitLength(int commitLength) {
        this.metaDataLock.writeLock().lock();

        try {
            this.metadata.setCommitLength(commitLength);
        } finally {
            this.metaDataLock.writeLock().unlock();
            this.persistMetaData();
        }
    }

    @Override
    public void updateVotedFor(String votedFor) {
        this.metaDataLock.writeLock().lock();

        try {
            this.metadata.setVotedFor(votedFor);
        } finally {
            this.metaDataLock.writeLock().unlock();
            this.persistMetaData();
        }
    }

    @Override
    public void addLog(LogEntry logEntry) {
        this.logs.add(logEntry);
    }

    @Override
    public void appendLogPersist(LogEntry logEntry) {
        try {
            this.storage.appendLogEntry(logEntry);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void truncateLogs(int start, int end) {
        this.logs.subList(start, end).clear();

        try {
            this.storage.updateLogEntries(this.logs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateCurrentLeader(String leader) {
        this.currentLeaderLock.writeLock().lock();

        try {
            this.currentLeader = leader;
        } finally {
            this.currentLeaderLock.writeLock().unlock();
        }
    }

    @Override
    public void updateStateMachine() {
        this.stateMachineLock.writeLock().lock();

        try {
            for (int i = 0; i < Math.min(logs.size(), this.getCommitLength()); i++) {
                Command cmd = logs.get(i).getCommand();

                if (cmd == null) {
                    continue;
                }

                if (cmd.getInstruction() == CommandInstruction.SET) {
                    this.stateMachine.put(cmd.getKey(), cmd.getValue());
                } else if (cmd.getInstruction() == CommandInstruction.DELETE) {
                    this.stateMachine.remove(cmd.getKey());
                } else {
                    logger.info("Command not supported: {}, key - {}, value - {}", cmd.getInstruction(), cmd.getKey(), cmd.getValue());
                }
            }

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            this.stateMachineLock.writeLock().unlock();
            logger.info("Logs applied to stateMachine machine");
        }
    }

    @Override
    public void updateVotes(String follower) {
        this.votesReceived.add(follower);
    }

    @Override
    public void updateSentLength(String follower, Integer sentLength) {
        this.sentLength.put(follower, sentLength);
    }

    @Override
    public void updateAckedLength(String follower, Integer ackLength) {
        this.ackedLength.put(follower, ackLength);
    }

    @Override
    public void clearVotes() {
        this.votesReceived.clear();
    }

    @Override
    public void cancelHeartbeatTimer() {
        this.timer.cancelHeartBeatTimer();
    }

    @Override
    public void cancelElectionTimer() {
        this.timer.cancelElectionTimer();
    }

    @Override
    public void resetAndStartHeartbeatTimer(Runnable callback) {
        this.timer.startHeartBeatTimer(callback);
    }

    @Override
    public void resetAndStartElectionTimer(Runnable callback) {
        this.timer.startElectionTimer(callback);
    }

    @Override
    public String getCurrentAddress() {
        return this.properties.getCurrentNodeAddress();
    }

    @Override
    public Integer getLogLength() {
        return this.logs.size();
    }

    @Override
    public List<String> getPeers() {
        return this.properties.getPeers();
    }

    @Override
    public Integer getQuorum() {
        int totalNodes = this.properties.getPeers().size() + 1;
        int quorum = (totalNodes / 2) + 1;
        logger.info("Required Quorum - {}", quorum);
        return quorum;
    }

    @Override
    public String getData() {
        this.stateMachineLock.readLock().lock();

        try {
            return this.stateMachine.dump();
        } finally {
            this.stateMachineLock.readLock().unlock();
        }
    }

    @Override
    public Integer getCurrentTerm() {
        this.metaDataLock.readLock().lock();

        try {
            return this.metadata.getCurrentTerm();
        } finally {
            this.metaDataLock.readLock().unlock();
        }
    }

    @Override
    public Integer getCommitLength() {
        this.metaDataLock.readLock().lock();

        try {
            return this.metadata.getCommitLength();
        } finally {
            this.metaDataLock.readLock().unlock();
        }
    }

    @Override
    public String getVotedFor() {
        this.metaDataLock.readLock().lock();

        try {
            return this.metadata.getVotedFor();
        } finally {
            this.metaDataLock.readLock().unlock();
        }
    }

    @Override
    public String getCurrentLeader() {
        this.currentLeaderLock.readLock().lock();

        try {
            return this.currentLeader;
        } finally {
            this.currentLeaderLock.readLock().unlock();
        }
    }

    @Override
    public Integer getTotalVotes() {
        return this.votesReceived.size();
    }

    @Override
    public Integer getSentLength(String follower) {
        return this.sentLength.getOrDefault(follower, 0);
    }

    @Override
    public Integer getAckedLength(String follower) {
        return this.ackedLength.getOrDefault(follower, 0);
    }

    @Override
    public LogEntry getLogAt(int index) {
        if (index >= this.logs.size()) {
            return null;
        }

        return this.logs.get(index);
    }

    @Override
    public CurrentRole getCurrentRole() {
        this.currentRoleLock.readLock().lock();
        try {
            return this.currentRole;
        } finally {
            this.currentRoleLock.readLock().unlock();
        }
    }

    @Override
    public void becomeCandidate() {
        this.currentRoleLock.writeLock().lock();

        this.resetTimers();

        try {
            this.clearVotes();
            this.updateCurrentTerm(this.getCurrentTerm() + 1);
            this.updateVotedFor(this.getCurrentAddress());
            this.updateVotes(this.getCurrentAddress());

            this.currentRole = new CandidateRole(this, client);
            logger.info("Changed role to CANDIDATE");
        } finally {
            this.currentRoleLock.writeLock().unlock();
        }

    }

    @Override
    public void becomeFollower(int term, String leaderAddress) {
        this.currentRoleLock.writeLock().lock();
        this.resetTimers();

        try {
            if (term > this.getCurrentTerm()) {
                this.updateCurrentTerm(term);
                this.updateVotedFor(null);
            }

            this.updateCurrentLeader(leaderAddress);
            this.clearVotes();

            this.currentRole = new FollowerRole(this, taskExecutor, restTemplate);
            logger.info("Changed role to FOLLOWER");
        } finally {
            this.currentRoleLock.writeLock().unlock();
        }
    }

    @Override
    public void becomeLeader() {
        this.currentRoleLock.writeLock().lock();

        this.resetTimers();

        try {
            this.updateCurrentLeader(this.getCurrentAddress());
            this.clearVotes();

            this.currentRole = new LeaderRole(this, taskExecutor, client);
            logger.info("Changed role to LEADER");
        } finally {
            this.currentRoleLock.writeLock().unlock();
        }
    }

    private void loadLogs() {
        try {
            List<LogEntry> storageLogs = this.storage.getLogEntries();
            this.logs.clear();

            for (LogEntry log : storageLogs) {
                this.addLog(log);
            }
        } catch (IOException e) {
            logger.error("Failed to load logs", e);
            throw new RuntimeException("Failed to load logs", e);
        }
    }

    private void initMetaData() {
        try {
            Metadata savedMetaData = this.storage.getMetaData();

            if (savedMetaData == null) {
                logger.info("Saved metadata not found");
                savedMetaData = new Metadata();
                this.storage.saveMetaData(this.metadata);
            }

            this.metadata = savedMetaData;
            logger.info("Initialized metadata - {}", this.metadata);
        } catch (IOException e) {
            logger.error("Failed to initialize metadata", e);
            throw new RuntimeException("Failed to initialize metadata", e);
        }
    }

    private void persistMetaData() {
        try {
            logger.info("Persisting metadata - {}", this.metadata);
            this.storage.saveMetaData(this.metadata);
        } catch (IOException e) {
            logger.error("Failed to save metadata", e);
        }
    }

    private void resetTimers() {
        this.timer.cancelElectionTimer();
        this.timer.cancelHeartBeatTimer();
    }
}
