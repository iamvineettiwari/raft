package com.vineet.raft.api.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class NodeStatus {

    private int currentTerm;
    private String currentLeader;
    private String data;

}
