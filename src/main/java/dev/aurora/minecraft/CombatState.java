package dev.aurora.minecraft;

/** Local-player state needed to reproduce vanilla airborne critical-hit timing. */
public record CombatState(boolean available, boolean onGround, boolean sprinting, double fallDistance,
                          double velocityY, boolean climbing, boolean touchingWater, boolean inLava,
                          boolean blindness, boolean slowFalling, boolean levitation, boolean hasVehicle) {
    public static CombatState unavailable() {
        return new CombatState(false, true, false, 0.0D, 0.0D, false, false, false,
                false, false, false, false);
    }

    public boolean canBecomeAirCrit() {
        return available && !climbing && !touchingWater && !inLava && !blindness
                && !slowFalling && !levitation && !hasVehicle;
    }

    public boolean fallingForCrit() {
        // fallDistance > 0 is vanilla's exact crit gate; velocityY < -0.08 is a robust fallback
        // for runtimes where fallDistance can't be reflected. The -0.08 threshold skips the apex
        // tick, so it only trips on real descent (the server then sees fallDistance > 0 → crit).
        return fallDistance > 0.0D || velocityY < -0.08D;
    }
}
