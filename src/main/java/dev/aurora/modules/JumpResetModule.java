package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.network.KnockbackPackets;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Performs a real jump-reset: when the server applies knockback to you, it taps the jump key so you
 * leave the ground on the tick the knockback lands. Being airborne at that moment cuts the
 * horizontal distance you get pushed and lets you re-establish sprint sooner. Driven entirely by
 * real jump-key input.
 */
public final class JumpResetModule extends AbstractModule {
    private final MinecraftBridge minecraft;
    private final KnockbackPackets knockbackPackets;
    private final ModuleSetting delayTicks;
    private final ModuleSetting cooldownTicks;
    private final ModuleSetting chanceMin;
    private final ModuleSetting chanceMax;
    private final ModuleSetting requireSprint;

    // Set from the packet hook, consumed on the next tick.
    private volatile boolean knockbackReceived;

    private int delayRemaining;
    private boolean jumpPending;
    private boolean holdingJump;
    private int cooldownRemaining;

    public JumpResetModule(MinecraftBridge minecraft, KnockbackPackets knockbackPackets) {
        super("jump-reset", "Jump Reset", "Movement",
                "Jumps the moment incoming knockback lands to cut the distance you take. Uses real inputs.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.knockbackPackets = Objects.requireNonNull(knockbackPackets, "knockbackPackets");
        delayTicks = setting("delay-ticks", "Delay Ticks", 0.0D, 0.0D, 6.0D, 1.0D)
                .description("Ticks to wait after receiving knockback before pressing jump.");
        cooldownTicks = setting("cooldown-ticks", "Cooldown Ticks", 10.0D, 0.0D, 40.0D, 1.0D)
                .description("Minimum ticks between automatic jump-reset attempts.");
        chanceMin = setting("chance-min", "Chance Min", 1.0D, 0.0D, 1.0D, 0.01D)
                .description("Minimum randomized probability from 0 to 1 that a valid knockback event triggers a jump reset.");
        chanceMax = setting("chance-max", "Chance Max", 1.0D, 0.0D, 1.0D, 0.01D)
                .description("Maximum randomized probability from 0 to 1 that a valid knockback event triggers a jump reset.");
        requireSprint = booleanSetting("require-sprint", "Require Sprint", false)
                .description("Only performs a jump reset while your player is sprinting.");
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        releaseJump();
        resetState();
    }

    @Override
    public void onPacket(PacketEvent event) {
        if (event.direction() == PacketEvent.Direction.INBOUND
                && knockbackPackets.isOwnKnockback(event.packet(), minecraft.localEntityId())) {
            knockbackReceived = true;
        }
    }

    @Override
    public void onTick(TickEvent event) {
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
        }

        // Release a jump we forced on the previous tick.
        if (holdingJump) {
            releaseJump();
            cooldownRemaining = (int) Math.round(cooldownTicks.value());
            knockbackReceived = false;
            return;
        }

        if (!jumpPending) {
            if (!knockbackReceived) {
                return;
            }
            knockbackReceived = false;
            if (cooldownRemaining > 0 || !canReset()) {
                return;
            }
            delayRemaining = (int) Math.round(delayTicks.value());
            jumpPending = true;
        }

        if (delayRemaining > 0) {
            delayRemaining--;
            return;
        }

        jumpPending = false;
        // Only jump if we are actually on the ground at the firing moment.
        if (!minecraft.isOnGround()) {
            return;
        }
        minecraft.setJumpKeyHeld(true);
        holdingJump = true;
    }

    private boolean canReset() {
        if (!minecraft.isOnGround()) {
            return false;
        }
        if (enabled(requireSprint) && !minecraft.isSprinting()) {
            return false;
        }
        double chanceValue = rollChance();
        return !(chanceValue < 1.0D && ThreadLocalRandom.current().nextDouble() > chanceValue);
    }

    private double rollChance() {
        double min = Math.min(chanceMin.value(), chanceMax.value());
        double max = Math.max(chanceMin.value(), chanceMax.value());
        return max <= min ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }

    private void releaseJump() {
        if (holdingJump) {
            minecraft.setJumpKeyHeld(false);
        }
        holdingJump = false;
    }

    private void resetState() {
        knockbackReceived = false;
        delayRemaining = 0;
        jumpPending = false;
        holdingJump = false;
        cooldownRemaining = 0;
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }
}
