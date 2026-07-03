package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;

/** Removes the local jump cooldown — a pure toggle, no settings. */
public final class NoJumpDelayModule extends AbstractModule {
    private final MinecraftBridge minecraft;

    public NoJumpDelayModule(MinecraftBridge minecraft) {
        super("no-jump-delay", "No Jump Delay", "Movement", "Removes the local jump cooldown.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override
    public void onTick(TickEvent event) {
        minecraft.clearJumpCooldown();
    }
}
