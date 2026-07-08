package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.network.KnockbackPackets;
import dev.aurora.network.PacketRelay;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Delays the inbound stream when the local player receives knockback near a combat target. */
public final class KnockbackDelayModule extends AbstractModule {
    private static final int GROUND_TICKS_REQUIRED = 3;
    private static final double TARGET_RANGE = 6.0D;
    private static final double TARGET_FOV = 70.0D;

    private final MinecraftBridge minecraft;
    private final KnockbackPackets knockbackPackets;
    private final PacketRelay relay;
    private final ModuleSetting chance;
    private final ModuleSetting groundDelayMs;
    private final ModuleSetting airDelayMs;

    private volatile int groundTicks;
    private volatile long delayUntilMs;

    public KnockbackDelayModule(MinecraftBridge minecraft, KnockbackPackets knockbackPackets,
                                PacketRelay relay) {
        super("knockback-delay", "Knockback Delay", "Combat",
                "Delays incoming knockback briefly when fighting a player near the crosshair.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.knockbackPackets = Objects.requireNonNull(knockbackPackets, "knockbackPackets");
        this.relay = Objects.requireNonNull(relay, "relay");
        chance = setting("chance", "Chance %", 100.0D, 0.0D, 100.0D, 1.0D)
                .description("Probability that valid incoming knockback opens a delay window.");
        groundDelayMs = setting("ground-delay-ms", "Ground Delay ms", 80.0D, 0.0D, 10_000.0D, 10.0D)
                .description("Delay after being grounded for at least three ticks.");
        airDelayMs = setting("air-delay-ms", "Air Delay ms", 150.0D, 0.0D, 10_000.0D, 10.0D)
                .description("Delay while airborne or before three grounded ticks have elapsed.");
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        relay.release(this);
        resetState();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!minecraft.isInGame()) {
            groundTicks = 0;
            return;
        }
        groundTicks = minecraft.isOnGround()
                ? Math.min(groundTicks + 1, GROUND_TICKS_REQUIRED)
                : 0;
    }

    @Override
    public void onPacket(PacketEvent event) {
        if (event.direction() != PacketEvent.Direction.INBOUND || !minecraft.isInGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < delayUntilMs
                || !knockbackPackets.isOwnKnockback(event.packet(), minecraft.localEntityId())
                || !hasTargetNearCrosshair()
                || !passesChance()) {
            return;
        }
        int delay = groundTicks >= GROUND_TICKS_REQUIRED
                ? (int) Math.round(groundDelayMs.value())
                : (int) Math.round(airDelayMs.value());
        if (delay <= 0) {
            return;
        }
        delayUntilMs = now + delay;
        relay.request(this, delay, delay);
    }

    private boolean hasTargetNearCrosshair() {
        AimContext context = minecraft.aimContext(TARGET_RANGE, true);
        if (!context.available()) {
            return false;
        }
        for (AimTarget target : context.targets()) {
            double yawDelta = Math.abs(wrapDegrees(target.yaw() - context.yaw()));
            double pitchDelta = Math.abs(target.pitch() - context.pitch());
            if (Math.hypot(yawDelta, pitchDelta) <= TARGET_FOV) {
                return true;
            }
        }
        return false;
    }

    private boolean passesChance() {
        int percent = (int) Math.round(chance.value());
        return percent >= 100 || (percent > 0 && ThreadLocalRandom.current().nextInt(100) < percent);
    }

    private void resetState() {
        groundTicks = 0;
        delayUntilMs = 0L;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) wrapped -= 360.0D;
        if (wrapped < -180.0D) wrapped += 360.0D;
        return wrapped;
    }
}
