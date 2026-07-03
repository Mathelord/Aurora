package dev.aurora.api.events;

import dev.aurora.api.Event;

import java.time.Instant;

public record TickEvent(Instant createdAt) implements Event {
    public static TickEvent now() {
        return new TickEvent(Instant.now());
    }
}
