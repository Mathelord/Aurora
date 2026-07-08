package dev.aurora.injector;

import dev.aurora.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a Minecraft release from the launch command without attaching to the target JVM. */
final class MinecraftVersionDetector {
    private static final String RELEASE = "(?:1\\.\\d+(?:\\.\\d+)?|[2-9]\\d\\.\\d+(?:\\.\\d+)?)"
            + "(?:-(?:pre|rc|snapshot)[-.]?\\d+)?";
    private static final Pattern EXPLICIT_VERSION = Pattern.compile(
            "(?:^|\\s)(?:--version|--fml\\.mcVersion)(?:=|\\s+)(?:\\\"([^\\\"]+)\\\"|'([^']+)'|(\\S+))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_DIRECTORY = Pattern.compile(
            "(?:^|[/\\\\])versions[/\\\\](" + RELEASE + ")(?:[/\\\\]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NATIVE_LIBRARY_PATH = argumentPattern("-Djava\\.library\\.path=");
    private static final Pattern GAME_DIRECTORY = argumentPattern("--gameDir(?:=|\\s+)");
    private static final List<String> INSTANCE_METADATA_FILES = List.of(
            "mmc-pack.json",
            "profile.json",
            "minecraftinstance.json",
            "instance.json");
    private static final Set<String> MINECRAFT_VERSION_KEYS = Set.of(
            "game_version",
            "gameversion",
            "minecraft_version",
            "minecraftversion");
    private static final Pattern RELEASE_IN_VALUE = Pattern.compile(
            "(?<![A-Za-z0-9.])(" + RELEASE + ")(?![A-Za-z0-9.])",
            Pattern.CASE_INSENSITIVE);

    private MinecraftVersionDetector() {
    }

    static Optional<String> detect(String displayName, String commandLine) {
        String command = commandLine == null ? "" : commandLine;

        Matcher explicit = EXPLICIT_VERSION.matcher(command);
        while (explicit.find()) {
            String value = firstNonNull(explicit.group(1), explicit.group(2), explicit.group(3));
            Optional<String> release = releaseFrom(value);
            if (release.isPresent()) {
                return release;
            }
        }

        Matcher directory = VERSION_DIRECTORY.matcher(command);
        if (directory.find()) {
            return Optional.of(directory.group(1));
        }

        Optional<String> metadataVersion = versionFromLauncherMetadata(command);
        if (metadataVersion.isPresent()) {
            return metadataVersion;
        }

        return releaseFrom(displayName == null ? "" : displayName);
    }

    private static Optional<String> versionFromLauncherMetadata(String commandLine) {
        for (Path candidate : instanceDirectories(commandLine)) {
            Optional<String> version = versionFromInstanceDirectory(candidate);
            if (version.isPresent()) {
                return version;
            }
        }
        return Optional.empty();
    }

    private static List<Path> instanceDirectories(String commandLine) {
        java.util.ArrayList<Path> directories = new java.util.ArrayList<>();
        collectArgumentPaths(commandLine, NATIVE_LIBRARY_PATH, true, directories);
        collectArgumentPaths(commandLine, GAME_DIRECTORY, false, directories);
        return directories.stream().distinct().toList();
    }

    private static void collectArgumentPaths(String commandLine, Pattern pattern, boolean stripNatives,
                                             List<Path> destination) {
        Matcher matcher = pattern.matcher(commandLine);
        while (matcher.find()) {
            String rawPath = firstNonNull(matcher.group(1), matcher.group(2), matcher.group(3));
            try {
                Path path = Path.of(rawPath);
                if (stripNatives && path.getFileName() != null
                        && "natives".equalsIgnoreCase(path.getFileName().toString())) {
                    path = path.getParent();
                }
                if (path != null) {
                    destination.add(path);
                    if ("minecraft".equalsIgnoreCase(String.valueOf(path.getFileName()))
                            && path.getParent() != null) {
                        destination.add(path.getParent());
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // A path from another operating system is only parsed on that target OS.
            }
        }
    }

    private static Optional<String> versionFromInstanceDirectory(Path directory) {
        for (String fileName : INSTANCE_METADATA_FILES) {
            Path metadata = directory.resolve(fileName);
            if (!Files.isRegularFile(metadata)) {
                continue;
            }
            Optional<String> version = versionFromMetadata(metadata, fileName);
            if (version.isPresent()) {
                return version;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> versionFromMetadata(Path metadata, String fileName) {
        try {
            Object root = Json.parse(Files.readString(metadata));
            if ("mmc-pack.json".equals(fileName)) {
                Optional<String> componentVersion = minecraftComponentVersion(root);
                if (componentVersion.isPresent()) {
                    return componentVersion;
                }
            }
            return findNamedMinecraftVersion(root);
        } catch (IOException | IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> minecraftComponentVersion(Object root) {
        if (!(root instanceof Map<?, ?> rootObject)
                || !(rootObject.get("components") instanceof List<?> components)) {
            return Optional.empty();
        }
        for (Object component : components) {
            if (component instanceof Map<?, ?> values
                    && "net.minecraft".equals(values.get("uid"))
                    && values.get("version") instanceof String version) {
                return releaseFrom(version);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findNamedMinecraftVersion(Object value) {
        if (value instanceof Map<?, ?> object) {
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                if (entry.getKey() instanceof String key
                        && MINECRAFT_VERSION_KEYS.contains(key.toLowerCase(java.util.Locale.ROOT))
                        && entry.getValue() instanceof String version) {
                    Optional<String> release = releaseFrom(version);
                    if (release.isPresent()) {
                        return release;
                    }
                }
            }
            for (Object nested : object.values()) {
                Optional<String> release = findNamedMinecraftVersion(nested);
                if (release.isPresent()) {
                    return release;
                }
            }
        } else if (value instanceof List<?> values) {
            for (Object nested : values) {
                Optional<String> release = findNamedMinecraftVersion(nested);
                if (release.isPresent()) {
                    return release;
                }
            }
        }
        return Optional.empty();
    }

    private static Pattern argumentPattern(String argument) {
        return Pattern.compile(
                "(?:^|\\s)" + argument + "(?:\\\"([^\\\"]+)\\\"|'([^']+)'|(\\S+))",
                Pattern.CASE_INSENSITIVE);
    }

    private static Optional<String> releaseFrom(String value) {
        Matcher matcher = RELEASE_IN_VALUE.matcher(value);
        String lastRelease = null;
        while (matcher.find()) {
            lastRelease = matcher.group(1);
        }
        return Optional.ofNullable(lastRelease);
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return "";
    }
}
