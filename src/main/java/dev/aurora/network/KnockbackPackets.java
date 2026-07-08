package dev.aurora.network;

import java.lang.reflect.Method;
import java.util.List;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.Set;

/**
 * Recognizes the inbound "apply velocity to an entity" packet Minecraft sends for knockback, used
 * by {@code JumpResetModule}.
 */
public final class KnockbackPackets {
    private static final List<String> ENTITY_ID_ACCESSOR_NAMES = List.of(
            "getEntityId", "getId", "id", "entityId", "method_11818", "b"
    );

    private final Set<String> velocityPacketClassNames;
    private final Set<String> explosionPacketClassNames;

    public KnockbackPackets(Set<String> velocityPacketClassNames) {
        this(velocityPacketClassNames, Set.of());
    }

    public KnockbackPackets(Set<String> velocityPacketClassNames, Set<String> explosionPacketClassNames) {
        this.velocityPacketClassNames = Set.copyOf(velocityPacketClassNames);
        this.explosionPacketClassNames = Set.copyOf(explosionPacketClassNames);
    }

    public static KnockbackPackets minecraft1214() {
        return new KnockbackPackets(Set.of(
                "net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket",
                "net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket",
                "net.minecraft.class_2743",
                "aex", "agr"
        ), Set.of(
                "net.minecraft.network.packet.s2c.play.ExplosionS2CPacket",
                "net.minecraft.network.protocol.game.ClientboundExplodePacket",
                "net.minecraft.class_2664",
                "acr", "aek"
        ));
    }

    /** Whether {@code packet} is a velocity-update packet targeting the given local player entity id. */
    public boolean isOwnKnockback(Object packet, int localEntityId) {
        if (packet == null || localEntityId < 0) {
            return false;
        }
        if (velocityPacketClassNames.contains(packet.getClass().getName())) {
            return readEntityId(packet).stream().anyMatch(id -> id == localEntityId);
        }
        return explosionPacketClassNames.contains(packet.getClass().getName()) && hasExplosionKnockback(packet);
    }

    private static boolean hasExplosionKnockback(Object packet) {
        for (Method method : packet.getClass().getMethods()) {
            if (!List.of("playerKnockback", "comp_2884", "c", "e").contains(method.getName())
                    || method.getParameterCount() != 0) {
                continue;
            }
            try {
                Object value = method.invoke(packet);
                return value instanceof Optional<?> optional && optional.isPresent();
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return nonZeroNumber(packet, "getPlayerVelocityX")
                || nonZeroNumber(packet, "getPlayerVelocityY")
                || nonZeroNumber(packet, "getPlayerVelocityZ");
    }

    private static boolean nonZeroNumber(Object packet, String name) {
        try {
            Method method = packet.getClass().getMethod(name);
            Object value = method.invoke(packet);
            return value instanceof Number number && number.doubleValue() != 0.0D;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static OptionalInt readEntityId(Object packet) {
        for (Method method : packet.getClass().getMethods()) {
            if (!ENTITY_ID_ACCESSOR_NAMES.contains(method.getName()) || method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType != int.class && returnType != Integer.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                return OptionalInt.of((Integer) method.invoke(packet));
            } catch (ReflectiveOperationException ignored) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }
}
