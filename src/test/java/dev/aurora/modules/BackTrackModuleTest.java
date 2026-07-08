package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.TargetPose;
import dev.aurora.network.PacketRelay;
import dev.aurora.network.PlayerAttackPackets;
import dev.aurora.render.WorldGeometryBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackTrackModuleTest {
    private static final AimTarget TARGET = new AimTarget(
            "target", "Target", new Vec3(2.0D, 65.0D, 0.0D), 4.0D, 0.0D, 0.0D, 20.0D);

    private final PacketRelay relay = PacketRelay.get();
    private final FakeBridge bridge = new FakeBridge();
    private final PlayerAttackPackets packets = new PlayerAttackPackets(Set.of(FakeInteractPacket.class.getName()));
    private final BackTrackModule module = new BackTrackModule(bridge, relay, packets);

    @AfterEach
    void resetRelay() {
        module.setEnabled(false);
        relay.reset();
    }

    @Test
    void matchingPlayerAttackStartsInboundDelay() {
        module.setEnabled(true);

        module.onPacket(PacketEvent.now(PacketEvent.Direction.OUTBOUND, new FakeInteractPacket(7, true)));

        assertTrue(module.controlling());
        assertTrue(relay.isLagging());
    }

    @Test
    void interactionsAndMismatchedTargetsDoNotStartDelay() {
        module.setEnabled(true);
        module.onPacket(PacketEvent.now(PacketEvent.Direction.OUTBOUND, new FakeInteractPacket(7, false)));
        assertFalse(module.controlling());

        module.onPacket(PacketEvent.now(PacketEvent.Direction.OUTBOUND, new FakeInteractPacket(9, true)));
        assertFalse(module.controlling());
        assertFalse(relay.isLagging());
    }

    @Test
    void disappearingTargetStopsControlAndReleasesDelay() {
        module.setEnabled(true);
        module.onPacket(PacketEvent.now(PacketEvent.Direction.OUTBOUND, new FakeInteractPacket(7, true)));
        bridge.targetPresent = false;

        module.onTick(TickEvent.now());

        assertFalse(module.controlling());
        assertFalse(relay.isLagging());
    }

    @Test
    void rendersEstimatedServerPositionWhenMovementIsHeld() {
        module.setEnabled(true);
        module.onPacket(PacketEvent.now(PacketEvent.Direction.OUTBOUND, new FakeInteractPacket(7, true)));
        net.minecraft.class_2684 movement = new net.minecraft.class_2684.class_2685(
                7, (short) 4096, (short) 0, (short) 0);
        assertTrue(relay.captureInbound(new Object(), movement));
        WorldGeometryBatch geometry = new WorldGeometryBatch(new Object(), new Object(), bridge.cameraPose());

        module.onWorldRender(WorldRenderEvent.now(geometry));

        assertEquals(6, geometry.quadCount());
        assertEquals(12, geometry.lineCount());
    }

    private static final class FakeBridge implements MinecraftBridge {
        private boolean targetPresent = true;

        @Override
        public Optional<AimTarget> crosshairPlayer(double range) {
            return Optional.of(TARGET);
        }

        @Override
        public OptionalInt targetEntityId(String targetId) {
            return "target".equals(targetId) ? OptionalInt.of(7) : OptionalInt.empty();
        }

        @Override
        public Optional<TargetPose> targetPose(String targetId) {
            return targetPresent && "target".equals(targetId)
                    ? Optional.of(new TargetPose(2.0D, 64.0D, 0.0D, 0.6D, 1.8D))
                    : Optional.empty();
        }

        @Override
        public CameraPose cameraPose() {
            return new CameraPose(true, new Vec3(0.0D, 65.0D, -5.0D),
                    0.0F, 0.0F, 70.0D, 800, 600);
        }

        @Override
        public boolean isInGame() {
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

    private static final class FakeInteractPacket {
        private static final Object ATTACK = new Object();
        private static final Object INTERACT = new Object();

        private final int entityId;
        private final Object type;

        private FakeInteractPacket(int entityId, boolean attack) {
            this.entityId = entityId;
            this.type = attack ? ATTACK : INTERACT;
        }
    }
}
