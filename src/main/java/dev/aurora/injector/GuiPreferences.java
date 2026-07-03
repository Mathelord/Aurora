package dev.aurora.injector;

import java.awt.Color;
import java.util.Locale;
import java.util.prefs.Preferences;

/** Persistent desktop appearance settings shared by the launcher and control panel. */
final class GuiPreferences {
    static final Color DEFAULT_ACCENT = new Color(0x18, 0x66, 0xDF);
    private static final String ACCENT_KEY = "appearance.accent";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ControlPanelGui.class);

    private GuiPreferences() {
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
