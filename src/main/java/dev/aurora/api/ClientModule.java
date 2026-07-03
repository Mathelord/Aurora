package dev.aurora.api;

import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.api.events.WorldRenderEvent;

import java.util.List;

public interface ClientModule {
    String id();

    String displayName();

    String category();

    String description();

    boolean enabled();

    void setEnabled(boolean enabled);

    int keybind();

    void setKeybind(int keyCode);

    List<ModuleSetting> settings();

    default void onEnable() {
    }

    default void onDisable() {
    }

    default void onTick(TickEvent event) {
    }

    default void onRender(RenderEvent event) {
    }

    default void onWorldRender(WorldRenderEvent event) {
    }

    default void onPacket(PacketEvent event) {
    }
}
