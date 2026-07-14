package dev.aurora.agent;

import java.nio.file.Path;
import java.util.Locale;

/** Resolves the per-user Aurora config location, independent of whichever Minecraft instance
 * the agent happens to be injected into. */
public final class ConfigPaths {
    private ConfigPaths() {
    }

    static Path configFile() {
        return configDir().resolve("config.properties");
    }

    /** Directory holding named configuration profiles, alongside the active config.properties. */
    public static Path profilesDir() {
        return configDir().resolve("profiles");
    }

    private static Path configDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData != null && !appData.isBlank()
                    ? Path.of(appData)
                    : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
            return base.resolve("Aurora");
        }
        String xdgConfig = System.getenv("XDG_CONFIG_HOME");
        Path base = xdgConfig != null && !xdgConfig.isBlank()
                ? Path.of(xdgConfig)
                : Path.of(System.getProperty("user.home"), ".config");
        return base.resolve("aurora");
    }
}
