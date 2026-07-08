package dev.aurora.network;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockbackPacketsTest {
    private final KnockbackPackets knockbackPackets = new KnockbackPackets(Set.of(FakeVelocityPacket.class.getName()));

    @Test
    void recognizesTheLocalPlayerOnAVelocityPacket() {
        FakeVelocityPacket packet = new FakeVelocityPacket(42);
        assertTrue(knockbackPackets.isOwnKnockback(packet, 42));
    }

    @Test
    void ignoresVelocityPacketsForOtherEntities() {
        FakeVelocityPacket packet = new FakeVelocityPacket(7);
        assertFalse(knockbackPackets.isOwnKnockback(packet, 42));
    }

    @Test
    void ignoresUnrecognizedPacketTypes() {
        assertFalse(knockbackPackets.isOwnKnockback("not a packet", 42));
        assertFalse(knockbackPackets.isOwnKnockback(null, 42));
    }

    @Test
    void treatsANegativeLocalEntityIdAsUnavailable() {
        FakeVelocityPacket packet = new FakeVelocityPacket(42);
        assertFalse(knockbackPackets.isOwnKnockback(packet, -1));
    }

    @Test
    void readsFabricIntermediaryEntityIdAccessor() {
        KnockbackPackets packets = new KnockbackPackets(Set.of(FabricVelocityPacket.class.getName()));

        assertTrue(packets.isOwnKnockback(new FabricVelocityPacket(42), 42));
    }

    @Test
    void readsOfficialEntityIdAccessor() {
        KnockbackPackets packets = new KnockbackPackets(Set.of(OfficialVelocityPacket.class.getName()));

        assertTrue(packets.isOwnKnockback(new OfficialVelocityPacket(42), 42));
    }

    @Test
    void recognizesFabricExplosionKnockback() {
        KnockbackPackets packets = new KnockbackPackets(Set.of(), Set.of(FabricExplosionPacket.class.getName()));

        assertTrue(packets.isOwnKnockback(new FabricExplosionPacket(Optional.of("knockback")), 42));
        assertFalse(packets.isOwnKnockback(new FabricExplosionPacket(Optional.empty()), 42));
    }

    @Test
    void recognizesOfficial12111ExplosionKnockback() {
        KnockbackPackets packets = new KnockbackPackets(Set.of(), Set.of(OfficialExplosionPacket.class.getName()));

        assertTrue(packets.isOwnKnockback(new OfficialExplosionPacket(Optional.of("knockback")), 42));
        assertFalse(packets.isOwnKnockback(new OfficialExplosionPacket(Optional.empty()), 42));
    }

    private static final class FakeVelocityPacket {
        private final int entityId;

        private FakeVelocityPacket(int entityId) {
            this.entityId = entityId;
        }

        @SuppressWarnings("unused")
        public int getEntityId() {
            return entityId;
        }
    }

    private static final class FabricVelocityPacket {
        private final int value;

        private FabricVelocityPacket(int value) {
            this.value = value;
        }

        @SuppressWarnings("unused")
        public int method_11818() {
            return value;
        }
    }

    private static final class OfficialVelocityPacket {
        private final int value;

        private OfficialVelocityPacket(int value) {
            this.value = value;
        }

        @SuppressWarnings("unused")
        public int b() {
            return value;
        }
    }

    private record FabricExplosionPacket(Optional<?> knockback) {
        @SuppressWarnings("unused")
        public Optional<?> comp_2884() {
            return knockback;
        }
    }

    private record OfficialExplosionPacket(Optional<?> knockback) {
        @SuppressWarnings("unused")
        public Optional<?> e() {
            return knockback;
        }
    }
}
