package dev.aurora.injector;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class LogBuffer {
    private final int capacity;
    private final ArrayDeque<LogEntry> entries;

    public LogBuffer(int capacity) {
        this.capacity = capacity;
        this.entries = new ArrayDeque<>(capacity);
    }

    public synchronized void add(String level, String message) {
        if (entries.size() == capacity) {
            entries.removeFirst();
        }
        entries.addLast(new LogEntry(Instant.now().toString(), level, message));
    }

    public synchronized List<LogEntry> snapshot() {
        return new ArrayList<>(entries);
    }

    public record LogEntry(String timestamp, String level, String message) {
    }
}
