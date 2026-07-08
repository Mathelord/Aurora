package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreecamModuleTest {
    private static final int KEY_W = 87;
    private static final int KEY_SPACE = 32;

    @Test
    void enableSeedsPositionFromCameraForcesFirstPersonAndFreezesMovement() {
        FakeBridge bridge = new FakeBridge(new Vec3(10.0D, 64.0D, -5.0D), 0.0F);
        bridge.perspective = 1; // third person
        FreecamModule module = new FreecamModule(bridge);

        module.setEnabled(true);

        assertTrue(module.state().freezeMovement());
        assertEquals(0, bridge.perspective); // forced to first person
        double[] pos = module.state().interpolatedPosition(1.0D).orElseThrow();
        assertEquals(10.0D, pos[0], 1.0e-6);
        assertEquals(64.0D, pos[1], 1.0e-6);
        assertEquals(-5.0D, pos[2], 1.0e-6);
    }

    @Test
    void disableRestoresPerspective() {
        FakeBridge bridge = new FakeBridge(Vec3.ZERO, 0.0F);
        bridge.perspective = 2; // third person front
        FreecamModule module = new FreecamModule(bridge);

        module.setEnabled(true);
        assertEquals(0, bridge.perspective);
        module.setEnabled(false);
        assertEquals(2, bridge.perspective);
    }

    @Test
    void tickMovesCameraForwardAlongViewYaw() {
        // Yaw 0 in Minecraft faces +Z, so pressing forward should advance the camera along +Z.
        FakeBridge bridge = new FakeBridge(new Vec3(0.0D, 64.0D, 0.0D), 0.0F);
        FreecamModule module = new FreecamModule(bridge);
        setting(module, "speed").setValue(1.0D); // step = 0.5 * 1.0
        module.setEnabled(true);

        bridge.press(KEY_W);
        module.onTick(TickEvent.now());

        double[] pos = module.state().interpolatedPosition(1.0D).orElseThrow();
        assertEquals(0.0D, pos[0], 1.0e-6);
        assertEquals(64.0D, pos[1], 1.0e-6);
        assertEquals(0.5D, pos[2], 1.0e-6);
    }

    @Test
    void tickAscendsWhileSpaceHeld() {
        FakeBridge bridge = new FakeBridge(new Vec3(0.0D, 64.0D, 0.0D), 0.0F);
        FreecamModule module = new FreecamModule(bridge);
        setting(module, "speed").setValue(1.0D);
        module.setEnabled(true);

        bridge.press(KEY_SPACE);
        module.onTick(TickEvent.now());

        assertEquals(64.5D, module.state().interpolatedPosition(1.0D).orElseThrow()[1], 1.0e-6);
    }

    @Test
    void tickDoesNotMoveWhenAScreenIsOpen() {
        FakeBridge bridge = new FakeBridge(new Vec3(0.0D, 64.0D, 0.0D), 0.0F);
        bridge.screenOpen = true;
        FreecamModule module = new FreecamModule(bridge);
        module.setEnabled(true);
        bridge.press(KEY_W);

        module.onTick(TickEvent.now());

        assertEquals(0.0D, module.state().interpolatedPosition(1.0D).orElseThrow()[2], 1.0e-6);
    }

    @Test
    void holdToActivateReflectsSetting() {
        FreecamModule module = new FreecamModule(new FakeBridge(Vec3.ZERO, 0.0F));
        assertFalse(module.holdToActivate());

        setting(module, "hold").setValue(1.0D);
        assertTrue(module.holdToActivate());
    }

    private static ModuleSetting setting(FreecamModule module, String id) {
        return module.settings().stream()
                .filter(setting -> setting.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static final class FakeBridge implements MinecraftBridge {
        private final Vec3 eye;
        private final float yaw;
        private final Set<Integer> pressed = new HashSet<>();
        private boolean screenOpen;
        private int perspective = 0;

        private FakeBridge(Vec3 eye, float yaw) {
            this.eye = eye;
            this.yaw = yaw;
        }

        private void press(int keyCode) {
            pressed.add(keyCode);
        }

        @Override
        public boolean isKeyDown(int keyCode) {
            return pressed.contains(keyCode);
        }

        @Override
        public boolean hasOpenScreen() {
            return screenOpen;
        }

        @Override
        public CameraPose cameraPose() {
            return new CameraPose(true, eye, yaw, 0.0F, 70.0D, 1920, 1080);
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
