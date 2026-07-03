package dev.aurora.input;

import dev.aurora.minecraft.MinecraftBridge;

/**
 * Drives attacks/uses through the game's own click handlers instead of issuing interaction calls
 * directly. {@link #leftClick()} runs the vanilla {@code doAttack} and {@link #rightClick()} runs
 * {@code doItemUse} — the exact code paths a physical click takes (swing + interaction) — so the
 * resulting action looks input-driven rather than a manually fabricated attack.
 *
 * <p>Both act on the game's current crosshair target. A silent/decoupled aura therefore points the
 * crosshair at its intended entity first (see {@link MinecraftBridge#setCrosshairTarget(String)})
 * and then calls {@link #leftClick()}.
 *
 * <p>Ported from the Meteor addon's {@code RealClickSimulator}; here it sits on top of the
 * reflection {@link MinecraftBridge} rather than mixin accessors, and reaches the click handlers
 * directly instead of replaying GLFW mouse callbacks (which would double-fire against the game's own
 * per-tick input pass).
 */
public final class RealClickSimulator {
    private static final RealClickSimulator INSTANCE = new RealClickSimulator();
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final long HUD_CLICK_VISIBLE_MS = 95L;

    private MinecraftBridge minecraft;
    private long leftVisibleUntil;
    private long rightVisibleUntil;

    private RealClickSimulator() {
    }

    /** Wires the simulator to the active bridge. Must be called once before any click. */
    public static void init(MinecraftBridge minecraft) {
        INSTANCE.minecraft = minecraft;
    }

    public static boolean leftClick() {
        return INSTANCE.click(LEFT);
    }

    public static boolean rightClick() {
        return INSTANCE.click(RIGHT);
    }

    public static boolean isLeftClickVisiblyPressed() {
        return INSTANCE.isVisiblyPressed(LEFT);
    }

    public static boolean isRightClickVisiblyPressed() {
        return INSTANCE.isVisiblyPressed(RIGHT);
    }

    private boolean click(int button) {
        if (minecraft == null) {
            return false;
        }
        boolean acted;
        if (button == LEFT) {
            ActivationClickSuppressor.beginModuleAttack();
            try {
                acted = minecraft.doAttack();
            } finally {
                ActivationClickSuppressor.endModuleAttack();
            }
        } else {
            acted = minecraft.doItemUse();
        }
        if (acted) {
            markVisible(button);
        }
        return acted;
    }

    private void markVisible(int button) {
        long visibleUntil = System.currentTimeMillis() + HUD_CLICK_VISIBLE_MS;
        if (button == LEFT) {
            leftVisibleUntil = visibleUntil;
        } else if (button == RIGHT) {
            rightVisibleUntil = visibleUntil;
        }
    }

    private boolean isVisiblyPressed(int button) {
        long visibleUntil = switch (button) {
            case LEFT -> leftVisibleUntil;
            case RIGHT -> rightVisibleUntil;
            default -> 0L;
        };
        return System.currentTimeMillis() <= visibleUntil;
    }
}
