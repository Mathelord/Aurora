package dev.aurora.aim;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecoupledAimStateTest {
    private final DecoupledAimState state = DecoupledAimState.get();

    @AfterEach
    void resetState() {
        state.reset();
    }

    @Test
    void keepsVisualAndSilentAnglesIndependent() {
        state.activate(new AimAngles(10.0F, 5.0F));
        state.updateSilentAngles(new AimAngles(45.0F, -12.0F));
        state.applyMouseDelta(20.0D, -10.0D);

        assertTrue(state.isActive());
        assertEquals(13.0F, state.visualAngles().yaw(), 0.001F);
        assertEquals(3.5F, state.visualAngles().pitch(), 0.001F);
        assertEquals(45.0F, state.silentAngles().yaw(), 0.001F);
        assertEquals(-12.0F, state.silentAngles().pitch(), 0.001F);

        assertEquals(13.0F, state.deactivate().yaw(), 0.001F);
        assertFalse(state.isActive());
    }
}
