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
        assertFalse(relay.shouldSuppress(true, new FakeConnection(), new FakePacket()));
    }

    @Test
    void requestHoldsInboundPacketsUntilReleased() {
        relay.request("owner-a", 50, 5000);
        assertTrue(relay.isLagging());

        FakeConnection connection = new FakeConnection();
        FakePacket packet = new FakePacket();
        assertTrue(relay.shouldSuppress(true, connection, packet));

        relay.release("owner-a");
        assertFalse(relay.isLagging());
        assertFalse(relay.shouldSuppress(true, connection, new FakePacket()));
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
    void heldInboundPacketsAreReplayedThroughTheConnectionListenerOnceReleased() {
        relay.request("owner-a", 5000, 5000);
        FakeConnection connection = new FakeConnection();
        FakePacket packet = new FakePacket();
        assertTrue(relay.shouldSuppress(true, connection, packet));

        // Releasing drops the effective latency to zero, which makes onTick() force-release
        // everything still queued regardless of each packet's own release time.
        relay.release("owner-a");
        relay.onTick();

        assertEquals(List.of(packet), connection.listener.applied);
    }

    @Test
    void intermediaryPacketListenerAccessorWinsOverTheNettyChannelField() {
        relay.request("owner-a", 5000, 5000);
        IntermediaryConnection connection = new IntermediaryConnection();
        FakePacket packet = new FakePacket();
        assertTrue(relay.shouldSuppress(true, connection, packet));

        relay.release("owner-a");
        relay.onTick();

        assertEquals(List.of(packet), connection.field_11652.applied);
    }

    @Test
    void replaysThroughMinecraft1214IntermediaryPacketApplyMethod() {
        relay.request("owner-a", 5000, 5000);
        IntermediaryConnection connection = new IntermediaryConnection();
        IntermediaryPacket packet = new IntermediaryPacket();
        assertTrue(relay.shouldSuppress(true, connection, packet));

        relay.release("owner-a");
        relay.onTick();

        assertEquals(1, packet.applied);
    }

    @Test
    void tracksHeldEntityMovementForBackTrackRendering() {
        relay.request("owner-a", 5000, 5000);
        net.minecraft.class_2684 movement = new net.minecraft.class_2684.class_2685(
                7, (short) 4096, (short) -2048, (short) 1024);

        assertTrue(relay.shouldSuppress(true, new FakeConnection(), movement));

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

        assertTrue(relay.shouldSuppress(false, connection, first));
        assertTrue(relay.shouldSuppress(false, connection, second));
        assertEquals(2, relay.heldOutboundCount());

        relay.flushOutbound();

        assertEquals(List.of(first, second), connection.sent);
        assertEquals(0, relay.heldOutboundCount());
    }

    @Test
    void outboundPacketsPassThroughWhenNotHeld() {
        assertFalse(relay.shouldSuppress(false, new FakeConnection(), new FakePacket()));
    }

    private static final class FakeConnection {
        private final FakeListener listener = new FakeListener();
        private final List<FakePacket> sent = new ArrayList<>();

        @SuppressWarnings("unused")
        void send(FakePacket packet) {
            sent.add(packet);
        }
    }

    private static final class IntermediaryConnection {
        @SuppressWarnings("unused")
        private final Object field_11651 = new Object();
        private final FakeListener field_11652 = new FakeListener();

        @SuppressWarnings("unused")
        private FakeListener method_10744() {
            return field_11652;
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
}
