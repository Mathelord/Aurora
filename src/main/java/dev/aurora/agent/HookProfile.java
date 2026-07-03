package dev.aurora.agent;

import java.util.List;

public record HookProfile(
        List<String> tickClasses,
        List<String> tickMethods,
        List<String> renderClasses,
        List<String> renderMethods,
        List<String> packetClasses,
        List<String> outboundPacketMethods,
        List<String> inboundPacketMethods
) {
    public static HookProfile minecraft1214() {
        return new HookProfile(
                List.of(
                        "net.minecraft.client.Minecraft",
                        "net.minecraft.client.MinecraftClient",
                        "net.minecraft.class_310",
                        "flk"
                ),
                List.of("tick", "method_1574", "m_91398_", "t"),
                List.of(
                        "net.minecraft.client.gui.Gui",
                        "net.minecraft.client.gui.hud.InGameHud",
                        "net.minecraft.class_329"
                ),
                List.of("render", "method_1753"),
                List.of(
                        "net.minecraft.network.ClientConnection",
                        "net.minecraft.class_2535"
                ),
                List.of("send", "method_10743"),
                List.of("channelRead0", "handlePacket", "method_10770")
        );
    }
}
