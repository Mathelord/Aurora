package dev.aurora.minecraft;

/** Scaled dimensions of the current HUD render target. */
public record HudSize(boolean available, int width, int height) {
    public static HudSize unavailable() {
        return new HudSize(false, 0, 0);
    }

    public static HudSize of(int width, int height) {
        if (width <= 0 || height <= 0) {
            return unavailable();
        }
        return new HudSize(true, width, height);
    }
}
