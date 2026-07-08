package dev.aurora.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HookProfileTest {
    private static final List<String> MAPPED_121_VERSIONS = List.of(
            "1.21.4", "1.21.11");
    private static final Map<String, String> OBFUSCATED_MINECRAFT_CLASSES = Map.of(
            "1.21.4", "flk", "1.21.11", "gfj");

    @Test
    void createsVersionSpecificProfilesForEveryMapped121Release() {
        for (String version : MAPPED_121_VERSIONS) {
            HookProfile profile = HookProfile.forVersion(version);

            assertEquals(version, profile.minecraftVersion());
            assertTrue(profile.tickClasses().contains("net.minecraft.class_310"));
            assertTrue(profile.tickMethods().contains("method_1574"));
            assertTrue(profile.tickClasses().contains(OBFUSCATED_MINECRAFT_CLASSES.get(version)));
            assertTrue(profile.packetClasses().contains("net.minecraft.class_2535"));
            assertTrue(profile.outboundPacketMethods().contains("method_10743"));
            assertTrue(profile.inboundPacketMethods().contains("method_10759"));
        }
    }

    @Test
    void switchesWorldGeometryHookAfterRenderEntitiesWasRemoved() {
        assertTrue(HookProfile.forVersion("1.21.4").hasWorldRenderHook());
        HookProfile modern = HookProfile.forVersion("1.21.11");
        assertTrue(modern.hasWorldRenderHook());
        assertEquals(3, modern.worldRenderArgumentCount());
        assertTrue(modern.worldRenderMethods().contains("method_62206"));
    }

    @Test
    void rejectsIntermediate121Versions() {
        for (String version : List.of(
                "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10")) {
            assertThrows(IllegalArgumentException.class, () -> HookProfile.forVersion(version));
        }
    }

    @Test
    void rejectsRemoved26xSupport() {
        assertThrows(IllegalArgumentException.class, () -> HookProfile.forVersion("26.1.2"));
        assertThrows(IllegalArgumentException.class, () -> HookProfile.forVersion("26.2"));
    }
}
