package dev.aurora.api.events;

import dev.aurora.api.Event;
import dev.aurora.render.WorldGeometryBatch;

import java.time.Instant;

/** World-space render pass with geometry submitted through Minecraft's 3D vertex pipeline. */
public record WorldRenderEvent(Instant createdAt, WorldGeometryBatch geometry) implements Event {
    public static WorldRenderEvent now(WorldGeometryBatch geometry) {
        return new WorldRenderEvent(Instant.now(), geometry);
    }
}
