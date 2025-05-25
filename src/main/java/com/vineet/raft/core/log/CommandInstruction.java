package com.vineet.raft.core.log;

public enum CommandInstruction {
    SET, DELETE;

    public com.vineet.raft.grpc.CommandInstruction toGrpcCommandInstruction() {
        return switch (this) {
            case SET -> com.vineet.raft.grpc.CommandInstruction.SET;
            case DELETE -> com.vineet.raft.grpc.CommandInstruction.DELETE;
            default -> null;
        };
    }

    public static CommandInstruction fromGrpcCommandInstruction(com.vineet.raft.grpc.CommandInstruction instruction) {
        return switch (instruction) {
            case SET -> SET;
            case DELETE -> DELETE;
            default -> null;
        };
    }
}
