package dev.aurora.injector;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class ProcessDiscovery {
    public List<JavaProcess> listJavaProcesses() {
        List<JavaProcess> processes = new ArrayList<>();
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            String pid = descriptor.id();
            String displayName = descriptor.displayName();
            String commandLine = commandLine(pid).orElse(displayName);
            List<String> hints = minecraftHints(displayName + " " + commandLine);
            Optional<String> minecraftVersion = MinecraftVersionDetector.detect(displayName, commandLine);
            processes.add(new JavaProcess(
                    pid, displayName, commandLine, !hints.isEmpty(), hints, minecraftVersion));
        }
        return processes.stream()
                .sorted(Comparator.comparing(JavaProcess::likelyMinecraft).reversed()
                        .thenComparing(JavaProcess::pid))
                .collect(Collectors.toList());
    }

    public static List<String> minecraftHints(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> hints = new ArrayList<>();
        if (lower.contains("net.minecraft.client.main.main") || lower.contains("minecraft")) {
            hints.add("Minecraft main class or argument");
        }
        if (lower.contains(".minecraft") || lower.contains("minecraftlauncher")) {
            hints.add("Official launcher path");
        }
        if (lower.contains("prismlauncher") || lower.contains("multimc") || lower.contains("prismlauncher/instances")) {
            hints.add("Prism/MultiMC instance path");
        }
        if (lower.contains("fabric-loader") || lower.contains("quilt-loader") || lower.contains("forge")) {
            hints.add("Mod loader argument");
        }
        return List.copyOf(hints);
    }

    private Optional<String> commandLine(String pid) {
        Optional<String> procCommandLine = procCommandLine(pid);
        if (procCommandLine.isPresent()) {
            return procCommandLine;
        }
        try {
            return ProcessHandle.of(Long.parseLong(pid))
                    .flatMap(handle -> handle.info().commandLine());
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> procCommandLine(String pid) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of("/proc", pid, "cmdline"));
            if (bytes.length == 0) {
                return Optional.empty();
            }
            String commandLine = new String(bytes, StandardCharsets.UTF_8)
                    .replace('\0', ' ')
                    .trim();
            return commandLine.isEmpty() ? Optional.empty() : Optional.of(commandLine);
        } catch (IOException | SecurityException ignored) {
            return Optional.empty();
        }
    }
}
