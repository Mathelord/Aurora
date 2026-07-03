package dev.aurora.combat;

import dev.aurora.minecraft.CombatState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalHitDetectorTest {
    private final CriticalHitDetector detector = new CriticalHitDetector();

    @Test
    void detectsVanillaCriticalWindow() {
        CriticalHitDetector.Detection detection = detector.detect(state(false, false, 0.2D, -0.1D), 0.90D);

        assertTrue(detection.timingWindow());
        assertTrue(detection.willCrit());
        assertFalse(detection.needsSprintReset());
    }

    @Test
    void reportsSprintResetBeforeCriticalCanLand() {
        CriticalHitDetector.Detection detection = detector.detect(state(false, true, 0.2D, -0.1D), 1.0D);

        assertTrue(detection.timingWindow());
        assertFalse(detection.willCrit());
        assertTrue(detection.needsSprintReset());
    }

    @Test
    void rejectsAscendingAndLowCooldownTicks() {
        assertFalse(detector.detect(state(false, false, 0.0D, 0.2D), 1.0D).timingWindow());
        assertFalse(detector.detect(state(false, false, 0.2D, -0.1D), 0.80D).timingWindow());
    }

    @Test
    void opensWindowOnDownwardVelocityWhenFallDistanceUnavailable() {
        // When fallDistance can't be reflected (reads 0) a real descent still shows as negative
        // velocityY, and the server sees fallDistance > 0 by the time the hit lands, so the
        // window must open rather than hold forever.
        assertTrue(detector.detect(state(false, false, 0.0D, -0.5D), 1.0D).timingWindow());
    }

    @Test
    void staysClosedWhileAirborneWithoutFalling() {
        // Airborne but not descending (velocityY ~0, fallDistance 0) must never open a window.
        for (int tick = 0; tick < 10; tick++) {
            assertFalse(detector.detect(state(false, false, 0.0D, 0.0D), 1.0D).timingWindow());
        }
    }

    private static CombatState state(boolean onGround, boolean sprinting, double fallDistance, double velocityY) {
        return new CombatState(true, onGround, sprinting, fallDistance, velocityY,
                false, false, false, false, false, false, false);
    }
}
