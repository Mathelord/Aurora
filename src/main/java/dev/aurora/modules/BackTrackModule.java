package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.TargetPose;
import dev.aurora.network.PacketRelay;
import dev.aurora.network.PlayerAttackPackets;
import dev.aurora.render.GhostBoxRenderer;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;

/** Delays inbound gameplay updates briefly after attacking a player under the crosshair. */
public final class BackTrackModule extends AbstractModule {
    private static final long HIT_GRACE_MS = 500L;

    private final MinecraftBridge minecraft;
    private final PacketRelay relay;
    private final PlayerAttackPackets attackPackets;
    private final ModuleSetting latencyMsMin;
    private final ModuleSetting latencyMsMax;
    private final ModuleSetting range;
    private final ModuleSetting renderServerPosition;
    private final ModuleSetting markerColor;

    private String targetKey;
    private int targetEntityId = -1;
    private long controlUntilMs;
    private int activeLatencyMs;

    public BackTrackModule(MinecraftBridge minecraft, PacketRelay relay) {
        this(minecraft, relay, PlayerAttackPackets.supportedVersions());
    }

    BackTrackModule(MinecraftBridge minecraft, PacketRelay relay, PlayerAttackPackets attackPackets) {
        super("back-track", "BackTrack", "Combat",
                "Briefly delays player updates after an attack so previous positions remain hittable.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.relay = Objects.requireNonNull(relay, "relay");
        this.attackPackets = Objects.requireNonNull(attackPackets, "attackPackets");
        latencyMsMin = setting("latency-ms-min", "Latency Min", 150.0D, 0.0D, 2_500.0D, 25.0D)
                .description("Minimum inbound gameplay delay applied after attacking a valid player.");
        latencyMsMax = setting("latency-ms-max", "Latency Max", 250.0D, 0.0D, 2_500.0D, 25.0D)
                .description("Maximum inbound gameplay delay applied after attacking a valid player.");
        range = setting("range", "Range", 5.0D, 1.0D, 12.0D, 0.1D)
                .description("Maximum distance at which an attacked player can activate BackTrack.");
        renderServerPosition = booleanSetting("render-server-position", "Server Position", true)
                .description("Draws the target's estimated current server-side position.");
        markerColor = colorSetting("marker-color", "Marker Color", 0xFF2828)
                .description("Color of the server-position marker.")
                .visibleWhen(() -> enabled(renderServerPosition));
    }

    @Override
    public void onDisable() {
        stopControlling();
    }

    @Override
    public void onPacket(PacketEvent event) {
        if (event.direction() != PacketEvent.Direction.OUTBOUND || maxLatency() <= 0) {
            return;
        }
        OptionalInt attackedId = attackPackets.attackedEntityId(event.packet());
        if (attackedId.isEmpty()) {
            return;
        }
        AimTarget target = minecraft.crosshairPlayer(range.value()).orElse(null);
        if (target == null) {
            return;
        }
        OptionalInt resolvedId = minecraft.targetEntityId(target.id());
        if (resolvedId.isEmpty() || resolvedId.getAsInt() != attackedId.getAsInt()) {
            return;
        }
        targetKey = target.id();
        targetEntityId = attackedId.getAsInt();
        activeLatencyMs = sampleLatency();
        controlUntilMs = System.currentTimeMillis() + activeLatencyMs + HIT_GRACE_MS;
        requestDelay();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!controlling()) {
            return;
        }
        if (!minecraft.isInGame() || System.currentTimeMillis() >= controlUntilMs
                || minecraft.targetPose(targetKey).isEmpty()) {
            stopControlling();
            return;
        }
        requestDelay();
    }

    @Override
    public void onWorldRender(WorldRenderEvent event) {
        if (!controlling() || !enabled(renderServerPosition)) {
            return;
        }
        TargetPose pose = minecraft.targetPose(targetKey).orElse(null);
        Vec3 displacement = relay.heldDisplacement(targetEntityId);
        if (pose == null || displacement.lengthSquared() <= 1.0e-12D) {
            return;
        }
        Vec3 serverPosition = new Vec3(pose.feetX(), pose.feetY(), pose.feetZ()).add(displacement);
        GhostBoxRenderer.render(event.geometry(), serverPosition, pose.width(), pose.height(),
                (int) Math.round(markerColor.value()));
    }

    boolean controlling() {
        return targetKey != null && targetEntityId >= 0;
    }

    private void requestDelay() {
        relay.request(this, activeLatencyMs, activeLatencyMs + HIT_GRACE_MS);
    }

    private void stopControlling() {
        relay.release(this);
        targetKey = null;
        targetEntityId = -1;
        controlUntilMs = 0L;
        activeLatencyMs = 0;
    }

    private int sampleLatency() {
        int lo = (int) Math.round(latencyMsMin.value());
        int hi = (int) Math.round(latencyMsMax.value());
        if (lo > hi) {
            int swap = lo;
            lo = hi;
            hi = swap;
        }
        return lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }

    private int maxLatency() {
        return (int) Math.round(latencyMsMax.value());
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }
}
