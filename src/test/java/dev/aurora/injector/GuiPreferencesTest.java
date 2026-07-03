package dev.aurora.injector;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuiPreferencesTest {
    @Test
    void parsesAndFormatsSixDigitHexColors() {
        Color color = GuiPreferences.parseColor("#18a0df");

        assertEquals(new Color(0x18, 0xA0, 0xDF), color);
        assertEquals("#18A0DF", GuiPreferences.formatColor(color));
    }

    @Test
    void rejectsInvalidHexColors() {
        assertThrows(IllegalArgumentException.class, () -> GuiPreferences.parseColor("blue"));
        assertThrows(IllegalArgumentException.class, () -> GuiPreferences.parseColor("#123"));
    }
}
