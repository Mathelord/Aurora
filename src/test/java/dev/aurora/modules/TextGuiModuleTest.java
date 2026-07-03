package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ClientModule;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextGuiModuleTest {
    @Test
    void rendersEnabledModulesAtTopLeft() {
        FakeBridge bridge = new FakeBridge();
        TestModule combat = new TestModule("Combat Module");
        combat.setEnabled(true);
        TestModule disabled = new TestModule("Disabled Module");
        TextGuiModule textGui = new TextGuiModule(bridge, () -> List.of(combat, disabled));

        textGui.onRender(RenderEvent.now(new Object()));

        assertEquals(List.of("Combat Module"), bridge.text);
        assertEquals(1, bridge.fills);
        assertEquals(6, bridge.firstLeft);
        assertEquals(6, bridge.firstTop);
    }

    private static final class TestModule extends AbstractModule {
        private TestModule(String name) {
            super(name.toLowerCase().replace(' ', '-'), name);
        }
    }

    private static final class FakeBridge implements MinecraftBridge {
        private final List<String> text = new ArrayList<>();
        private int fills;
        private int firstLeft;
        private int firstTop;

        @Override public boolean isSinglePlayer() { return false; }
        @Override public boolean applyReach(double range) { return false; }
        @Override public boolean resetReach() { return false; }
        @Override public boolean renderStatusText(Object renderContext, String text) { return false; }
        @Override public String environment() { return "test"; }
        @Override public int textWidth(String value) { return value.length() * 6; }

        @Override
        public boolean fill(Object context, int left, int top, int right, int bottom, int color) {
            if (fills == 0) {
                firstLeft = left;
                firstTop = top;
            }
            fills++;
            return true;
        }

        @Override
        public boolean drawText(Object context, String value, int x, int y, int color) {
            text.add(value);
            return true;
        }
    }
}
