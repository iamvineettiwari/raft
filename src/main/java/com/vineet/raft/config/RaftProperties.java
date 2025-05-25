package com.vineet.raft.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
public class RaftProperties {

    @Value("${id:default-node}")
    private String nodeId;

    @Value("#{'${peers:}'.split(',')}")
    private List<String> peers;

    @Value("${storage:./data/}")
    private String storageDirectory;

    @Value("${enableLog:true}")
    private boolean enableLog;

    @Value("${eTime:0}")
    private long eTime;

    @Value("${hTime:0}")
    private long hTime;

    @Value("${server.port}")
    private int serverPort;

    public String getCurrentNodeAddress() {
        return "localhost:" + serverPort;
    }
}
