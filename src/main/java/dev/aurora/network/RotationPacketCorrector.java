package dev.aurora.network;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.DecoupledAimState;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps Silent Aim actually silent. While {@link DecoupledAimState} is active the local player
 * entity's yaw/pitch hold the bot's snapped angle (so local hit detection and rendering work), but
 * that means Minecraft's own outbound movement/rotation packet would otherwise broadcast the same
 * snapped angle to the server every tick. This rewrites that one outbound packet's yaw/pitch fields
 * back to the player's real ("visual") look direction just before it is sent, in place, so the
 * connection never actually reveals the silent angle.
 */
public final class RotationPacketCorrector {
    private static final List<String> YAW_FIELD_NAMES = List.of("yaw", "yRot");
    private static final List<String> PITCH_FIELD_NAMES = List.of("pitch", "xRot");

    private final Set<String> movementPacketClassNames;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public RotationPacketCorrector(Set<String> movementPacketClassNames) {
        this.movementPacketClassNames = Set.copyOf(movementPacketClassNames);
    }

    public static RotationPacketCorrector minecraft1214() {
        return new RotationPacketCorrector(Set.of(
                "net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket",
                "net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$Full",
                "net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$LookAndOnGround",
                "net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$PositionAndOnGround",
                "net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$OnGroundOnly",
                "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket",
                "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Rot",
                "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$PosRot",
                "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Pos",
                "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$StatusOnly"
        ));
    }

    /** Mutates {@code packet} in place if it is an outbound movement/rotation packet and decoupled
     * aim is active. Never suppresses the send — the corrected packet still goes out normally. */
    public void correct(Object packet) {
        if (packet == null || !DecoupledAimState.get().isActive()) {
            return;
        }
        if (!movementPacketClassNames.contains(packet.getClass().getName())) {
            return;
        }
        try {
            AimAngles visual = DecoupledAimState.get().visualAngles();
            boolean wroteYaw = writeFloatField(packet, YAW_FIELD_NAMES, visual.yaw());
            boolean wrotePitch = writeFloatField(packet, PITCH_FIELD_NAMES, visual.pitch());
            if (!wroteYaw && !wrotePitch) {
                logOnce("Could not locate yaw/pitch fields on " + packet.getClass().getName()
                        + "; Silent Aim rotation may leak to the server.");
            }
        } catch (RuntimeException exception) {
            logOnce("Rotation packet correction failed: " + exception.getMessage());
        }
    }

    private void logOnce(String message) {
        if (failureLogged.compareAndSet(false, true)) {
            System.err.println("[Aurora] " + message);
        }
    }

    private static boolean writeFloatField(Object target, List<String> fieldNames, float value) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!fieldNames.contains(field.getName())) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (fieldType != float.class && fieldType != double.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (fieldType == float.class) {
                        field.setFloat(target, value);
                    } else {
                        field.setDouble(target, value);
                    }
                    return true;
                } catch (ReflectiveOperationException ignored) {
                    return false;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
