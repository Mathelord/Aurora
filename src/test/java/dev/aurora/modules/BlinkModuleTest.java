package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.network.PacketRelay;
import dev.aurora.render.WorldGeometryBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlinkModuleTest {
    private final PacketRelay relay = PacketRelay.get();
    private final FakeBridge bridge = new FakeBridge();
    private final BlinkModule module = new BlinkModule(bridge, relay);

    @AfterEach
    void resetRelay() {
        module.setEnabled(false);
        relay.reset();
    }

    @Test
    void holdsPacketsWhileEnabledAndFlushesInOrderWhenDisabled() {
        FakeConnection connection = new FakeConnection();
        FakePacket first = new FakePacket(1);
        FakePacket second = new FakePacket(2);

        module.setEnabled(true);
        assertTrue(relay.captureOutbound(connection, first));
        assertTrue(relay.captureOutbound(connection, second));
        assertEquals(2, module.queuedPacketCount());

        module.setEnabled(false);

        assertEquals(List.of(first, second), connection.sent);
        assertEquals(0, relay.heldOutboundCount());
    }

    @Test
    void thresholdFlushesAndImmediatelyStartsANewQueue() {
        setSetting("send-threshold", 2.0D);
        FakeConnection connection = new FakeConnection();
        FakePacket first = new FakePacket(1);
        FakePacket second = new FakePacket(2);

        module.setEnabled(true);
        assertTrue(relay.captureOutbound(connection, first));
        assertTrue(relay.captureOutbound(connection, second));
        module.onTick(TickEvent.now());

        assertEquals(List.of(first, second), connection.sent);
        assertEquals(0, module.queuedPacketCount());
        assertTrue(relay.captureOutbound(connection, new FakePacket(3)));
    }

    @Test
    void disconnectDiscardsStalePacketsInsteadOfReplayingThem() {
        FakeConnection connection = new FakeConnection();
        module.setEnabled(true);
        assertTrue(relay.captureOutbound(connection, new FakePacket(1)));

        bridge.inGame = false;
        module.onTick(TickEvent.now());

        assertTrue(connection.sent.isEmpty());
        assertEquals(0, relay.heldOutboundCount());
        assertFalse(relay.captureOutbound(connection, new FakePacket(2)));
    }

    @Test
    void enablingOutsideAWorldDoesNotHoldLoginTraffic() {
        bridge.inGame = false;
        FakeConnection connection = new FakeConnection();

        module.setEnabled(true);

        assertFalse(relay.captureOutbound(connection, new FakePacket(1)));
    }

    @Test
    void protocolTransitionFlushesAndDisablesBlink() {
        FakeConnection connection = new FakeConnection();
        FakePacket queued = new FakePacket(1);
        module.setEnabled(true);
        assertTrue(relay.captureOutbound(connection, queued));

        module.onPacket(PacketEvent.now(PacketEvent.Direction.OUTBOUND, new TransitionPacket()));

        assertEquals(List.of(queued), connection.sent);
        assertFalse(module.enabled());
        assertFalse(relay.captureOutbound(connection, new FakePacket(2)));
    }

    @Test
    void rendersMarkerAtPositionCapturedOnEnable() {
        bridge.position = new Vec3(4.0D, 70.0D, -2.0D);
        module.setEnabled(true);
        bridge.position = new Vec3(8.0D, 70.0D, -2.0D);
        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.cameraPose());

        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertEquals(6, geometry.quadCount());
        assertEquals(12, geometry.lineCount());
    }

    private void setSetting(String id, double value) {
        ModuleSetting setting = module.settings().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
        setting.setValue(value);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private boolean inGame = true;
        private Vec3 position = Vec3.ZERO;

        @Override
        public boolean isInGame() {
            return inGame;
        }

        @Override
        public Vec3 playerPosition() {
            return position;
        }

        @Override
        public CameraPose cameraPose() {
            return new CameraPose(true, new Vec3(0.0D, 70.0D, -5.0D),
                    0.0F, 0.0F, 70.0D, 800, 600);
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

    private static final class FakeConnection {
        private final List<FakePacket> sent = new ArrayList<>();

        @SuppressWarnings("unused")
        private void send(FakePacket packet) {
            sent.add(packet);
        }
    }

    private record FakePacket(int id) {
    }

    private static final class TransitionPacket {
        @SuppressWarnings("unused")
        private boolean method_55943() {
            return true;
        }
    }
}
