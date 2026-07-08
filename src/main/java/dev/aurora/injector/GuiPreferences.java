package dev.aurora.injector;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;

/** Persistent desktop appearance settings shared by the launcher and control panel. */
final class GuiPreferences {
    static final Color DEFAULT_ACCENT = new Color(0x18, 0x66, 0xDF);
    private static final String ACCENT_KEY = "appearance.accent";
    private static final String RAINBOW_KEY = "appearance.rainbowModules";
    private static final String SILENT_AIM_INDICATOR_KEY = "silentAim.crosshairIndicator";
    private static final String FRIENDS_KEY = "friends.list";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ControlPanelGui.class);

    private GuiPreferences() {
    }

    /** The friended player names, in the order they were added. */
    static List<String> friends() {
        String stored = PREFERENCES.get(FRIENDS_KEY, "");
        if (stored.isBlank()) {
            return new ArrayList<>();
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String name : stored.split("\n")) {
            if (!name.isBlank()) {
                ordered.add(name.strip());
            }
        }
        return new ArrayList<>(ordered);
    }

    static void setFriends(List<String> friends) {
        PREFERENCES.put(FRIENDS_KEY, String.join("\n", friends));
    }

    /** Whether module cards are painted as a top-to-bottom rainbow gradient instead of the accent. */
    static boolean rainbowModules() {
        return PREFERENCES.getBoolean(RAINBOW_KEY, false);
    }

    static void setRainbowModules(boolean enabled) {
        PREFERENCES.putBoolean(RAINBOW_KEY, enabled);
    }

    static boolean silentAimCrosshairIndicator() {
        return PREFERENCES.getBoolean(SILENT_AIM_INDICATOR_KEY, true);
    }

    static void setSilentAimCrosshairIndicator(boolean enabled) {
        PREFERENCES.putBoolean(SILENT_AIM_INDICATOR_KEY, enabled);
    }

    static Color accentColor() {
        try {
            return parseColor(PREFERENCES.get(ACCENT_KEY, formatColor(DEFAULT_ACCENT)));
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_ACCENT;
        }
    }

    static void setAccentColor(Color color) {
        PREFERENCES.put(ACCENT_KEY, formatColor(color));
    }

    static void resetAppearance() {
        PREFERENCES.remove(ACCENT_KEY);
        PREFERENCES.remove(RAINBOW_KEY);
    }

    static Color parseColor(String value) {
        if (value == null) throw new IllegalArgumentException("Color is required");
        String hex = value.strip();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("(?i)[0-9a-f]{6}")) {
            throw new IllegalArgumentException("Use a six-digit hex color such as #1866DF");
        }
        return new Color(Integer.parseInt(hex, 16));
    }

    static String formatColor(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
