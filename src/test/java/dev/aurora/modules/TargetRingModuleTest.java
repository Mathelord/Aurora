package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.TargetPose;
import dev.aurora.render.WorldGeometryBatch;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetRingModuleTest {
    @Test
    void rendersARingWhenAnAimTargetIsPresent() {
        FakeBridge bridge = new FakeBridge();
        bridge.camera = new CameraPose(true, new Vec3(0.0D, 0.0D, -5.0D), 0.0F, 0.0F, 70.0D, 800, 600);
        bridge.pose = new TargetPose(1.0D, 64.0D, 1.0D, 0.6D, 1.8D);
        String[] target = {"target-1"};
        TargetRingModule module = new TargetRingModule(bridge, () -> target[0]);
        module.setEnabled(true);

        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.camera);
        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertTrue(geometry.quadCount() >= 1);
        assertTrue(geometry.lineCount() >= 1);
        module.setEnabled(false);
    }

    @Test
    void rendersNothingWithoutAnAimTarget() {
        FakeBridge bridge = new FakeBridge();
        bridge.camera = new CameraPose(true, new Vec3(0.0D, 0.0D, -5.0D), 0.0F, 0.0F, 70.0D, 800, 600);
        bridge.pose = new TargetPose(1.0D, 64.0D, 1.0D, 0.6D, 1.8D);
        TargetRingModule module = new TargetRingModule(bridge, () -> null);
        module.setEnabled(true);

        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.camera);
        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertEquals(0, geometry.quadCount());
        assertEquals(0, geometry.lineCount());
        module.setEnabled(false);
    }

    @Test
    void rendersNothingWhenTheTargetPoseIsUnknown() {
        FakeBridge bridge = new FakeBridge();
        bridge.camera = new CameraPose(true, new Vec3(0.0D, 0.0D, -5.0D), 0.0F, 0.0F, 70.0D, 800, 600);
        bridge.pose = null;
        TargetRingModule module = new TargetRingModule(bridge, () -> "gone");
        module.setEnabled(true);

        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.camera);
        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertEquals(0, geometry.quadCount());
        module.setEnabled(false);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private CameraPose camera = CameraPose.unavailable();
        private TargetPose pose;

        @Override
        public Optional<TargetPose> targetPose(String targetId) {
            return Optional.ofNullable(pose);
        }

        @Override
        public CameraPose cameraPose() {
            return camera;
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
