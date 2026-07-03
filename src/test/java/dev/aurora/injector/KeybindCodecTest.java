package dev.aurora.injector;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeybindCodecTest {
    private final JButton source = new JButton();

    @Test
    void preservesGlfwCompatibleLetterCodes() {
        assertEquals(82, KeybindCodec.fromKeyEvent(event(KeyEvent.VK_R, KeyEvent.KEY_LOCATION_STANDARD)));
        assertEquals("R", KeybindCodec.displayName(82));
    }

    @Test
    void translatesFunctionAndNavigationKeys() {
        assertEquals(290, KeybindCodec.fromKeyEvent(event(KeyEvent.VK_F1, KeyEvent.KEY_LOCATION_STANDARD)));
        assertEquals(263, KeybindCodec.fromKeyEvent(event(KeyEvent.VK_LEFT, KeyEvent.KEY_LOCATION_STANDARD)));
        assertEquals("F1", KeybindCodec.displayName(290));
        assertEquals("Left", KeybindCodec.displayName(263));
    }

    @Test
    void distinguishesLeftAndRightModifiers() {
        assertEquals(340, KeybindCodec.fromKeyEvent(event(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT)));
        assertEquals(344, KeybindCodec.fromKeyEvent(event(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT)));
    }

    private KeyEvent event(int keyCode, int location) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 1L, 0, keyCode, KeyEvent.CHAR_UNDEFINED, location);
    }
}
