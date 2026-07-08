package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.network.PacketRelay;
import dev.aurora.render.GhostBoxRenderer;

import java.util.Objects;

/** Holds outbound packets until disabled or the configured safety threshold is reached. */
public final class BlinkModule extends AbstractModule {
    private final MinecraftBridge minecraft;
    private final PacketRelay relay;
    private final Runnable autoDisableCallback;
    private final ModuleSetting sendThreshold;
    private final ModuleSetting renderMarker;
    private final ModuleSetting markerColor;

    private boolean capturing;
    private Vec3 serverPosition;

    public BlinkModule(MinecraftBridge minecraft, PacketRelay relay) {
        this(minecraft, relay, () -> { });
    }

    public BlinkModule(MinecraftBridge minecraft, PacketRelay relay, Runnable autoDisableCallback) {
        super("blink", "Blink", "Network",
                "Queues outbound packets and sends them together when disabled or at the safety limit.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.relay = Objects.requireNonNull(relay, "relay");
        this.autoDisableCallback = Objects.requireNonNull(autoDisableCallback, "autoDisableCallback");
        sendThreshold = setting("send-threshold", "Send Threshold", 60.0D, 1.0D, 124.0D, 1.0D)
                .description("Flushes the packet queue at this size to reduce timeout risk while staying enabled.");
        renderMarker = booleanSetting("render-marker", "Server Position", true)
                .description("Draws a box at the last position sent to the server.");
        markerColor = colorSetting("marker-color", "Marker Color", 0x9600FF)
                .description("Color of the server-position box.")
                .visibleWhen(() -> enabled(renderMarker));
    }

    @Override
    public void onEnable() {
        if (minecraft.isInGame()) {
            beginCapture();
        }
    }

    @Override
    public void onDisable() {
        if (capturing) {
            if (minecraft.isInGame()) {
                relay.flushOutbound();
            } else {
                relay.discardOutbound();
            }
        }
        capturing = false;
        serverPosition = null;
    }

    @Override
    public void onPacket(PacketEvent event) {
        if (event.direction() != PacketEvent.Direction.OUTBOUND
                || !PacketRelay.isProtocolTransition(event.packet())) {
            return;
        }
        if (capturing) {
            relay.flushOutbound();
        }
        capturing = false;
        serverPosition = null;
        setEnabled(false);
        autoDisableCallback.run();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!minecraft.isInGame()) {
            if (capturing) {
                relay.discardOutbound();
                capturing = false;
                serverPosition = null;
            }
            return;
        }
        if (!capturing) {
            beginCapture();
            return;
        }
        if (relay.heldOutboundCount() >= threshold()) {
            relay.flushOutbound();
            serverPosition = minecraft.playerPosition();
            relay.holdOutbound();
        }
    }

    @Override
    public void onWorldRender(WorldRenderEvent event) {
        if (capturing && enabled(renderMarker) && serverPosition != null) {
            GhostBoxRenderer.render(event.geometry(), serverPosition, (int) Math.round(markerColor.value()));
        }
    }

    int queuedPacketCount() {
        return capturing ? relay.heldOutboundCount() : 0;
    }

    private void beginCapture() {
        serverPosition = minecraft.playerPosition();
        relay.holdOutbound();
        capturing = true;
    }

    private int threshold() {
        return (int) Math.round(sendThreshold.value());
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }
}
