package dev.aurora.api.events;

import dev.aurora.api.Event;

import java.time.Instant;

public record RenderEvent(Instant createdAt, Object context) implements Event {
    public static RenderEvent now(Object context) {
        return new RenderEvent(Instant.now(), context);
    }
}
