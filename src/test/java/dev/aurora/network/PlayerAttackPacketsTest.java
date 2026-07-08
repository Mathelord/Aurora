package dev.aurora.network;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAttackPacketsTest {
    private final PlayerAttackPackets packets = new PlayerAttackPackets(Set.of(FakeInteractPacket.class.getName()));

    @Test
    void resolvesEntityIdOnlyForAttackAction() {
        assertEquals(OptionalInt.of(42), packets.attackedEntityId(new FakeInteractPacket(42, true)));
        assertTrue(packets.attackedEntityId(new FakeInteractPacket(42, false)).isEmpty());
    }

    @Test
    void ignoresUnrelatedPackets() {
        assertTrue(packets.attackedEntityId("not a packet").isEmpty());
        assertTrue(packets.attackedEntityId(null).isEmpty());
    }

    static final class FakeInteractPacket {
        static final Object ATTACK = new Object();
        private static final Object INTERACT = new Object();

        private final int entityId;
        private final Object type;

        FakeInteractPacket(int entityId, boolean attack) {
            this.entityId = entityId;
            this.type = attack ? ATTACK : INTERACT;
        }
    }
}
