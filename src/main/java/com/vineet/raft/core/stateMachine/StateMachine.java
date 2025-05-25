package com.vineet.raft.core.stateMachine;

import com.vineet.raft.exceptions.InvalidRequestException;

public interface StateMachine {
    void put(String key, String value) throws InvalidRequestException;
    String get(String key) throws InvalidRequestException;
    void remove(String key) throws InvalidRequestException;
    String dump();
}
