package dev.aurora.injector;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModuleUpdateTest {
    @Test
    void roundTripsEnabledKeybindAndNestedSettings() {
        ModuleUpdate written = new ModuleUpdate("reach", true, 82, Map.of("range", 5.2D));

        ModuleUpdate read = ModuleUpdate.fromJson(written.toJson());

        assertEquals("reach", read.id());
        assertEquals(true, read.enabled());
        assertEquals(82, read.keybind());
        assertEquals(5.2D, read.settings().get("range"), 0.0001D);
    }

    @Test
    void keepsOmittedOptionalFieldsOmitted() {
        ModuleUpdate read = ModuleUpdate.fromJson("{\"id\":\"blink\",\"settings\":{}}");

        assertEquals("blink", read.id());
        assertNull(read.enabled());
        assertNull(read.keybind());
        assertEquals(Map.of(), read.settings());
    }
}
