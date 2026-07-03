package dev.aurora.injector;

import java.util.List;

public record JavaProcess(
        String pid,
        String displayName,
        String commandLine,
        boolean likelyMinecraft,
        List<String> hints
) {
}
