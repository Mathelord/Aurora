package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.ModuleSetting;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeLookModuleTest {
    @Test
    void enableCapturesBodyAngleAndDisableDeactivates() {
        FreeLookModule module = new FreeLookModule(new FakeBridge(45.0F, 12.0F));

        module.setEnabled(true);
        assertTrue(module.state().isActive());
        assertEquals(45.0F, module.state().bodyAngles().yaw(), 1.0e-4);
        assertEquals(12.0F, module.state().bodyAngles().pitch(), 1.0e-4);
        // Free Look never detaches the camera position.
        assertFalse(module.state().freezeMovement());
        assertTrue(module.state().interpolatedPosition(1.0D).isEmpty());

        module.setEnabled(false);
        assertFalse(module.state().isActive());
    }

    @Test
    void enableSwitchesToThirdPersonAndDisableRestores() {
        FakeBridge bridge = new FakeBridge(0.0F, 0.0F);
        bridge.perspective = 0; // first person
        FreeLookModule module = new FreeLookModule(bridge);

        module.setEnabled(true);
        assertEquals(1, bridge.perspective); // third person back

        module.setEnabled(false);
        assertEquals(0, bridge.perspective); // restored
    }

    @Test
    void doesNotSwitchPerspectiveWhenThirdPersonOptionDisabled() {
        FakeBridge bridge = new FakeBridge(0.0F, 0.0F);
        bridge.perspective = 0;
        FreeLookModule module = new FreeLookModule(bridge);
        setting(module, "toggle-perspective").setValue(0.0D);

        module.setEnabled(true);

        assertEquals(0, bridge.perspective);
    }

    @Test
    void holdToActivateReflectsSetting() {
        FreeLookModule module = new FreeLookModule(new FakeBridge(0.0F, 0.0F));
        assertFalse(module.holdToActivate());

        setting(module, "hold").setValue(1.0D);
        assertTrue(module.holdToActivate());
    }

    private static ModuleSetting setting(FreeLookModule module, String id) {
        return module.settings().stream()
                .filter(setting -> setting.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static final class FakeBridge implements MinecraftBridge {
        private final float yaw;
        private final float pitch;
        private int perspective = 1;

        private FakeBridge(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public CameraPose cameraPose() {
            return new CameraPose(true, Vec3.ZERO, yaw, pitch, 70.0D, 1920, 1080);
        }

        @Override
        public int cameraPerspective() {
            return perspective;
        }

        @Override
        public boolean setCameraPerspective(int ordinal) {
            perspective = ordinal;
            return true;
        }

        @Override
        public boolean isSinglePlayer() {
            return false;
        }

        @Override
        public boolean applyReach(double range) {
            return false;
        }

        @Override
        public boolean resetReach() {
            return false;
        }

        @Override
        public boolean renderStatusText(Object renderContext, String text) {
            return false;
        }

        @Override
        public String environment() {
            return "test";
        }
    }
}
