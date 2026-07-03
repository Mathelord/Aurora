package dev.aurora.injector;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public final class AttachService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttachService.class);

    public AttachResult attach(String pid, AgentArguments arguments) {
        Path jarPath;
        try {
            jarPath = currentJarPath();
            if (Files.isDirectory(jarPath)) {
                return AttachResult.failure("Aurora is running from class files. Build and run the shadow jar in build/libs so the agent manifest is available.");
            }
            String validationError = validateAgentJar(jarPath);
            if (validationError != null) {
                return AttachResult.failure(validationError);
            }
        } catch (RuntimeException exception) {
            return AttachResult.failure("Could not resolve Aurora jar path: " + exception.getMessage());
        }

        VirtualMachine vm = null;
        try {
            AgentLocation agent = targetVisibleAgent(pid, jarPath);
            LOGGER.info("Attaching Aurora agent to PID {}", pid);
            vm = VirtualMachine.attach(pid);
            vm.loadAgent(agent.targetPath(), arguments.encode());
            return AttachResult.success("Agent loaded into PID " + pid);
        } catch (AttachNotSupportedException exception) {
            return AttachResult.failure("Attach is not supported for PID " + pid + ". Start Minecraft with a JDK or use the documented -javaagent fallback.");
        } catch (IOException exception) {
            return AttachResult.failure("Attach failed: " + explainIOException(exception));
        } catch (Exception exception) {
            return AttachResult.failure("Attach failed: " + exception.getMessage());
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (IOException exception) {
                    LOGGER.warn("Could not detach from PID {}", pid, exception);
                }
            }
        }
    }

    private static String explainIOException(IOException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        String lower = message.toLowerCase();
        if (lower.contains("permission") || lower.contains("operation not permitted")) {
            return message + ". Check that Aurora and Minecraft run as the same user. On Linux, ptrace_scope may also block attach.";
        }
        if (lower.contains("attachnot") || lower.contains("socket")) {
            return message + ". The target JVM may have disabled attach with -XX:+DisableAttachMechanism.";
        }
        return message;
    }

    static String validateAgentJar(Path jarPath) {
        if (!Files.isRegularFile(jarPath)) {
            return "Agent JAR does not exist: " + jarPath.toAbsolutePath();
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            if (jar.getManifest() == null) {
                return "Agent JAR has no manifest: " + jarPath.toAbsolutePath();
            }
            String agentClass = jar.getManifest().getMainAttributes().getValue("Agent-Class");
            if (agentClass == null || agentClass.isBlank()) {
                return "Agent JAR has no Agent-Class attribute: " + jarPath.toAbsolutePath();
            }
            String classEntry = agentClass.replace('.', '/') + ".class";
            if (jar.getJarEntry(classEntry) == null) {
                return "Agent JAR does not contain " + classEntry + ": " + jarPath.toAbsolutePath();
            }
            return null;
        } catch (IOException exception) {
            return "Could not read Agent JAR " + jarPath.toAbsolutePath() + ": " + exception.getMessage();
        }
    }

    private static AgentLocation targetVisibleAgent(String pid, Path sourceJar) {
        if (!isLinux() || pid == null || !pid.matches("[0-9]+")) {
            return new AgentLocation(sourceJar.toAbsolutePath().toString());
        }

        Path targetTemp = Path.of("/proc", pid, "root", "tmp");
        if (!Files.isDirectory(targetTemp) || !Files.isWritable(targetTemp)) {
            LOGGER.warn("Target-visible temp directory {} is unavailable; using the original agent path", targetTemp);
            return new AgentLocation(sourceJar.toAbsolutePath().toString());
        }

        String fileName = "aurora-agent-" + UUID.randomUUID() + ".jar";
        Path stagedJar = targetTemp.resolve(fileName);
        try {
            Files.copy(sourceJar, stagedJar, StandardCopyOption.COPY_ATTRIBUTES);
            stagedJar.toFile().deleteOnExit();
            String targetPath = "/tmp/" + fileName;
            LOGGER.info("Staged Aurora agent for target PID {} at {}", pid, targetPath);
            return new AgentLocation(targetPath);
        } catch (IOException exception) {
            LOGGER.warn("Could not stage the agent in target-visible storage; using {}", sourceJar, exception);
            return new AgentLocation(sourceJar.toAbsolutePath().toString());
        }
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux");
    }

    private static Path currentJarPath() {
        try {
            return Path.of(AttachService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record AgentLocation(String targetPath) {
    }

    public record AttachResult(boolean success, String message) {
        public static AttachResult success(String message) {
            return new AttachResult(true, message);
        }

        public static AttachResult failure(String message) {
            return new AttachResult(false, message);
        }
    }
}
