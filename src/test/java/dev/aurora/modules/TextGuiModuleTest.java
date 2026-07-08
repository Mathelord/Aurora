package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.minecraft.HudSize;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextGuiModuleTest {
    private static final Object CONTEXT = new Object();
    private static final Instant START = Instant.parse("2026-07-08T12:00:00Z");

    @Test
    void rendersEnabledModulesAtTopRightOrderedByWidth() {
        FakeBridge bridge = new FakeBridge(HudSize.of(200, 100));
        TestModule shortName = enabledModule("Fly");
        TestModule longName = enabledModule("Combat Module");
        TestModule disabled = new TestModule("Disabled Module");
        TextGuiModule textGui = new TextGuiModule(bridge, () -> List.of(shortName, disabled, longName));

        finishEntering(textGui, bridge);

        assertEquals(List.of("Combat Module", "Fly"), bridge.text);
        assertEquals(194, bridge.fills.get(0).right);
        assertTrue(bridge.fills.get(0).left < bridge.fills.get(2).left,
                "wider rows should extend farther left from the common right edge");
    }

    @Test
    void supportsAlphabeticalSortingAndTopLeftPosition() {
        FakeBridge bridge = new FakeBridge(HudSize.of(200, 100));
        TestModule zebra = enabledModule("Z");
        TestModule alpha = enabledModule("Alpha");
        TextGuiModule textGui = new TextGuiModule(bridge, () -> List.of(zebra, alpha));
        setting(textGui, "position").setValue(1.0D);
        setting(textGui, "sorting").setValue(1.0D);

        finishEntering(textGui, bridge);

        assertEquals(List.of("Alpha", "Z"), bridge.text);
        assertEquals(6, bridge.fills.get(0).left);
        assertEquals(8, bridge.fills.get(1).right);
    }

    @Test
    void keepsDisabledModuleUntilExitAnimationFinishes() {
        FakeBridge bridge = new FakeBridge(HudSize.of(200, 100));
        TestModule combat = enabledModule("Combat");
        TextGuiModule textGui = new TextGuiModule(bridge, () -> List.of(combat));
        finishEntering(textGui, bridge);

        combat.setEnabled(false);
        bridge.clear();
        textGui.onRender(new RenderEvent(START.plusMillis(250), CONTEXT));
        assertEquals(List.of("Combat"), bridge.text);

        bridge.clear();
        for (int millis = 300; millis <= 450; millis += 50) {
            textGui.onRender(new RenderEvent(START.plusMillis(millis), CONTEXT));
            bridge.clear();
        }
        textGui.onRender(new RenderEvent(START.plusMillis(500), CONTEXT));
        assertTrue(bridge.text.isEmpty());
    }

    @Test
    void appliesBackgroundVisibilityAccentColorAndSelfVisibility() {
        FakeBridge bridge = new FakeBridge(HudSize.of(200, 100));
        TestModule combat = enabledModule("Combat");
        TextGuiModule[] holder = new TextGuiModule[1];
        TextGuiModule textGui = new TextGuiModule(bridge, () -> List.of(combat, holder[0]));
        holder[0] = textGui;
        textGui.setEnabled(true);
        setting(textGui, "background").setValue(0.0D);
        setting(textGui, "accent-color").setValue(0x12AB34);

        finishEntering(textGui, bridge);

        assertEquals(List.of("Combat"), bridge.text);
        assertEquals(1, bridge.fills.size(), "only the accent edge should be drawn without backgrounds");
        assertEquals(0xFF12AB34, bridge.fills.get(0).color);

        setting(textGui, "show-self").setValue(1.0D);
        bridge.clear();
        for (int millis = 250; millis <= 450; millis += 50) {
            textGui.onRender(new RenderEvent(START.plusMillis(millis), CONTEXT));
            bridge.clear();
        }
        textGui.onRender(new RenderEvent(START.plusMillis(500), CONTEXT));
        assertTrue(bridge.text.contains("Text GUI"));
    }

    @Test
    void rainbowIsOptInAndOnlyChangesAccentColors() {
        FakeBridge bridge = new FakeBridge(HudSize.of(200, 100));
        TextGuiModule textGui = new TextGuiModule(bridge,
                () -> List.of(enabledModule("Combat"), enabledModule("Fly")));
        setting(textGui, "rainbow").setValue(1.0D);

        finishEntering(textGui, bridge);

        int firstAccent = bridge.fills.get(1).color;
        int secondAccent = bridge.fills.get(3).color;
        assertTrue(firstAccent != secondAccent, "rainbow should offset the hue for each row");
        assertEquals(0xFFF2F2F7, bridge.textColors.get(0));
        assertEquals(0xFFF2F2F7, bridge.textColors.get(1));
    }

    @Test
    void fallsBackToTopLeftAndIgnoresMissingContext() {
        FakeBridge bridge = new FakeBridge(HudSize.unavailable());
        TextGuiModule textGui = new TextGuiModule(bridge, () -> List.of(enabledModule("Combat")));

        textGui.onRender(new RenderEvent(START, null));
        assertTrue(bridge.text.isEmpty());
        finishEntering(textGui, bridge);

        assertEquals(6, bridge.fills.get(0).left);
    }

    private static void finishEntering(TextGuiModule textGui, FakeBridge bridge) {
        for (int millis = 0; millis <= 200; millis += 50) {
            textGui.onRender(new RenderEvent(START.plusMillis(millis), CONTEXT));
        }
        bridge.clear();
        textGui.onRender(new RenderEvent(START.plusMillis(200), CONTEXT));
    }

    private static ModuleSetting setting(TextGuiModule module, String id) {
        return module.settings().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private static TestModule enabledModule(String name) {
        TestModule module = new TestModule(name);
        module.setEnabled(true);
        return module;
    }

    private static final class TestModule extends AbstractModule {
        private TestModule(String name) {
            super(name.toLowerCase().replace(' ', '-'), name);
        }
    }

    private static final class FakeBridge implements MinecraftBridge {
        private final HudSize hudSize;
        private final List<String> text = new ArrayList<>();
        private final List<Integer> textColors = new ArrayList<>();
        private final List<FillCall> fills = new ArrayList<>();

        private FakeBridge(HudSize hudSize) {
            this.hudSize = hudSize;
        }

        @Override public boolean isSinglePlayer() { return false; }
        @Override public boolean applyReach(double range) { return false; }
        @Override public boolean resetReach() { return false; }
        @Override public boolean renderStatusText(Object renderContext, String text) { return false; }
        @Override public String environment() { return "test"; }
        @Override public int textWidth(String value) { return value.length() * 6; }
        @Override public HudSize hudSize(Object context) { return hudSize; }

        @Override
        public boolean fill(Object context, int left, int top, int right, int bottom, int color) {
            fills.add(new FillCall(left, top, right, bottom, color));
            return true;
        }

        @Override
        public boolean fillRounded(Object context, int left, int top, int right, int bottom,
                                   int radius, int color) {
            fills.add(new FillCall(left, top, right, bottom, color));
            return true;
        }

        @Override
        public boolean drawText(Object context, String value, int x, int y, int color) {
            text.add(value);
            textColors.add(color);
            return true;
        }

        private void clear() {
            text.clear();
            textColors.clear();
            fills.clear();
        }
    }

    private record FillCall(int left, int top, int right, int bottom, int color) {}
}
