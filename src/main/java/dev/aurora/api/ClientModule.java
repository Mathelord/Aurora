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

    /** Whether the module may only be enabled when it has a keybind. */
    default boolean requiresKeybind() {
        return false;
    }

    /**
     * Whether this module's keybind should act as a momentary hold (enabled only while the key is
     * held, disabled on release) instead of the default press-to-toggle behaviour. Modules that
     * expose a "Hold to activate" option return the current value of that option.
     */
    default boolean holdToActivate() {
        return false;
    }

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
