package com.vineet.raft.core.node.timer;

import com.vineet.raft.config.RaftProperties;
import lombok.Getter;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Timer {
    @Getter
    private final long electionTimerDelay;

    @Getter
    private final long heartBeatTimerInterval;

    private ScheduledFuture<?> electionTimer;
    private ScheduledFuture<?> heartBeatTimer;

    private final ScheduledExecutorService executor;

    public Timer(ScheduledExecutorService executor, RaftProperties properties) {
        this.executor = executor;
        this.electionTimerDelay = properties.getETime() == 0 ? ThreadLocalRandom.current().nextInt(150, 301) : properties.getETime();
        this.heartBeatTimerInterval = properties.getHTime() == 0 ? ThreadLocalRandom.current().nextInt(50, 150) : properties.getHTime();
    }

    public void cancelElectionTimer() {
        this.cancelTimer(this.electionTimer);
    }

    public void cancelHeartBeatTimer() {
        this.cancelTimer(this.heartBeatTimer);
    }

    public void startElectionTimer(Runnable electionTimerAction) {
        cancelTimer(this.electionTimer);

        this.electionTimer = this.executor.schedule(electionTimerAction, this.electionTimerDelay, TimeUnit.MILLISECONDS);
    }

    public void startHeartBeatTimer(Runnable heartBeatTimerAction) {
        cancelTimer(this.heartBeatTimer);

        this.heartBeatTimer = this.executor.scheduleAtFixedRate(heartBeatTimerAction, 0, this.heartBeatTimerInterval, TimeUnit.MILLISECONDS);
    }


    private void cancelTimer(ScheduledFuture<?> timer) {
        if (timer != null && !timer.isCancelled()) {
            timer.cancel(true);
        }
    }

}
