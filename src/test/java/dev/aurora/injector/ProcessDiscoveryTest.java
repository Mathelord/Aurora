package dev.aurora.injector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDiscoveryTest {
    @Test
    void detectsOfficialLauncherMinecraftCommand() {
        assertTrue(ProcessDiscovery.minecraftHints("java -cp app.jar com.example.Main").isEmpty());
        assertTrue(ProcessDiscovery.minecraftHints("net.minecraft.client.main.Main --gameDir C:/Users/me/AppData/Roaming/.minecraft").size() >= 2);
    }

    @Test
    void detectsPrismInstancePath() {
        assertTrue(ProcessDiscovery.minecraftHints("/home/me/.local/share/PrismLauncher/instances/Test/.minecraft").stream()
                .anyMatch(hint -> hint.contains("Prism")));
    }
}
