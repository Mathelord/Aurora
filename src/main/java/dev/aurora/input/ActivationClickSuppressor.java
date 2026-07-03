package dev.aurora.input;

import dev.aurora.minecraft.MinecraftBridge;

/**
 * Prevents the physical click used to activate a hold-to-run combat module from also becoming an
 * uncontrolled vanilla attack. Module-generated attacks temporarily bypass the suppression.
 */
public final class ActivationClickSuppressor {
    private static MinecraftBridge minecraft;
    private static boolean suppressAttackUntilRelease;
    private static int moduleAttackBypassDepth;

    private ActivationClickSuppressor() {
    }

    public static void init(MinecraftBridge minecraft) {
        ActivationClickSuppressor.minecraft = minecraft;
        clear();
    }

    public static void armIfAttackKeyHeld(boolean enabled) {
        suppressAttackUntilRelease = enabled && minecraft != null && minecraft.isMouseButtonDown(0);
    }

    public static void beginModuleAttack() {
        moduleAttackBypassDepth++;
    }

    public static void endModuleAttack() {
        if (moduleAttackBypassDepth > 0) moduleAttackBypassDepth--;
    }

    public static void clear() {
        suppressAttackUntilRelease = false;
        moduleAttackBypassDepth = 0;
    }

    public static boolean shouldSuppressAttack() {
        if (moduleAttackBypassDepth > 0 || !suppressAttackUntilRelease) return false;
        if (minecraft == null || !minecraft.isMouseButtonDown(0)) {
            suppressAttackUntilRelease = false;
            return false;
        }
        return true;
    }
}
