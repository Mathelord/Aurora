package dev.aurora.combat;

import dev.aurora.minecraft.CombatState;

/** Per-tick detector for the vanilla conditions that make the next melee hit critical. */
public final class CriticalHitDetector {
    public static final double CRITICAL_COOLDOWN = 0.90D;

    public Detection detect(CombatState state, double attackCooldown) {
        if (state == null || !state.available()) {
            return Detection.unavailable();
        }

        // `airborne` is intentionally permissive: it also treats an ascending jump (positive
        // velocityY, even on the ground-flagged takeoff tick) as airborne so AirCrit *holds*
        // fire until the descending window instead of firing an early ground hit. Being loose
        // here only defers attacks; it never fires a non-crit swing.
        boolean airborne = !state.onGround() || state.velocityY() > 0.02D || state.fallDistance() > 0.0D;
        boolean environmentEligible = state.canBecomeAirCrit();
        // The crit window itself must match vanilla exactly (fallDistance > 0), otherwise we
        // release the attack a tick early and the server declines to crit it.
        boolean descending = state.fallingForCrit();
        boolean timingWindow = airborne && environmentEligible && descending
                && attackCooldown >= CRITICAL_COOLDOWN;
        boolean willCrit = timingWindow && !state.sprinting();
        return new Detection(true, airborne, environmentEligible, descending, timingWindow,
                willCrit, timingWindow && state.sprinting());
    }

    public void reset() {
    }

    public record Detection(boolean available, boolean airborne, boolean environmentEligible,
                            boolean descending, boolean timingWindow, boolean willCrit,
                            boolean needsSprintReset) {
        private static Detection unavailable() {
            return new Detection(false, false, false, false, false, false, false);
        }
    }
}
