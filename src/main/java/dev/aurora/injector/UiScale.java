package dev.aurora.injector;

import java.util.prefs.Preferences;

/** Persistent desktop scale factor applied to the control panel's hand-painted Swing UI, so the
 * fixed pixel sizing this UI is built on (module cards, sliders, fonts, icons) can be scaled up
 * for high pixel-density displays instead of rendering tiny at 100%. */
final class UiScale {
    static final double DEFAULT_FACTOR = 1.0D;
    static final double MIN_FACTOR = 0.75D;
    static final double MAX_FACTOR = 2.0D;
    private static final String SCALE_KEY = "appearance.guiScale";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ControlPanelGui.class);

    private static volatile double factor = clamp(PREFERENCES.getDouble(SCALE_KEY, DEFAULT_FACTOR));

    private UiScale() {
    }

    static double factor() {
        return factor;
    }

    static void setFactor(double value) {
        factor = clamp(value);
        PREFERENCES.putDouble(SCALE_KEY, factor);
    }

    static void resetFactor() {
        PREFERENCES.remove(SCALE_KEY);
        factor = DEFAULT_FACTOR;
    }

    private static double clamp(double value) {
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, value));
    }
}
