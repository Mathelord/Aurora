package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.TargetPose;
import dev.aurora.render.WorldGeometryBatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TracersModuleTest {
    @Test
    void drawsAnOutlineAndCoreQuadForEachTargetInRangeByDefault() {
        FakeBridge bridge = new FakeBridge();
        bridge.camera = new CameraPose(true, new Vec3(0.0D, 0.0D, 0.0D), 0.0F, 0.0F, 70.0D, 800, 600);
        bridge.pose = new TargetPose(1.0D, 64.0D, 1.0D, 0.6D, 1.8D);
        bridge.context = new AimContext(true, false, 0.0D, 0.0D, 0.5D,
                List.of(new AimTarget("target-1", "Target", Vec3.ZERO, 25.0D, 0.0D, 0.0D, 20.0D)));
        TracersModule module = new TracersModule(bridge);
        module.setEnabled(true);

        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.camera);
        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertEquals(2, geometry.quadCount());
        module.setEnabled(false);
    }

    @Test
    void drawsOnlyTheCoreQuadWhenOutlineIsDisabled() {
        FakeBridge bridge = new FakeBridge();
        bridge.camera = new CameraPose(true, new Vec3(0.0D, 0.0D, 0.0D), 0.0F, 0.0F, 70.0D, 800, 600);
        bridge.pose = new TargetPose(1.0D, 64.0D, 1.0D, 0.6D, 1.8D);
        bridge.context = new AimContext(true, false, 0.0D, 0.0D, 0.5D,
                List.of(new AimTarget("target-1", "Target", Vec3.ZERO, 25.0D, 0.0D, 0.0D, 20.0D)));
        TracersModule module = new TracersModule(bridge);
        module.setEnabled(true);
        module.settings().stream()
                .filter(setting -> setting.id().equals("outline"))
                .findFirst()
                .orElseThrow()
                .setValue(0.0D);

        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.camera);
        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertEquals(1, geometry.quadCount());
        module.setEnabled(false);
    }

    @Test
    void rendersNothingWhenNoTargetsAreAvailable() {
        FakeBridge bridge = new FakeBridge();
        bridge.camera = new CameraPose(true, new Vec3(0.0D, 0.0D, 0.0D), 0.0F, 0.0F, 70.0D, 800, 600);
        TracersModule module = new TracersModule(bridge);
        module.setEnabled(true);

        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.camera);
        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertEquals(0, geometry.quadCount());
        module.setEnabled(false);
    }

    @Test
    void rendersNothingWhenTheTargetPoseIsUnknown() {
        FakeBridge bridge = new FakeBridge();
        bridge.camera = new CameraPose(true, new Vec3(0.0D, 0.0D, 0.0D), 0.0F, 0.0F, 70.0D, 800, 600);
        bridge.pose = null;
        bridge.context = new AimContext(true, false, 0.0D, 0.0D, 0.5D,
                List.of(new AimTarget("target-1", "Target", Vec3.ZERO, 25.0D, 0.0D, 0.0D, 20.0D)));
        TracersModule module = new TracersModule(bridge);
        module.setEnabled(true);

        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.camera);
        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertEquals(0, geometry.quadCount());
        module.setEnabled(false);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private CameraPose camera = CameraPose.unavailable();
        private TargetPose pose;
        private AimContext context = AimContext.unavailable();

        @Override
        public AimContext aimContext(double range, boolean ignoreWalls) {
            return context;
        }

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
