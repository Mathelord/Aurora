package dev.aurora.network;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.DecoupledAimState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotationPacketCorrectorTest {
    private final DecoupledAimState state = DecoupledAimState.get();
    private final RotationPacketCorrector corrector =
            new RotationPacketCorrector(Set.of(YarnMovementPacket.class.getName(), MojmapMovementPacket.class.getName()));

    @AfterEach
    void resetState() {
        state.reset();
    }

    @Test
    void rewritesYarnStyleFieldsToTheVisualAngleWhileDecoupled() {
        state.activate(new AimAngles(0.0F, 0.0F));
        state.applyMouseDelta(100.0D, 0.0D);
        YarnMovementPacket packet = new YarnMovementPacket(91.0F, 4.0F);

        corrector.correct(packet);

        assertEquals(state.visualAngles().yaw(), packet.yaw, 0.001F);
        assertEquals(state.visualAngles().pitch(), packet.pitch, 0.001F);
    }

    @Test
    void rewritesMojmapStyleFieldsToo() {
        state.activate(new AimAngles(0.0F, 0.0F));
        state.applyMouseDelta(50.0D, 0.0D);
        MojmapMovementPacket packet = new MojmapMovementPacket(91.0F, 4.0F);

        corrector.correct(packet);

        assertEquals(state.visualAngles().yaw(), packet.yRot, 0.001F);
        assertEquals(state.visualAngles().pitch(), packet.xRot, 0.001F);
    }

    @Test
    void leavesUnrecognizedPacketsUntouched() {
        state.activate(new AimAngles(0.0F, 0.0F));
        state.applyMouseDelta(100.0D, 0.0D);
        Object other = new Object();

        corrector.correct(other);
        corrector.correct(null);
    }

    @Test
    void doesNothingWhenDecoupledAimIsNotActive() {
        YarnMovementPacket packet = new YarnMovementPacket(91.0F, 4.0F);

        corrector.correct(packet);

        assertEquals(91.0F, packet.yaw, 0.001F);
        assertEquals(4.0F, packet.pitch, 0.001F);
    }

    private static final class YarnMovementPacket {
        private float yaw;
        private float pitch;

        private YarnMovementPacket(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class MojmapMovementPacket {
        private float yRot;
        private float xRot;

        private MojmapMovementPacket(float yRot, float xRot) {
            this.yRot = yRot;
            this.xRot = xRot;
        }
    }
}
