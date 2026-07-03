package dev.aurora.aim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecoupledMovementSteeringTest {
    @Test
    void rotatesForwardInputRelativeToVisualYaw() {
        DecoupledMovementSteering.Keys input = new DecoupledMovementSteering.Keys(
                true, false, false, false, true, false, true);

        DecoupledMovementSteering.Keys steered = DecoupledMovementSteering.steer(input, 90.0D);

        assertEquals(new DecoupledMovementSteering.Keys(
                false, false, false, true, true, false, true), steered);
    }

    @Test
    void preservesInputWhenAnglesAreAlreadyAligned() {
        DecoupledMovementSteering.Keys input = new DecoupledMovementSteering.Keys(
                true, false, true, false, false, true, false);

        assertEquals(input, DecoupledMovementSteering.steer(input, 0.0D));
    }
}
