package dev.aurora.injector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesAgentManifestAndClassEntry() throws IOException {
        Path jar = temporaryDirectory.resolve("agent.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Agent-Class", "dev.aurora.Agent");
        try (JarOutputStream output = new JarOutputStream(java.nio.file.Files.newOutputStream(jar), manifest)) {
            output.putNextEntry(new JarEntry("dev/aurora/Agent.class"));
            output.write(new byte[]{0});
            output.closeEntry();
        }

        assertNull(AttachService.validateAgentJar(jar));
    }

    @Test
    void rejectsJarWithoutAgentClass() throws IOException {
        Path jar = temporaryDirectory.resolve("not-an-agent.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream ignored = new JarOutputStream(java.nio.file.Files.newOutputStream(jar), manifest)) {
        }

        String error = AttachService.validateAgentJar(jar);

        assertTrue(error.contains("no Agent-Class"));
    }

    @Test
    void rejectsMissingJar() {
        String error = AttachService.validateAgentJar(temporaryDirectory.resolve("missing.jar"));

        assertTrue(error.contains("does not exist"));
    }
}
