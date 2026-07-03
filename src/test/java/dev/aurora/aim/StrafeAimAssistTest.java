package dev.aurora.aim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrafeAimAssistTest {
    @Test
    void increasesResponseAndMultiplierForSidewaysMovement() {
        StrafeAimAssist.Result result = StrafeAimAssist.plan(
                new Vec3(0.215D, 0.0D, 0.0D),
                new AimAngles(0.0F, 0.0F),
                new AimAngles(45.0F, 0.0F),
                0.35D, 18.0D, 52.0D);

        assertTrue(result.targetResponse() > 18.0D);
        assertTrue(result.rotationMultiplier() > 1.0D);
    }

    @Test
    void staysNeutralWithoutMovement() {
        StrafeAimAssist.Result result = StrafeAimAssist.plan(
                Vec3.ZERO, new AimAngles(0.0F, 0.0F), new AimAngles(90.0F, 0.0F),
                0.35D, 18.0D, 52.0D);

        assertEquals(18.0D, result.targetResponse());
        assertEquals(1.0D, result.rotationMultiplier());
    }
}
