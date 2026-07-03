package dev.aurora.api.events;

import dev.aurora.api.Event;

import java.time.Instant;

public record PacketEvent(Instant createdAt, Direction direction, Object packet) implements Event {
    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    public static PacketEvent now(Direction direction, Object packet) {
        return new PacketEvent(Instant.now(), direction, packet);
    }
}
