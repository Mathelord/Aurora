package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.render.WorldGeometryBatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailModuleTest {
    @Test
    void doesNotAddANewNodeUntilMovedFarEnough() {
        FakeBridge bridge = new FakeBridge();
        TrailModule module = new TrailModule(bridge);
        module.setEnabled(true);

        bridge.position = new Vec3(0.0D, 0.0D, 0.0D);
        module.onTick(TickEvent.now());
        bridge.position = new Vec3(0.01D, 0.0D, 0.0D);
        module.onTick(TickEvent.now());

        assertEquals(1, module.nodeCount());
        module.setEnabled(false);
    }

    @Test
    void addsANewNodeOnceMovedFarEnough() {
        FakeBridge bridge = new FakeBridge();
        TrailModule module = new TrailModule(bridge);
        module.setEnabled(true);

        bridge.position = new Vec3(0.0D, 0.0D, 0.0D);
        module.onTick(TickEvent.now());
        bridge.position = new Vec3(1.0D, 0.0D, 0.0D);
        module.onTick(TickEvent.now());

        assertEquals(2, module.nodeCount());
        module.setEnabled(false);
    }

    @Test
    void disablingClearsTheTrail() {
        FakeBridge bridge = new FakeBridge();
        TrailModule module = new TrailModule(bridge);
        module.setEnabled(true);
        bridge.position = new Vec3(0.0D, 0.0D, 0.0D);
        module.onTick(TickEvent.now());

        module.setEnabled(false);

        assertEquals(0, module.nodeCount());
    }

    @Test
    void rendersFilledSegmentsBetweenNodesWhenACameraIsAvailable() {
        FakeBridge bridge = new FakeBridge();
        bridge.camera = new CameraPose(true, new Vec3(0.0D, 0.0D, -5.0D), 0.0F, 0.0F, 70.0D, 800, 600);
        TrailModule module = new TrailModule(bridge);
        module.setEnabled(true);
        bridge.position = new Vec3(0.0D, 0.0D, 0.0D);
        module.onTick(TickEvent.now());
        bridge.position = new Vec3(1.0D, 0.0D, 0.0D);
        module.onTick(TickEvent.now());

        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.camera);
        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertTrue(geometry.quadCount() >= 1);
        module.setEnabled(false);
    }

    @Test
    void doesNotRenderWithoutACamera() {
        FakeBridge bridge = new FakeBridge();
        TrailModule module = new TrailModule(bridge);
        module.setEnabled(true);
        bridge.position = new Vec3(0.0D, 0.0D, 0.0D);
        module.onTick(TickEvent.now());
        bridge.position = new Vec3(1.0D, 0.0D, 0.0D);
        module.onTick(TickEvent.now());

        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.camera);
        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertEquals(0, geometry.quadCount());
        module.setEnabled(false);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private Vec3 position = Vec3.ZERO;
        private CameraPose camera = CameraPose.unavailable();

        @Override
        public Vec3 playerPosition() {
            return position;
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
