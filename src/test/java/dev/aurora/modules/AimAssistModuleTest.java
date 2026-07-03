package dev.aurora.modules;

import dev.aurora.api.ModuleSetting;
import dev.aurora.aim.Vec3;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimAssistModuleTest {
    @Test
    void appliesTargetYawAndKeepsCurrentPitch() {
        FakeBridge bridge = new FakeBridge(new AimContext(
                true,
                false,
                0.0D,
                8.0D,
                0.5D,
                List.of(target("target", 20.0D, -12.0D, 20.0D))
        ));
        AimAssistModule module = new AimAssistModule(bridge);
        set(module, "strength-min", 1.0D);
        set(module, "strength-max", 1.0D);

        module.setEnabled(true);
        module.onTick(TickEvent.now());

        assertTrue(bridge.lastYaw > 0.0D);
        assertTrue(bridge.lastYaw < 20.0D);
        assertEquals(8.0D, bridge.lastPitch, 0.001D);
    }

    @Test
    void prefersCurrentTargetWhenStillValid() {
        FakeBridge bridge = new FakeBridge(new AimContext(
                true,
                false,
                0.0D,
                0.0D,
                0.5D,
                List.of(target("first", 10.0D, 10.0D), target("second", 25.0D, 25.0D))
        ));
        AimAssistModule module = new AimAssistModule(bridge);
        set(module, "strength-min", 1.0D);
        set(module, "strength-max", 1.0D);

        module.setEnabled(true);
        module.onTick(TickEvent.now());
        assertTrue(bridge.lastYaw > 0.0D);
        assertTrue(bridge.lastYaw < 10.0D);

        bridge.context = new AimContext(
                true,
                false,
                0.0D,
                0.0D,
                0.5D,
                List.of(target("first", 30.0D, 30.0D), target("second", 5.0D, 5.0D))
        );
        module.onTick(TickEvent.now());

        assertTrue(bridge.lastYaw > 0.0D);
        assertTrue(bridge.lastYaw < 30.0D);
    }

    @Test
    void exposesOnlyBackedSettings() {
        AimAssistModule module = new AimAssistModule(new FakeBridge(AimContext.unavailable()));
        Set<String> settingIds = module.settings().stream()
                .map(ModuleSetting::id)
                .collect(Collectors.toSet());

        assertFalse(settingIds.contains("ignore-friends"));
        assertFalse(settingIds.contains("visual-debug"));
        assertTrue(settingIds.contains("hold-mouse"));
        assertFalse(settingIds.contains("smoothing-profile"));
        assertFalse(settingIds.stream().anyMatch(id -> id.startsWith("custom-")));
    }

    @Test
    void forwardsRangeAndWallModeToBridge() {
        FakeBridge bridge = new FakeBridge(new AimContext(true, false, 0.0D, 0.0D, 0.5D, List.of()));
        AimAssistModule module = new AimAssistModule(bridge);
        set(module, "range-min", 7.0D);
        set(module, "range-max", 7.0D);
        set(module, "ignore-walls", 0.0D);

        module.setEnabled(true);
        module.onTick(TickEvent.now());

        assertEquals(7.0D, bridge.lastRange, 0.001D);
        assertFalse(bridge.lastIgnoreWalls);
    }

    private static AimTarget target(String id, double yaw, double distance) {
        return target(id, yaw, 0.0D, distance);
    }

    private static AimTarget target(String id, double yaw, double pitch, double distance) {
        return new AimTarget(id, id, new Vec3(0.0D, 1.0D, distance), distance * distance, yaw, pitch, 20.0D);
    }

    private static void set(AimAssistModule module, String id, double value) {
        for (ModuleSetting setting : module.settings()) {
            if (setting.id().equals(id)) {
                setting.setValue(value);
                return;
            }
        }
        throw new AssertionError("unknown setting: " + id);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private AimContext context;
        private boolean rotationApplied;
        private boolean lastIgnoreWalls;
        private double lastRange;
        private double lastYaw;
        private double lastPitch;

        private FakeBridge(AimContext context) {
            this.context = context;
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
        public AimContext aimContext(double range, boolean ignoreWalls) {
            lastRange = range;
            lastIgnoreWalls = ignoreWalls;
            return context;
        }

        @Override
        public boolean applyAimRotation(double yaw, double pitch) {
            rotationApplied = true;
            lastYaw = yaw;
            lastPitch = pitch;
            return true;
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
