package dev.aurora.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketRelayTest {
    private final PacketRelay relay = PacketRelay.get();

    @AfterEach
    void resetState() {
        relay.reset();
    }

    @Test
    void inboundPacketsPassThroughWithNoActiveRequest() {
        assertFalse(relay.isLagging());
        assertFalse(relay.captureInbound(new FakeListener(), new FakePacket()));
    }

    @Test
    void requestHoldsInboundPacketsUntilReleased() {
        relay.request("owner-a", 50, 5000);
        assertTrue(relay.isLagging());

        FakeListener listener = new FakeListener();
        FakePacket packet = new FakePacket();
        assertTrue(relay.captureInbound(listener, packet));

        relay.release("owner-a");
        assertFalse(relay.isLagging());
        assertFalse(relay.captureInbound(listener, new FakePacket()));
    }

    @Test
    void latencyMeasurementPacketsAreHeldWithTheInboundStream() {
        relay.request("owner-a", 5000, 5000);
        FakeListener listener = new FakeListener();

        assertTrue(relay.captureInbound(listener, new net.minecraft.class_2670()));
        assertTrue(relay.captureInbound(listener, new net.minecraft.class_6373()));
        assertEquals(2, relay.heldInboundCount());
    }

    @Test
    void effectiveLatencyIsTheMaxAcrossOwners() {
        relay.request("owner-a", 50, 5000);
        relay.request("owner-b", 200, 5000);
        assertTrue(relay.isLagging());

        relay.release("owner-b");
        assertTrue(relay.isLagging());

        relay.release("owner-a");
        assertFalse(relay.isLagging());
    }

    @Test
    void zeroOrNegativeLatencyReleasesInsteadOfHolding() {
        relay.request("owner-a", 50, 5000);
        assertTrue(relay.isLagging());

        relay.request("owner-a", 0, 5000);
        assertFalse(relay.isLagging());
    }

    @Test
    void heldInboundPacketsAreReplayedThroughTheCapturedListenerOnceReleased() {
        relay.request("owner-a", 5000, 5000);
        FakeListener listener = new FakeListener();
        FakePacket packet = new FakePacket();
        assertTrue(relay.captureInbound(listener, packet));

        // Releasing drops the effective latency to zero, which makes onTick() force-release
        // everything still queued regardless of each packet's own release time.
        relay.release("owner-a");
        relay.onTick();

        assertEquals(List.of(packet), listener.applied);
    }

    @Test
    void replaysThroughMinecraft1214IntermediaryPacketApplyMethod() {
        relay.request("owner-a", 5000, 5000);
        FakeListener listener = new FakeListener();
        IntermediaryPacket packet = new IntermediaryPacket();
        assertTrue(relay.captureInbound(listener, packet));

        relay.release("owner-a");
        relay.onTick();

        assertEquals(1, packet.applied);
    }

    @Test
    void replaysThroughOfficialObfuscated1214Names() {
        relay.request("owner-a", 5000, 5000);
        FakeListener listener = new FakeListener();
        OfficialPacket packet = new OfficialPacket();
        assertTrue(relay.captureInbound(listener, packet));

        relay.release("owner-a");
        relay.onTick();

        assertEquals(1, packet.applied);
    }

    @Test
    void tracksHeldEntityMovementForBackTrackRendering() {
        relay.request("owner-a", 5000, 5000);
        net.minecraft.class_2684 movement = new net.minecraft.class_2684.class_2685(
                7, (short) 4096, (short) -2048, (short) 1024);

        assertTrue(relay.captureInbound(new FakeListener(), movement));

        assertEquals(1.0D, relay.heldDisplacement(7).x(), 1.0e-9);
        assertEquals(-0.5D, relay.heldDisplacement(7).y(), 1.0e-9);
        assertEquals(0.25D, relay.heldDisplacement(7).z(), 1.0e-9);
    }

    @Test
    void outboundHoldQueuesPacketsAndFlushResendsThemInOrder() {
        relay.holdOutbound();
        FakeConnection connection = new FakeConnection();
        FakePacket first = new FakePacket();
        FakePacket second = new FakePacket();

        assertTrue(relay.captureOutbound(connection, first));
        assertTrue(relay.captureOutbound(connection, second));
        assertEquals(2, relay.heldOutboundCount());

        relay.flushOutbound();

        assertEquals(List.of(first, second), connection.sent);
        assertEquals(0, relay.heldOutboundCount());
    }

    @Test
    void outboundPacketsPassThroughWhenNotHeld() {
        assertFalse(relay.captureOutbound(new FakeConnection(), new FakePacket()));
    }

    @Test
    void protocolTransitionFlushesOldPacketsAndPassesThroughVanilla() {
        relay.holdOutbound();
        FakeConnection connection = new FakeConnection();
        FakePacket queued = new FakePacket();
        assertTrue(relay.captureOutbound(connection, queued));

        assertFalse(relay.captureOutbound(connection, new TransitionPacket()));

        assertEquals(List.of(queued), connection.sent);
        assertEquals(0, relay.heldOutboundCount());
        assertFalse(relay.captureOutbound(connection, new FakePacket()));
    }

    private static final class FakeConnection {
        private final List<FakePacket> sent = new ArrayList<>();

        @SuppressWarnings("unused")
        void send(FakePacket packet) {
            sent.add(packet);
        }
    }

    private static final class FakeListener {
        private final List<FakePacket> applied = new ArrayList<>();
    }

    private static final class FakePacket {
        @SuppressWarnings("unused")
        void apply(FakeListener listener) {
            listener.applied.add(this);
        }
    }

    private static final class IntermediaryPacket {
        private int applied;

        @SuppressWarnings("unused")
        private void method_65081(FakeListener listener) {
            applied++;
        }
    }

    private static final class OfficialPacket {
        private int applied;

        @SuppressWarnings("unused")
        private void a(FakeListener listener) {
            applied++;
        }
    }

    private static final class TransitionPacket {
        @SuppressWarnings("unused")
        private boolean method_55943() {
            return true;
        }
    }
}
