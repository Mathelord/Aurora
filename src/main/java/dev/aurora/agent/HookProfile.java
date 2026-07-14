package dev.aurora.agent;

import dev.aurora.versions.v1_21_11.Version12111Hooks;

import java.util.List;
import java.util.Map;

/** Runtime hook names for one Minecraft release family and namespace. */
public record HookProfile(
        String minecraftVersion,
        List<String> tickClasses,
        List<String> tickMethods,
        List<String> renderClasses,
        List<String> renderMethods,
        List<String> packetClasses,
        List<String> outboundPacketMethods,
        List<String> inboundPacketMethods,
        List<String> mouseClasses,
        List<String> mouseButtonMethods,
        List<String> mouseScrollMethods,
        int mouseButtonArgumentCount,
        List<String> entityClasses,
        List<String> lookMethods,
        List<String> cameraClasses,
        List<String> cameraUpdateMethods,
        List<String> worldRenderClasses,
        List<String> worldRenderMethods,
        int worldRenderArgumentCount,
        List<String> attackMethods,
        List<String> blockAttackMethods
) {
    private static final Map<String, ObfuscatedNames> OBFUSCATED = Map.ofEntries(
            Map.entry("1.21.4", new ObfuscatedNames("flk", "t", "foe", "vi", "fll", 4,
                    "bum", "fks", "glv", "bo", "d")),
            Map.entry("1.21.11", new ObfuscatedNames("gfj", "x", "giq", "wu", "gfk", 3,
                    "cgk", "ger", "hoh", "bu", "e"))
    );

    public static HookProfile minecraft1214() {
        return forVersion("1.21.4");
    }

    public static HookProfile forVersion(String requestedVersion) {
        String version = requestedVersion == null ? "" : requestedVersion.trim();
        if ("1.21.11".equals(version)) {
            return Version12111Hooks.profile();
        }
        ObfuscatedNames obfuscated = OBFUSCATED.get(version);
        if (obfuscated != null) {
            return obfuscatedProfile(version, obfuscated);
        }
        if (!version.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Minecraft version: " + version);
        }
        return obfuscatedProfile("1.21.4", OBFUSCATED.get("1.21.4"));
    }

    public boolean hasWorldRenderHook() {
        return !worldRenderMethods.isEmpty() && worldRenderArgumentCount > 0;
    }

    private static HookProfile obfuscatedProfile(String version, ObfuscatedNames names) {
        boolean legacyWorldRenderer = "1.21.4".equals(version);
        List<String> worldRendererClasses = List.of(
                "net.minecraft.client.renderer.LevelRenderer",
                "net.minecraft.client.render.WorldRenderer", "net.minecraft.class_761",
                names.levelRendererClass);
        return new HookProfile(
                version,
                List.of("net.minecraft.client.Minecraft", "net.minecraft.client.MinecraftClient",
                        "net.minecraft.class_310", names.minecraftClass),
                List.of("tick", "method_1574", "m_91398_", names.tickMethod),
                List.of("net.minecraft.client.gui.Gui", "net.minecraft.client.gui.hud.InGameHud",
                        "net.minecraft.class_329", names.guiClass),
                List.of("render", "method_1753", "a"),
                List.of("net.minecraft.network.Connection", "net.minecraft.network.ClientConnection",
                        "net.minecraft.class_2535", names.connectionClass),
                List.of("send", "method_10743", "a"),
                List.of("handlePacket", "method_10759", "a"),
                List.of("net.minecraft.client.MouseHandler", "net.minecraft.client.Mouse",
                        "net.minecraft.class_312", names.mouseClass),
                List.of("onPress", "onButton", "onMouseButton", "method_1601", "a"),
                List.of("onScroll", "onMouseScroll", "method_1598", "a"),
                names.mouseButtonArgumentCount,
                List.of("net.minecraft.world.entity.Entity", "net.minecraft.entity.Entity",
                        "net.minecraft.class_1297", names.entityClass),
                List.of("turn", "changeLookDirection", "method_5872", "m_5616_", "b"),
                List.of("net.minecraft.client.Camera", "net.minecraft.client.render.Camera",
                        "net.minecraft.class_4184", names.cameraClass),
                List.of("setup", "update", "method_19321", "m_90575_", "a"),
                worldRendererClasses,
                legacyWorldRenderer
                        ? List.of("renderEntities", "method_62207", "a")
                        : List.of("renderBlockDestroyAnimation", "renderBlockDamage", "method_62206", "a"),
                legacyWorldRenderer ? 5 : 3,
                List.of("doAttack", "startAttack", "method_1536", names.startAttackMethod),
                List.of("handleBlockBreaking", "continueAttack", "method_1581", "method_1590",
                        names.continueAttackMethod));
    }

    private record ObfuscatedNames(String minecraftClass, String tickMethod, String guiClass,
                                   String connectionClass, String mouseClass, int mouseButtonArgumentCount,
                                   String entityClass, String cameraClass, String levelRendererClass,
                                   String startAttackMethod, String continueAttackMethod) {
    }
}
