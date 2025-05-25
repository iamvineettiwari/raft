package com.vineet.raft.storage;

import com.vineet.raft.core.log.LogEntry;
import com.vineet.raft.core.node.state.Metadata;

import java.io.IOException;
import java.util.List;

public interface Storage {
    void saveLogEntries(List<LogEntry> entries) throws IOException;
    void appendLogEntry(LogEntry entry) throws IOException;
    void updateLogEntries(List<LogEntry> entries) throws IOException;
    List<LogEntry> getLogEntries() throws IOException;
    void saveMetaData(Metadata metadata) throws IOException;
    Metadata getMetaData() throws IOException;
}
