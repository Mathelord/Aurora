package dev.aurora.minecraft;

public record ClickGuiInput(
        boolean available,
        int screenWidth,
        int screenHeight,
        double mouseX,
        double mouseY,
        boolean leftDown,
        boolean rightShiftDown,
        boolean escapeDown
) {
    public static ClickGuiInput unavailable() {
        return new ClickGuiInput(false, 0, 0, 0.0D, 0.0D, false, false, false);
    }
}
