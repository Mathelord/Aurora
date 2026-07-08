package dev.aurora.render;

import dev.aurora.aim.AimAngles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SilentAimCrosshairIndicatorTest {
    @Test
    void pointsRightWhenSilentYawIsRightOfVisualYaw() {
        SilentAimCrosshairIndicator.Direction direction = SilentAimCrosshairIndicator.direction(
                new AimAngles(0.0F, 0.0F), new AimAngles(20.0F, 0.0F));

        assertNotNull(direction);
        assertTrue(direction.x() > 0.99D);
    }

    @Test
    void pointsUpWhenSilentPitchIsAboveVisualPitch() {
        SilentAimCrosshairIndicator.Direction direction = SilentAimCrosshairIndicator.direction(
                new AimAngles(0.0F, 0.0F), new AimAngles(0.0F, -20.0F));

        assertNotNull(direction);
        assertTrue(direction.y() < -0.99D);
    }
}
