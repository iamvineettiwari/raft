package com.vineet.raft.core.node.state;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Metadata {

    private int currentTerm;
    private int commitLength;
    private String votedFor;


}
