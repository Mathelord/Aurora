package dev.aurora.injector;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/** Converts Swing/AWT keyboard events to the GLFW key codes consumed inside Minecraft. */
final class KeybindCodec {
    static final int UNBOUND = -1;
    private static final Map<Integer, String> SPECIAL_NAMES = specialNames();

    private KeybindCodec() {
    }

    static int fromKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if ((code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z)
                || (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9)) {
            return code;
        }
        return switch (code) {
            case KeyEvent.VK_SPACE -> 32;
            case KeyEvent.VK_COMMA -> 44;
            case KeyEvent.VK_MINUS -> 45;
            case KeyEvent.VK_PERIOD -> 46;
            case KeyEvent.VK_SLASH -> 47;
            case KeyEvent.VK_SEMICOLON -> 59;
            case KeyEvent.VK_EQUALS -> 61;
            case KeyEvent.VK_OPEN_BRACKET -> 91;
            case KeyEvent.VK_BACK_SLASH -> 92;
            case KeyEvent.VK_CLOSE_BRACKET -> 93;
            case KeyEvent.VK_BACK_QUOTE -> 96;
            case KeyEvent.VK_ESCAPE -> 256;
            case KeyEvent.VK_ENTER -> 257;
            case KeyEvent.VK_TAB -> 258;
            case KeyEvent.VK_BACK_SPACE -> 259;
            case KeyEvent.VK_INSERT -> 260;
            case KeyEvent.VK_DELETE -> 261;
            case KeyEvent.VK_RIGHT -> 262;
            case KeyEvent.VK_LEFT -> 263;
            case KeyEvent.VK_DOWN -> 264;
            case KeyEvent.VK_UP -> 265;
            case KeyEvent.VK_PAGE_UP -> 266;
            case KeyEvent.VK_PAGE_DOWN -> 267;
            case KeyEvent.VK_HOME -> 268;
            case KeyEvent.VK_END -> 269;
            case KeyEvent.VK_CAPS_LOCK -> 280;
            case KeyEvent.VK_SCROLL_LOCK -> 281;
            case KeyEvent.VK_NUM_LOCK -> 282;
            case KeyEvent.VK_PRINTSCREEN -> 283;
            case KeyEvent.VK_PAUSE -> 284;
            case KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4,
                    KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8,
                    KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12 ->
                    290 + code - KeyEvent.VK_F1;
            case KeyEvent.VK_SHIFT -> event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT ? 344 : 340;
            case KeyEvent.VK_CONTROL -> event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT ? 345 : 341;
            case KeyEvent.VK_ALT, KeyEvent.VK_ALT_GRAPH -> event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT ? 346 : 342;
            case KeyEvent.VK_META, KeyEvent.VK_WINDOWS -> event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT ? 347 : 343;
            default -> UNBOUND;
        };
    }

    static String displayName(int glfwCode) {
        if (glfwCode == UNBOUND) {
            return "Unbound";
        }
        if ((glfwCode >= 'A' && glfwCode <= 'Z') || (glfwCode >= '0' && glfwCode <= '9')) {
            return Character.toString(glfwCode);
        }
        return SPECIAL_NAMES.getOrDefault(glfwCode, "Key " + glfwCode);
    }

    private static Map<Integer, String> specialNames() {
        Map<Integer, String> names = new HashMap<>();
        names.put(32, "Space");
        names.put(44, ","); names.put(45, "-"); names.put(46, "."); names.put(47, "/");
        names.put(59, ";"); names.put(61, "="); names.put(91, "["); names.put(92, "\\");
        names.put(93, "]"); names.put(96, "`");
        String[] navigation = {"Escape", "Enter", "Tab", "Backspace", "Insert", "Delete",
                "Right", "Left", "Down", "Up", "Page Up", "Page Down", "Home", "End"};
        for (int i = 0; i < navigation.length; i++) names.put(256 + i, navigation[i]);
        names.put(280, "Caps Lock"); names.put(281, "Scroll Lock"); names.put(282, "Num Lock");
        names.put(283, "Print Screen"); names.put(284, "Pause");
        for (int i = 1; i <= 12; i++) names.put(289 + i, "F" + i);
        names.put(340, "Left Shift"); names.put(341, "Left Ctrl"); names.put(342, "Left Alt");
        names.put(343, "Left Super"); names.put(344, "Right Shift"); names.put(345, "Right Ctrl");
        names.put(346, "Right Alt"); names.put(347, "Right Super");
        return Map.copyOf(names);
    }
}
