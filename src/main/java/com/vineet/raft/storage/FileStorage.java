package com.vineet.raft.storage;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.vineet.raft.core.log.LogEntry;
import com.vineet.raft.core.node.state.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileStorage implements Storage {
    private final Logger logger = LoggerFactory.getLogger(FileStorage.class);

    private final File logFile;
    private final File metadataFile;
    private final ObjectMapper objectMapper;

    public FileStorage(File storageDirectory) {
        this.objectMapper = new ObjectMapper();
        this.logFile = new File(storageDirectory, "log.json");
        this.metadataFile = new File(storageDirectory, "metadata.json");
    }


    @Override
    public void saveLogEntries(List<LogEntry> entries) throws IOException {
        File tempFile = new File(logFile.getAbsolutePath()+ ".tmp");
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            for (LogEntry entry : entries) {
                writer.println(objectMapper.writeValueAsString(entry));
            }
        }
        Files.move(tempFile.toPath(), logFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void appendLogEntry(LogEntry entry) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            String json = objectMapper.writeValueAsString(entry);
            writer.write(json);
            writer.newLine();
            logger.info("Appended log entry: {}", entry);
        }
    }

    @Override
    public void updateLogEntries(List<LogEntry> entries) throws IOException {
        File tempFile = new File(logFile.getAbsolutePath() + ".tmp");
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            for (LogEntry entry : entries) {
                writer.println(objectMapper.writeValueAsString(entry));
                writer.write("\n");
            }
        }
        Files.move(tempFile.toPath(), logFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public List<LogEntry> getLogEntries() throws IOException {
        List<LogEntry> entries = new ArrayList<>();

        if (!logFile.exists()) {
            this.saveLogEntries(entries);
            return entries;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    LogEntry entry = objectMapper.readValue(line, LogEntry.class);
                    entries.add(entry);
                } catch (Exception e) {
                    logger.error("Failed to parse log line: {}", line, e);
                }
            }

        }

        return entries;
    }

    @Override
    public void saveMetaData(Metadata metadata) throws IOException {
        File tempFile = new File(metadataFile.getAbsolutePath() + ".tmp");
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            String json = objectMapper.writeValueAsString(metadata);
            writer.write(json);
        }
        Files.move(tempFile.toPath(), metadataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Metadata getMetaData() throws IOException {
        if (!metadataFile.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(metadataFile))) {
            String line = reader.readLine();
            if (line == null || line.trim().isEmpty()) {
                return null;
            }

            return objectMapper.readValue(line, Metadata.class);
        }
    }
}
