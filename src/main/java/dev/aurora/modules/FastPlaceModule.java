package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;

/**
 * Reduces the delay between block placements while right-click is held. After each placement the
 * game sets a four-tick item-use cooldown; capping that cooldown to a configurable value each tick
 * lets blocks place faster (a delay of 0 is effectively every tick). Only acts while holding a
 * block, so it never speeds up other item uses.
 */
public final class FastPlaceModule extends AbstractModule {
    private final MinecraftBridge minecraft;
    private final ModuleSetting delay;

    public FastPlaceModule(MinecraftBridge minecraft) {
        super("fast-place", "FastPlace", "Player",
                "Reduces the delay between block placements while holding right-click.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.delay = setting("delay", "Delay", 2.0D, 0.0D, 4.0D, 1.0D)
                .description("Delay, in ticks, between block placements while holding right-click. "
                        + "Lower is faster.");
    }

    @Override
    public void onTick(TickEvent event) {
        if (!minecraft.isHoldingBlockItem()) {
            return;
        }
        int cap = (int) Math.round(delay.value());
        int current = minecraft.itemUseCooldown();
        if (current > cap) {
            minecraft.setItemUseCooldown(cap);
        }
    }
}
