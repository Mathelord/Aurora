package dev.aurora.network;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/** Resolves the target id from an outbound player-attack packet without linking Minecraft classes. */
public final class PlayerAttackPackets {
    private static final List<String> TARGET_ID_FIELDS = List.of("entityId", "field_12870", "b");
    private static final List<String> ACTION_FIELDS = List.of("type", "field_12871", "c");
    private static final List<String> ATTACK_ACTION_FIELDS = List.of("ATTACK", "field_29170", "e");

    private final Set<String> packetClassNames;

    public PlayerAttackPackets(Set<String> packetClassNames) {
        this.packetClassNames = Set.copyOf(packetClassNames);
    }

    public static PlayerAttackPackets supportedVersions() {
        return new PlayerAttackPackets(Set.of(
                "net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket",
                "net.minecraft.network.protocol.game.ServerboundInteractPacket",
                "net.minecraft.class_2824",
                "ahc", "aiy"
        ));
    }

    /** Returns the attacked entity id, or empty for right-click interactions and unrelated packets. */
    public OptionalInt attackedEntityId(Object packet) {
        if (packet == null || !packetClassNames.contains(packet.getClass().getName())) {
            return OptionalInt.empty();
        }
        Object action = readField(packet, ACTION_FIELDS, false);
        Object attackAction = readField(packet, ATTACK_ACTION_FIELDS, true);
        Object entityId = readField(packet, TARGET_ID_FIELDS, false);
        if (action == null || action != attackAction || !(entityId instanceof Number number)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(number.intValue());
    }

    private static Object readField(Object target, List<String> names, boolean requireStatic) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!names.contains(field.getName()) || Modifier.isStatic(field.getModifiers()) != requireStatic) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    return field.get(requireStatic ? null : target);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
