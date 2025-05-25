package com.vineet.raft.core.log;

import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    private int term;
    private Command command;

    public com.vineet.raft.grpc.LogEntry toGrpcLogEntry() {
        return com.vineet.raft.grpc.LogEntry.newBuilder()
                .setCommand(command.toGrpcCommand())
                .setTerm(term)
                .build();
    }

    public static LogEntry fromGrpcLogEntry(com.vineet.raft.grpc.LogEntry entry) {
        return LogEntry.builder()
                .term(entry.getTerm())
                .command(Command.fromGrpcCommand(entry.getCommand()))
                .build();
    }

}
