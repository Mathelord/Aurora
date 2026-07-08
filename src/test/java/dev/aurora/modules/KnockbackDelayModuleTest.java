package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.network.KnockbackPackets;
import dev.aurora.network.PacketRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockbackDelayModuleTest {
    private static final AimTarget TARGET = new AimTarget(
            "target", "Target", new Vec3(2.0D, 65.0D, 0.0D), 4.0D, 5.0D, 2.0D, 20.0D);

    private final PacketRelay relay = PacketRelay.get();
    private final FakeBridge bridge = new FakeBridge();
    private final KnockbackPackets packets = new KnockbackPackets(Set.of(FakeVelocityPacket.class.getName()));
    private final KnockbackDelayModule module = new KnockbackDelayModule(bridge, packets, relay);

    @AfterEach
    void resetRelay() {
        module.setEnabled(false);
        relay.reset();
    }

    @Test
    void airborneKnockbackStartsAirDelayAndCapturesTriggerPacket() {
        module.setEnabled(true);
        FakeVelocityPacket knockback = new FakeVelocityPacket(42);

        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, knockback));

        assertTrue(relay.isLagging());
        assertEquals(150, relay.metrics().latencyMs());
        assertTrue(relay.captureInbound(new Object(), knockback));
    }

    @Test
    void threeGroundTicksSelectGroundDelay() {
        module.setEnabled(true);
        bridge.onGround = true;
        module.onTick(TickEvent.now());
        module.onTick(TickEvent.now());
        module.onTick(TickEvent.now());

        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, new FakeVelocityPacket(42)));

        assertEquals(80, relay.metrics().latencyMs());
    }

    @Test
    void requiresTargetInsideCrosshairFov() {
        module.setEnabled(true);
        bridge.targetVisible = false;

        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, new FakeVelocityPacket(42)));

        assertFalse(relay.isLagging());
    }

    @Test
    void zeroChanceNeverStartsDelay() {
        module.setEnabled(true);
        setSetting("chance", 0.0D);

        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, new FakeVelocityPacket(42)));

        assertFalse(relay.isLagging());
    }

    @Test
    void disablingReleasesActiveDelay() {
        module.setEnabled(true);
        module.onPacket(PacketEvent.now(PacketEvent.Direction.INBOUND, new FakeVelocityPacket(42)));

        module.setEnabled(false);

        assertFalse(relay.isLagging());
    }

    private void setSetting(String id, double value) {
        ModuleSetting setting = module.settings().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
        setting.setValue(value);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private boolean onGround;
        private boolean targetVisible = true;

        @Override
        public boolean isInGame() {
            return true;
        }

        @Override
        public boolean isOnGround() {
            return onGround;
        }

        @Override
        public int localEntityId() {
            return 42;
        }

        @Override
        public AimContext aimContext(double range, boolean ignoreWalls) {
            return new AimContext(true, false, 0.0D, 0.0D, 0.5D,
                    targetVisible ? List.of(TARGET) : List.of());
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

    private record FakeVelocityPacket(int entityId) {
        @SuppressWarnings("unused")
        public int getEntityId() {
            return entityId;
        }
    }
}
