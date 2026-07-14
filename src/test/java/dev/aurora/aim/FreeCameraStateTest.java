package dev.aurora.aim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeCameraStateTest {
    @Test
    void mouseDeltaMovesViewButLeavesBodyFrozen() {
        FreeCameraState state = new FreeCameraState();
        state.activate(new AimAngles(30.0F, 10.0F), new AimAngles(30.0F, 10.0F), 0.0D, 0.0D, 0.0D, false);

        state.applyMouseDelta(100.0D, 40.0D);

        assertEquals(30.0F + 15.0F, state.viewAngles().yaw(), 1.0e-4);
        assertEquals(10.0F + 6.0F, state.viewAngles().pitch(), 1.0e-4);
        // Body stays exactly where it was captured, so the player keeps walking straight.
        assertEquals(30.0F, state.bodyAngles().yaw(), 1.0e-4);
        assertEquals(10.0F, state.bodyAngles().pitch(), 1.0e-4);
    }

    @Test
    void bodyRotationCanFollowAimWithoutMovingDetachedView() {
        FreeCameraState state = new FreeCameraState();
        state.activate(new AimAngles(10.0F, 5.0F), new AimAngles(30.0F, -5.0F),
                0.0D, 0.0D, 0.0D, false);

        state.updateBodyAngles(new AimAngles(80.0F, 12.0F));

        assertEquals(80.0F, state.bodyAngles().yaw(), 1.0e-4);
        assertEquals(12.0F, state.bodyAngles().pitch(), 1.0e-4);
        assertEquals(30.0F, state.viewAngles().yaw(), 1.0e-4);
        assertEquals(-5.0F, state.viewAngles().pitch(), 1.0e-4);
    }

    @Test
    void pitchIsClampedToVerticalLimits() {
        FreeCameraState state = new FreeCameraState();
        state.activate(new AimAngles(0.0F, 0.0F), new AimAngles(0.0F, 0.0F), 0.0D, 0.0D, 0.0D, false);

        state.applyMouseDelta(0.0D, 10_000.0D);

        assertEquals(90.0F, state.viewAngles().pitch(), 1.0e-4);
    }

    @Test
    void repeatedActivateDoesNotResetAnAlreadyMovedCamera() {
        FreeCameraState state = new FreeCameraState();
        state.activate(new AimAngles(0.0F, 0.0F), new AimAngles(0.0F, 0.0F), 1.0D, 2.0D, 3.0D, true);
        state.beginTick();
        state.moveBy(5.0D, 0.0D, 0.0D);

        state.activate(new AimAngles(99.0F, 0.0F), new AimAngles(99.0F, 0.0F), 100.0D, 100.0D, 100.0D, true);

        assertEquals(6.0D, state.interpolatedPosition(1.0D).orElseThrow()[0], 1.0e-6);
        assertEquals(0.0F, state.bodyAngles().yaw(), 1.0e-4);
    }

    @Test
    void positionIsAbsentWhenNotDetached() {
        FreeCameraState state = new FreeCameraState();
        state.activate(new AimAngles(0.0F, 0.0F), new AimAngles(0.0F, 0.0F), 1.0D, 2.0D, 3.0D, false);

        assertTrue(state.interpolatedPosition(1.0D).isEmpty());
        assertFalse(state.freezeMovement());
        // moveBy is a no-op without position detach.
        state.beginTick();
        state.moveBy(10.0D, 10.0D, 10.0D);
        assertTrue(state.interpolatedPosition(1.0D).isEmpty());
    }

    @Test
    void detachedFreecamFreezesMovementAndTracksPosition() {
        FreeCameraState state = new FreeCameraState();
        state.activate(new AimAngles(0.0F, 0.0F), new AimAngles(0.0F, 0.0F), 10.0D, 64.0D, -5.0D, true);

        assertTrue(state.freezeMovement());
        state.beginTick();
        state.moveBy(1.5D, -2.0D, 0.25D);

        double[] pos = state.interpolatedPosition(1.0D).orElseThrow();
        assertEquals(11.5D, pos[0], 1.0e-6);
        assertEquals(62.0D, pos[1], 1.0e-6);
        assertEquals(-4.75D, pos[2], 1.0e-6);
    }

    @Test
    void interpolatesPositionBetweenTicksByPartialTick() {
        FreeCameraState state = new FreeCameraState();
        state.activate(new AimAngles(0.0F, 0.0F), new AimAngles(0.0F, 0.0F), 0.0D, 0.0D, 0.0D, true);

        state.beginTick();
        state.moveBy(4.0D, 0.0D, 0.0D);

        // Half-way between the previous tick (0) and the current tick (4) is 2.
        assertEquals(2.0D, state.interpolatedPosition(0.5D).orElseThrow()[0], 1.0e-6);
        assertEquals(0.0D, state.interpolatedPosition(0.0D).orElseThrow()[0], 1.0e-6);
        assertEquals(4.0D, state.interpolatedPosition(1.0D).orElseThrow()[0], 1.0e-6);
    }

    @Test
    void deactivateClearsActiveAndPosition() {
        FreeCameraState state = new FreeCameraState();
        state.activate(new AimAngles(0.0F, 0.0F), new AimAngles(0.0F, 0.0F), 1.0D, 2.0D, 3.0D, true);

        state.deactivate();

        assertFalse(state.isActive());
        assertFalse(state.freezeMovement());
        assertTrue(state.interpolatedPosition(1.0D).isEmpty());
    }
}
