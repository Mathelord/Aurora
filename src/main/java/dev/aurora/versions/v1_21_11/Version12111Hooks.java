package dev.aurora.versions.v1_21_11;

import dev.aurora.agent.HookProfile;

import java.util.List;

/**
 * Minecraft 1.21.11 hook boundary definitions.
 *
 * <p>1.21.11 replaced the old entity render submission method with the block-damage render pass.
 * Keeping the complete profile here prevents future modern-renderer fixes from leaking into the
 * stable 1.21.4 path.
 */
public final class Version12111Hooks {
    private Version12111Hooks() {
    }

    public static HookProfile profile() {
        return new HookProfile(
                "1.21.11",
                List.of("net.minecraft.client.Minecraft", "net.minecraft.client.MinecraftClient",
                        "net.minecraft.class_310", "gfj"),
                List.of("tick", "method_1574", "m_91398_", "x"),
                List.of("net.minecraft.client.gui.Gui", "net.minecraft.client.gui.hud.InGameHud",
                        "net.minecraft.class_329", "giq"),
                List.of("render", "method_1753", "a"),
                List.of("net.minecraft.network.Connection", "net.minecraft.network.ClientConnection",
                        "net.minecraft.class_2535", "wu"),
                List.of("send", "method_10743", "a"),
                List.of("handlePacket", "method_10759", "a"),
                List.of("net.minecraft.client.MouseHandler", "net.minecraft.client.Mouse",
                        "net.minecraft.class_312", "gfk"),
                List.of("onPress", "onButton", "onMouseButton", "method_1601", "a"),
                List.of("onScroll", "onMouseScroll", "method_1598", "a"),
                3,
                List.of("net.minecraft.world.entity.Entity", "net.minecraft.entity.Entity",
                        "net.minecraft.class_1297", "cgk"),
                List.of("turn", "changeLookDirection", "method_5872", "m_5616_", "b"),
                List.of("net.minecraft.client.Camera", "net.minecraft.client.render.Camera",
                        "net.minecraft.class_4184", "ger"),
                List.of("setup", "update", "method_19321", "m_90575_", "a"),
                List.of("net.minecraft.client.renderer.LevelRenderer",
                        "net.minecraft.client.render.WorldRenderer", "net.minecraft.class_761", "hoh"),
                List.of("renderBlockDestroyAnimation", "renderBlockDamage", "method_62206", "a"),
                3,
                List.of("doAttack", "startAttack", "method_1536", "bu"),
                List.of("handleBlockBreaking", "continueAttack", "method_1581", "method_1590", "e"));
    }
}
