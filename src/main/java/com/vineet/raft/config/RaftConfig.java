package com.vineet.raft.config;

import com.vineet.raft.core.node.timer.Timer;
import com.vineet.raft.core.stateMachine.InMemoryStateMachine;
import com.vineet.raft.core.stateMachine.StateMachine;
import com.vineet.raft.storage.FileStorage;
import com.vineet.raft.storage.Storage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class RaftConfig {

    @Bean(name="taskExecutor")
    public Executor taskExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(cores * 50);
        executor.setThreadNamePrefix("RaftTaskExecutor-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(3);
    }

    @Bean
    public Timer timer(ScheduledExecutorService executorService, RaftProperties properties) {
        return new Timer(executorService, properties);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public StateMachine state() {
        return new InMemoryStateMachine();
    }


    @Bean
    public Storage storage(RaftProperties properties) {
        String storageDirectory = properties.getStorageDirectory();

        File storageDir = new File(storageDirectory);

        if (!storageDir.exists()) {
            boolean success = storageDir.mkdirs();

            if (!success) {
                throw new RuntimeException(
                        "Could not create directory " + storageDir.getAbsolutePath()
                );
            }
        }

        return new FileStorage(storageDir);
    }
}
