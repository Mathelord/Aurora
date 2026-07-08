package dev.aurora.injector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionDetectorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsVanillaVersionArgument() {
        assertEquals("1.21.4", MinecraftVersionDetector.detect(
                "net.minecraft.client.main.Main",
                "java net.minecraft.client.main.Main --version 1.21.4 --gameDir /tmp/game").orElseThrow());
    }

    @Test
    void extractsMinecraftVersionFromFabricVersionArgument() {
        assertEquals("1.21.11", MinecraftVersionDetector.detect(
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "java KnotClient --version fabric-loader-0.18.4-1.21.11").orElseThrow());
    }

    @Test
    void detectsNewVersionSchemeAndPatchRelease() {
        assertEquals("26.1.2", MinecraftVersionDetector.detect(
                "Minecraft", "java Main --version=26.1.2").orElseThrow());
    }

    @Test
    void detectsForgeMinecraftVersionArgument() {
        assertEquals("1.21.5", MinecraftVersionDetector.detect(
                "Forge", "java Main --fml.mcVersion 1.21.5").orElseThrow());
    }

    @Test
    void detectsVersionDirectoryOnClasspath() {
        assertEquals("1.21.8", MinecraftVersionDetector.detect(
                "Minecraft", "java -cp /home/me/.minecraft/versions/1.21.8/1.21.8.jar Main").orElseThrow());
    }

    @Test
    void doesNotMistakeJavaOrLoaderVersionForMinecraft() {
        assertTrue(MinecraftVersionDetector.detect(
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "/jdk-21.0.8/bin/java -cp fabric-loader-0.18.4.jar KnotClient").isEmpty());
    }

    @Test
    void readsMinecraftComponentFromPrismInstanceManifest() throws IOException {
        Files.writeString(temporaryDirectory.resolve("mmc-pack.json"), """
                {
                  "components": [
                    {"uid": "org.lwjgl3", "version": "3.4.1"},
                    {"uid": "net.minecraft", "version": "26.1.2"},
                    {"uid": "net.fabricmc.fabric-loader", "version": "0.19.3"}
                  ]
                }
                """);

        String command = "java -Djava.library.path=" + temporaryDirectory + "/natives "
                + "-cp NewLaunch.jar org.prismlauncher.EntryPoint";

        assertEquals("26.1.2", MinecraftVersionDetector.detect(
                "org.prismlauncher.EntryPoint", command).orElseThrow());
    }

    @Test
    void readsModrinthProfileFromQuotedGameDirectory() throws IOException {
        Path profile = Files.createDirectory(temporaryDirectory.resolve("Profile With Spaces"));
        Files.writeString(profile.resolve("profile.json"), """
                {"name": "Test", "game_version": "1.21.10", "loader": "fabric"}
                """);

        String command = "java Main --gameDir \"" + profile + "\"";

        assertEquals("1.21.10", MinecraftVersionDetector.detect("Minecraft", command).orElseThrow());
    }

    @Test
    void readsGenericLauncherMinecraftVersionMetadata() throws IOException {
        Path instance = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Files.writeString(instance.resolve("instance.json"), """
                {"launcher": {"minecraftVersion": "1.21.9", "version": "4.2.0"}}
                """);

        assertEquals("1.21.9", MinecraftVersionDetector.detect(
                "Minecraft", "java Main --gameDir=" + instance).orElseThrow());
    }
}
