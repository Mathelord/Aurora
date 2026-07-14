package dev.aurora.minecraft;

/** A world point projected into scaled HUD coordinates. */
public record ScreenPosition(boolean visible, double x, double y, double depth) {
    public static ScreenPosition invisible() {
        return new ScreenPosition(false, 0, 0, 0);
    }
}
