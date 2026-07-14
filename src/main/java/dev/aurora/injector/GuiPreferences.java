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
    private static final String NOTIFICATION_RADIUS_KEY = "notifications.radius";
    private static final String NOTIFICATIONS_ENABLED_KEY = "notifications.enabled";
    private static final String NOTIFICATION_BLUR_KEY = "notifications.blur";
    private static final String NOTIFICATION_OPACITY_KEY = "notifications.opacity";
    private static final String NOTIFICATION_DURATION_KEY = "notifications.durationMillis";
    private static final String FRIENDS_KEY = "friends.list";
    private static final String ACTIVE_PROFILE_KEY = "profiles.active";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ControlPanelGui.class);

    private GuiPreferences() {
    }

    /** The last profile the user applied, or an empty string when none is active. */
    static String activeProfile() {
        return PREFERENCES.get(ACTIVE_PROFILE_KEY, "");
    }

    static void setActiveProfile(String name) {
        PREFERENCES.put(ACTIVE_PROFILE_KEY, name == null ? "" : name);
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

    static int notificationRadius() {
        return Math.max(8, Math.min(28, PREFERENCES.getInt(NOTIFICATION_RADIUS_KEY, 18)));
    }

    static boolean notificationsEnabled() {
        return PREFERENCES.getBoolean(NOTIFICATIONS_ENABLED_KEY, true);
    }

    static void setNotificationsEnabled(boolean enabled) {
        PREFERENCES.putBoolean(NOTIFICATIONS_ENABLED_KEY, enabled);
    }

    static void setNotificationRadius(int radius) {
        PREFERENCES.putInt(NOTIFICATION_RADIUS_KEY, Math.max(8, Math.min(28, radius)));
    }

    static int notificationBlur() {
        return Math.max(4, Math.min(40, PREFERENCES.getInt(NOTIFICATION_BLUR_KEY, 4)));
    }

    static void setNotificationBlur(int blur) {
        PREFERENCES.putInt(NOTIFICATION_BLUR_KEY, Math.max(4, Math.min(40, blur)));
    }

    static int notificationOpacity() {
        return Math.max(0, Math.min(100, PREFERENCES.getInt(NOTIFICATION_OPACITY_KEY, 35)));
    }

    static void setNotificationOpacity(int opacity) {
        PREFERENCES.putInt(NOTIFICATION_OPACITY_KEY, Math.max(0, Math.min(100, opacity)));
    }

    static int notificationDurationMillis() {
        return Math.max(500, Math.min(5_000, PREFERENCES.getInt(NOTIFICATION_DURATION_KEY, 1_500)));
    }

    static void setNotificationDurationMillis(int durationMillis) {
        PREFERENCES.putInt(NOTIFICATION_DURATION_KEY, Math.max(500, Math.min(5_000, durationMillis)));
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
