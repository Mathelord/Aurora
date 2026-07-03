package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;

/**
 * Overrides the game's brightness (gamma) option so the world renders fully lit, restoring the
 * original value on disable. The override is re-applied every tick because the game — or another
 * screen such as the video settings menu — may otherwise reset it.
 */
public final class FullbrightModule extends AbstractModule {
    private static final double FULLBRIGHT_GAMMA = 16.0D;
    /** Vanilla brightness default, used to restore if the original value could not be captured. */
    private static final double DEFAULT_GAMMA = 0.5D;

    private final MinecraftBridge minecraft;
    private Double savedGamma;

    public FullbrightModule(MinecraftBridge minecraft) {
        super("fullbright", "Fullbright", "Render", "Lights up the world by overriding the gamma option.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override
    public void onEnable() {
        if (savedGamma == null) {
            minecraft.gamma().ifPresent(value -> savedGamma = value);
        }
        minecraft.setGamma(FULLBRIGHT_GAMMA);
    }

    @Override
    public void onDisable() {
        minecraft.setGamma(savedGamma != null ? savedGamma : DEFAULT_GAMMA);
        savedGamma = null;
    }

    @Override
    public void onTick(TickEvent event) {
        minecraft.setGamma(FULLBRIGHT_GAMMA);
    }
}
