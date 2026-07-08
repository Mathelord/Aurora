package dev.aurora.injector;

import java.util.List;
import java.util.Optional;

public record JavaProcess(
        String pid,
        String displayName,
        String commandLine,
        boolean likelyMinecraft,
        List<String> hints,
        Optional<String> minecraftVersion
) {
}
