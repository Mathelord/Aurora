package dev.aurora.injector;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwingThemeTest {
    @Test
    void usesBlackTextOnBrightAccentColors() {
        assertEquals(Color.BLACK, SwingTheme.contrastText(Color.WHITE));
        assertEquals(Color.BLACK, SwingTheme.contrastText(new Color(0xFF, 0xE0, 0x40)));
    }

    @Test
    void usesWhiteTextOnDarkAccentColors() {
        assertEquals(Color.WHITE, SwingTheme.contrastText(Color.BLACK));
        assertEquals(Color.WHITE, SwingTheme.contrastText(new Color(0x18, 0x66, 0xDF)));
    }
}
