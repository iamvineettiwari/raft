package com.vineet.raft.core.stateMachine;

import com.vineet.raft.exceptions.InvalidRequestException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStateMachine implements StateMachine {

    private final Map<String, String> data;

    public InMemoryStateMachine() {
        this.data = new ConcurrentHashMap<>();
    }

    @Override
    public void put(String key, String value) throws InvalidRequestException {
        if (key == null) {
            throw new InvalidRequestException("key can not be null");
        }

        if (value == null) {
            throw new InvalidRequestException("value can not be null");
        }

        this.data.put(key, value);
    }

    @Override
    public String get(String key) throws InvalidRequestException{
        if (key == null) {
            throw new InvalidRequestException("key can not be null");
        }

        return this.data.get(key);
    }

    @Override
    public void remove(String key) throws InvalidRequestException {
        if (key == null) {
            throw new InvalidRequestException("key can not be null");
        }

        if (!this.data.containsKey(key)) {
            return;
        }

        this.data.remove(key);
    }

    @Override
    public String dump() {
        return this.data.toString();
    }

}
