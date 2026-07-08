package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;

/**
 * Overrides the game's brightness (gamma) option so the world renders fully lit, setting vanilla
 * brightness to 100% on disable. The override is re-applied every tick because the game — or another
 * screen such as the video settings menu — may otherwise reset it.
 */
public final class FullbrightModule extends AbstractModule {
    private static final double FULLBRIGHT_GAMMA = 16.0D;
    /** The vanilla brightness slider's 100% value. */
    private static final double MAX_VANILLA_GAMMA = 1.0D;

    private final MinecraftBridge minecraft;

    public FullbrightModule(MinecraftBridge minecraft) {
        super("fullbright", "Fullbright", "Render", "Lights up the world by overriding the gamma option.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override
    public synchronized void onEnable() {
        minecraft.setGamma(FULLBRIGHT_GAMMA);
    }

    @Override
    public synchronized void onDisable() {
        minecraft.restoreGamma(MAX_VANILLA_GAMMA);
    }

    @Override
    public synchronized void onTick(TickEvent event) {
        // ModuleManager may have observed the module as enabled immediately before another thread
        // disables it. Do not let that already-dispatched tick overwrite onDisable's restoration.
        if (enabled()) {
            minecraft.setGamma(FULLBRIGHT_GAMMA);
        }
    }
}
