package dev.aurora.input;

import dev.aurora.minecraft.CombatState;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.concurrent.ThreadLocalRandom;

/** One-tick forward-key release used by Eclipse to stop sprinting before an airborne critical hit. */
public final class CritSprintReset {
    private final MinecraftBridge minecraft;
    private boolean active;
    private boolean restoreForwardHeld;
    private long readyAtMs;

    public CritSprintReset(MinecraftBridge minecraft) {
        this.minecraft = minecraft;
    }

    public boolean readyToCrit(CombatState state) {
        if (!active) {
            active = true;
            restoreForwardHeld = minecraft.isForwardKeyPhysicallyDown();
            readyAtMs = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(25L, 51L);
        }
        minecraft.setForwardKeyHeld(false);
        // Most versions stop sprinting through the normal forward-input state machine. Explicitly
        // clear the vanilla sprint state as a mapping-safe fallback; the attack is still deferred,
        // so the STOP_SPRINTING update is emitted before the hit rather than in the hit tick.
        if (state.sprinting()) minecraft.setSprinting(false);
        return !state.sprinting() && System.currentTimeMillis() >= readyAtMs;
    }

    public void complete() {
        if (!active) return;
        active = false;
        minecraft.setForwardKeyHeld(restoreForwardHeld);
    }
}
