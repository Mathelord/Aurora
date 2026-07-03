package dev.aurora.render;

import java.util.List;

/** Two-stop gradient palettes for the target ring. Each preset interpolates between a primary and a
 * secondary color as a smooth cosine wave around the ring, producing a seamless loop. Colors are
 * returned as packed ARGB ints to match Aurora's world-geometry pipeline. */
public enum TargetRingColorPreset {
    Aurora("Aurora", 0xFFD647, 0xFF7040),
    Aqua("Aqua", 0x2AEBFF, 0x408EFF),
    Violet("Violet", 0xBC5AFF, 0xFF49C4),
    Toxic("Toxic", 0x53FF60, 0xDAFF37),
    Fire("Fire", 0xFF402A, 0xFFB02C),
    Frost("Frost", 0xD2F6FF, 0x53AEFF),
    White("White", 0xFFFFFF, 0xB9D2FF);

    private final String title;
    private final int primary;
    private final int secondary;

    TargetRingColorPreset(String title, int primary, int secondary) {
        this.title = title;
        this.primary = primary;
        this.secondary = secondary;
    }

    public String title() {
        return title;
    }

    /** Packed ARGB color at a normalized position {@code position} (0..1) around the ring. */
    public int colorAt(double position, int alpha) {
        double t = (1.0 - Math.cos(position * Math.PI * 2.0)) * 0.5;
        int r = lerp((primary >> 16) & 0xFF, (secondary >> 16) & 0xFF, t);
        int g = lerp((primary >> 8) & 0xFF, (secondary >> 8) & 0xFF, t);
        int b = lerp(primary & 0xFF, secondary & 0xFF, t);
        return (clampByte(alpha) << 24) | (r << 16) | (g << 8) | b;
    }

    public static List<String> titles() {
        return java.util.Arrays.stream(values()).map(TargetRingColorPreset::title).toList();
    }

    public static TargetRingColorPreset byIndex(int index) {
        TargetRingColorPreset[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    private static int lerp(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * Math.max(0.0, Math.min(1.0, t)));
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
