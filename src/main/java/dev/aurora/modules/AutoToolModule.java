package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.BlockHitTarget;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;
import java.util.Optional;

/**
 * After left-click has been held on a block for a short delay, swaps to the fastest suitable hotbar
 * tool, then returns to the previous slot once the click is released. Ported from the Meteor/Eclipse
 * AutoTool+ module. Reacts only to a physically held left mouse button and the block actually under
 * the crosshair — it never swings by itself.
 */
public final class AutoToolModule extends AbstractModule {
    private static final long SWAP_RETRY_MS = 150L;

    private final MinecraftBridge minecraft;
    private final ModuleSetting swapDelayMs;
    private final ModuleSetting swapBackDelayMs;
    private final ModuleSetting onSneak;

    private long trackedBlockKey = Long.MIN_VALUE;
    private long trackingStartedAt;
    private long nextSwapAttemptAt;
    private int restoreSlot = -1;
    private long restoreAt;

    public AutoToolModule(MinecraftBridge minecraft) {
        super("auto-tool", "AutoTool", "Player",
                "Swaps to the best tool while mining, then swaps back when you stop.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.swapDelayMs = setting("swap-delay-ms", "Swap Delay", 140.0D, 0.0D, 1000.0D, 10.0D)
                .description("How long you must hold attack on the same block before swapping, in milliseconds.");
        this.swapBackDelayMs = setting("swap-back-delay-ms", "Swap Back Delay", 120.0D, 0.0D, 1000.0D, 10.0D)
                .description("How long to wait before returning to the previous slot after you stop attacking.");
        this.onSneak = booleanSetting("on-sneak", "On Sneak", false)
                .description("Only switches to the best tool while you are sneaking.");
    }

    @Override
    public void onEnable() {
        resetTracking();
        restoreSlot = -1;
        restoreAt = 0L;
    }

    @Override
    public void onDisable() {
        updateRestore(true);
        resetTracking();
    }

    @Override
    public void onTick(TickEvent event) {
        updateRestore(false);

        if (!minecraft.isMouseButtonDown(0)) {
            queueRestore();
            resetTracking();
            return;
        }

        if (enabled(onSneak) && !minecraft.isSneaking()) {
            resetTracking();
            return;
        }

        Optional<BlockHitTarget> target = minecraft.crosshairBlock();
        if (target.isEmpty()) {
            resetTracking();
            return;
        }
        BlockHitTarget hit = target.get();

        long now = System.currentTimeMillis();
        long blockKey = blockKey(hit.blockX(), hit.blockY(), hit.blockZ());
        if (blockKey != trackedBlockKey) {
            trackedBlockKey = blockKey;
            trackingStartedAt = now;
        }

        int bestSlot = minecraft.bestMiningToolSlot(hit.blockX(), hit.blockY(), hit.blockZ());
        if (bestSlot < 0) {
            return;
        }

        int selectedSlot = minecraft.selectedHotbarSlot();
        if (selectedSlot == bestSlot) {
            nextSwapAttemptAt = 0L;
            return;
        }

        if (now - trackingStartedAt < (long) swapDelayMs.value()) {
            return;
        }
        if (now < nextSwapAttemptAt) {
            return;
        }

        if (minecraft.selectHotbarSlot(bestSlot)) {
            if (restoreSlot < 0) {
                restoreSlot = selectedSlot;
            }
            restoreAt = 0L;
            nextSwapAttemptAt = 0L;
        } else {
            nextSwapAttemptAt = now + SWAP_RETRY_MS;
        }
    }

    private void queueRestore() {
        if (restoreSlot < 0 || restoreAt > 0L) {
            return;
        }
        restoreAt = System.currentTimeMillis() + (long) swapBackDelayMs.value();
    }

    private void updateRestore(boolean force) {
        if (restoreSlot < 0) {
            return;
        }
        if (!force && (restoreAt <= 0L || System.currentTimeMillis() < restoreAt)) {
            return;
        }
        int slot = restoreSlot;
        restoreSlot = -1;
        restoreAt = 0L;
        if (minecraft.selectedHotbarSlot() != slot) {
            minecraft.selectHotbarSlot(slot);
        }
    }

    private void resetTracking() {
        trackedBlockKey = Long.MIN_VALUE;
        trackingStartedAt = 0L;
        nextSwapAttemptAt = 0L;
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }
}
