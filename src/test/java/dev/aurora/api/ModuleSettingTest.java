package dev.aurora.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleSettingTest {
    @Test
    void storesTrimmedDescriptionMetadata() {
        ModuleSetting setting = new ModuleSetting("range", "Range", 4.5D, 1.0D, 8.0D, 0.1D)
                .description("  Maximum target acquisition distance.  ");

        assertEquals("Maximum target acquisition distance.", setting.description());
    }

    @Test
    void treatsNullDescriptionAsEmpty() {
        ModuleSetting setting = new ModuleSetting("range", "Range", 4.5D, 1.0D, 8.0D, 0.1D)
                .description(null);

        assertEquals("", setting.description());
    }
}
