package dev.aurora.modules;

import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.network.KnockbackPackets;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JumpResetModuleTest {
    private static final int LOCAL_ID = 3;
    private final KnockbackPackets knockbackPackets = new KnockbackPackets(Set.of(FakeVelocityPacket.class.getName()));

    @Test
    void tapsJumpTheTickAfterOwnKnockbackLandsOnGround() {
        FakeBridge bridge = new FakeBridge(true);
        JumpResetModule module = new JumpResetModule(bridge, knockbackPackets);
        module.setEnabled(true);

        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, new FakeVelocityPacket(LOCAL_ID)));
        module.onTick(TickEvent.now());

        assertTrue(bridge.jumpHeld);

        module.onTick(TickEvent.now());

        assertFalse(bridge.jumpHeld);
        module.setEnabled(false);
    }

    @Test
    void ignoresKnockbackForOtherEntities() {
        FakeBridge bridge = new FakeBridge(true);
        JumpResetModule module = new JumpResetModule(bridge, knockbackPackets);
        module.setEnabled(true);

        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, new FakeVelocityPacket(999)));
        module.onTick(TickEvent.now());

        assertFalse(bridge.jumpHeld);
        module.setEnabled(false);
    }

    @Test
    void doesNotJumpWhileAirborne() {
        FakeBridge bridge = new FakeBridge(false);
        JumpResetModule module = new JumpResetModule(bridge, knockbackPackets);
        module.setEnabled(true);

        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, new FakeVelocityPacket(LOCAL_ID)));
        module.onTick(TickEvent.now());

        assertFalse(bridge.jumpHeld);
        module.setEnabled(false);
    }

    @Test
    void requireSprintBlocksTheResetWhenNotSprinting() {
        FakeBridge bridge = new FakeBridge(true);
        bridge.sprinting = false;
        JumpResetModule module = new JumpResetModule(bridge, knockbackPackets);
        module.settings().stream().filter(s -> s.id().equals("require-sprint")).findFirst().orElseThrow().setValue(1.0D);
        module.setEnabled(true);

        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, new FakeVelocityPacket(LOCAL_ID)));
        module.onTick(TickEvent.now());

        assertFalse(bridge.jumpHeld);
        module.setEnabled(false);
    }

    @Test
    void disablingReleasesAHeldJump() {
        FakeBridge bridge = new FakeBridge(true);
        JumpResetModule module = new JumpResetModule(bridge, knockbackPackets);
        module.setEnabled(true);
        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, new FakeVelocityPacket(LOCAL_ID)));
        module.onTick(TickEvent.now());
        assertTrue(bridge.jumpHeld);

        module.setEnabled(false);

        assertFalse(bridge.jumpHeld);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private final boolean onGround;
        private boolean sprinting = true;
        private boolean jumpHeld;

        private FakeBridge(boolean onGround) {
            this.onGround = onGround;
        }

        @Override
        public int localEntityId() {
            return LOCAL_ID;
        }

        @Override
        public boolean isOnGround() {
            return onGround;
        }

        @Override
        public boolean isSprinting() {
            return sprinting;
        }

        @Override
        public boolean setJumpKeyHeld(boolean held) {
            jumpHeld = held;
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
}
