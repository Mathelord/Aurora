package dev.aurora.agent;

import dev.aurora.api.ClientModule;
import dev.aurora.api.ModuleManager;
import dev.aurora.api.ModuleSetting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/** Loads/saves module enabled state, keybinds, and settings to a per-user config file so they
 * survive across injections. */
final class ClientConfig {
    private static final String README = """
            Aurora configuration files
            ===========================
            Editing config.properties in this folder does NOT change anything about a
            running Aurora client. Aurora only reads this file once, at the moment the
            agent is injected, and only writes it back when you change something in the
            desktop control panel. Hand-editing it while Aurora is already injected has no
            effect until the next injection.
            """;

    private final Path file;

    ClientConfig(Path file) {
        this.file = file;
    }

    static ClientConfig standard() {
        return new ClientConfig(ConfigPaths.configFile());
    }

    void applyTo(ModuleManager modules, Consumer<String> onError) {
        Properties properties = load(onError);
        for (ClientModule module : modules.modules()) {
            String prefix = module.id() + ".";
            String keybindValue = properties.getProperty(prefix + "keybind");
            if (keybindValue != null) {
                try {
                    module.setKeybind(Integer.parseInt(keybindValue));
                } catch (NumberFormatException ignored) {
                }
            }
            String enabledValue = properties.getProperty(prefix + "enabled");
            if (enabledValue != null && (!module.requiresKeybind() || module.keybind() >= 0)) {
                module.setEnabled(Boolean.parseBoolean(enabledValue));
            }
            for (ModuleSetting setting : module.settings()) {
                String settingValue = properties.getProperty(prefix + setting.id());
                if (settingValue != null) {
                    try {
                        setting.setValue(Double.parseDouble(settingValue));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    void save(ModuleManager modules, Consumer<String> onError) {
        Properties properties = new Properties();
        for (ClientModule module : modules.modules()) {
            String prefix = module.id() + ".";
            properties.setProperty(prefix + "enabled", String.valueOf(module.enabled()));
            properties.setProperty(prefix + "keybind", String.valueOf(module.keybind()));
            for (ModuleSetting setting : module.settings()) {
                properties.setProperty(prefix + setting.id(), String.valueOf(setting.value()));
            }
        }
        try {
            Files.createDirectories(file.getParent());
            writeReadmeIfMissing(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "Aurora client configuration");
            }
        } catch (IOException exception) {
            onError.accept("Could not save config: " + exception.getMessage());
        }
    }

    private Properties load(Consumer<String> onError) {
        Properties properties = new Properties();
        if (!Files.isRegularFile(file)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            onError.accept("Could not load config: " + exception.getMessage());
        }
        return properties;
    }

    private static void writeReadmeIfMissing(Path dir) throws IOException {
        Path readme = dir.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme, README);
        }
    }
}
