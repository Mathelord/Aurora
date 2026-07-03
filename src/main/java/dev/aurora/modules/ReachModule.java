package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class ReachModule extends AbstractModule {
    private final MinecraftBridge minecraft;
    private final ModuleSetting rangeMin;
    private final ModuleSetting rangeMax;
    private double currentRange = 4.5D;

    public ReachModule(MinecraftBridge minecraft) {
        super("reach", "Reach", "Player", "Changes block and entity interaction range.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.rangeMin = setting("range-min", "Range Min", 4.5D, 3.0D, 6.0D, 0.1D)
                .description("Minimum randomized distance used for block and entity interaction.");
        this.rangeMax = setting("range-max", "Range Max", 4.5D, 3.0D, 6.0D, 0.1D)
                .description("Maximum randomized distance used for block and entity interaction.");
    }

    public double range() {
        return currentRange;
    }

    @Override
    public void onDisable() {
        minecraft.resetReach();
    }

    @Override
    public void onTick(TickEvent event) {
        currentRange = rollRange();
        minecraft.applyReach(currentRange);
    }

    private double rollRange() {
        double min = Math.min(rangeMin.value(), rangeMax.value());
        double max = Math.max(rangeMin.value(), rangeMax.value());
        return max <= min ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }
}
