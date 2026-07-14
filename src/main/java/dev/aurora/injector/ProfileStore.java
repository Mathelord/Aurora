package dev.aurora.injector;

import dev.aurora.agent.ConfigPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Named configuration profiles stored on disk next to the active config.properties. A profile is
 * a snapshot of every module's enabled state, keybind, and settings plus the shared global
 * settings, captured from the control panel and re-applied live to a connected agent. */
final class ProfileStore {
    private static final String EXTENSION = ".properties";
    private static final String GLOBAL_SILENT_AIM = "global.silentAimCrosshairIndicator";
    private static final String MODULE_PREFIX = "module.";
    private static final String SETTING_MARKER = ".setting.";

    /** A captured module state. Settings map is keyed by setting id. */
    record ModuleState(boolean enabled, int keybind, Map<String, Double> settings) {
    }

    /** A full captured profile: per-module state plus shared global settings. */
    record Snapshot(Map<String, ModuleState> modules, boolean silentAimCrosshairIndicator) {
    }

    private final Path directory;

    ProfileStore() {
        this(ConfigPaths.profilesDir());
    }

    ProfileStore(Path directory) {
        this.directory = directory;
    }

    /** Whether {@code name} is usable as a profile file name (kept simple and filesystem-safe). */
    static boolean isValidName(String name) {
        return name != null && name.strip().matches("[A-Za-z0-9 _-]{1,40}");
    }

    /** Profile names currently on disk, sorted case-insensitively. */
    List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return names;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
            for (Path entry : entries) {
                String fileName = entry.getFileName().toString();
                names.add(fileName.substring(0, fileName.length() - EXTENSION.length()));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list profiles", exception);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    boolean exists(String name) {
        return Files.isRegularFile(fileFor(name));
    }

    void save(String name, Snapshot snapshot) {
        Properties properties = new Properties();
        properties.setProperty(GLOBAL_SILENT_AIM, String.valueOf(snapshot.silentAimCrosshairIndicator()));
        for (Map.Entry<String, ModuleState> entry : snapshot.modules().entrySet()) {
            String prefix = MODULE_PREFIX + entry.getKey() + ".";
            ModuleState state = entry.getValue();
            properties.setProperty(prefix + "enabled", String.valueOf(state.enabled()));
            properties.setProperty(prefix + "keybind", String.valueOf(state.keybind()));
            for (Map.Entry<String, Double> setting : state.settings().entrySet()) {
                properties.setProperty(MODULE_PREFIX + entry.getKey() + SETTING_MARKER + setting.getKey(),
                        String.valueOf(setting.getValue()));
            }
        }
        try {
            Files.createDirectories(directory);
            try (OutputStream output = Files.newOutputStream(fileFor(name))) {
                properties.store(output, "Aurora profile: " + name);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not save profile " + name, exception);
        }
    }

    void exportTo(String name, Path destination) {
        if (destination == null) throw new IllegalArgumentException("destination is required");
        try {
            Path parent = destination.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(fileFor(name), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not export profile " + name, exception);
        }
    }

    /** Imports a portable profile without overwriting a local profile of the same name. */
    String importFrom(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Select a profile .properties file");
        }
        String fileName = source.getFileName().toString();
        if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(EXTENSION)) {
            throw new IllegalArgumentException("Profile files must use the .properties extension");
        }
        String baseName = fileName.substring(0, fileName.length() - EXTENSION.length()).strip();
        if (!isValidName(baseName)) baseName = "Imported Profile";
        Snapshot snapshot = read(source);
        String importedName = availableName(baseName);
        save(importedName, snapshot);
        return importedName;
    }

    Snapshot load(String name) {
        return read(fileFor(name));
    }

    private Snapshot read(Path file) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read profile", exception);
        }
        Map<String, ModuleState> modules = new LinkedHashMap<>();
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        Map<String, Integer> keybinds = new LinkedHashMap<>();
        Map<String, Map<String, Double>> settings = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(MODULE_PREFIX)) {
                continue;
            }
            String rest = key.substring(MODULE_PREFIX.length());
            int firstDot = rest.indexOf('.');
            if (firstDot < 0) {
                continue;
            }
            String moduleId = rest.substring(0, firstDot);
            String remainder = rest.substring(firstDot + 1);
            String value = properties.getProperty(key);
            if (remainder.equals("enabled")) {
                enabled.put(moduleId, Boolean.parseBoolean(value));
            } else if (remainder.equals("keybind")) {
                keybinds.put(moduleId, parseInt(value));
            } else if (remainder.startsWith("setting.")) {
                Double parsed = parseDouble(value);
                if (parsed != null) {
                    settings.computeIfAbsent(moduleId, id -> new LinkedHashMap<>())
                            .put(remainder.substring("setting.".length()), parsed);
                }
            }
        }
        for (String moduleId : union(enabled.keySet(), keybinds.keySet(), settings.keySet())) {
            modules.put(moduleId, new ModuleState(
                    enabled.getOrDefault(moduleId, false),
                    keybinds.getOrDefault(moduleId, -1),
                    settings.getOrDefault(moduleId, new LinkedHashMap<>())));
        }
        boolean silentAim = Boolean.parseBoolean(
                properties.getProperty(GLOBAL_SILENT_AIM, "true"));
        return new Snapshot(modules, silentAim);
    }

    private String availableName(String baseName) {
        if (!exists(baseName)) return baseName;
        for (int index = 2; index < 10_000; index++) {
            String suffix = " " + index;
            String candidate = baseName.substring(0, Math.min(baseName.length(), 40 - suffix.length())) + suffix;
            if (!exists(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not find an available profile name");
    }

    void delete(String name) {
        try {
            Files.deleteIfExists(fileFor(name));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not delete profile " + name, exception);
        }
    }

    void rename(String oldName, String newName) {
        try {
            Files.move(fileFor(oldName), fileFor(newName));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not rename profile " + oldName, exception);
        }
    }

    private Path fileFor(String name) {
        return directory.resolve(name.strip() + EXTENSION);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @SafeVarargs
    private static List<String> union(java.util.Set<String>... sets) {
        java.util.Set<String> ordered = new java.util.LinkedHashSet<>();
        for (java.util.Set<String> set : sets) {
            ordered.addAll(set);
        }
        return new ArrayList<>(ordered);
    }
}
