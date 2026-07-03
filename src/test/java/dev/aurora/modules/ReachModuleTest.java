package dev.aurora.modules;

import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachModuleTest {
    @Test
    void appliesRangeInMultiplayer() {
        FakeBridge bridge = new FakeBridge(false);
        ReachModule module = new ReachModule(bridge);

        module.setEnabled(true);
        module.onTick(TickEvent.now());

        assertTrue(module.enabled());
        assertEquals(1, bridge.applied);
        assertEquals(4.5D, bridge.lastRange);
        assertEquals(0, bridge.resets);
    }

    @Test
    void appliesClampedRangeInMultiplayer() {
        FakeBridge bridge = new FakeBridge(false);
        ReachModule module = new ReachModule(bridge);
        module.settings().getFirst().setValue(99.0D);

        module.setEnabled(true);
        module.onTick(TickEvent.now());

        assertTrue(module.enabled());
        assertTrue(bridge.lastRange >= 4.5D);
        assertTrue(bridge.lastRange <= 6.0D);
    }

    @Test
    void restoresReachWhenDisabled() {
        FakeBridge bridge = new FakeBridge(false);
        ReachModule module = new ReachModule(bridge);

        module.setEnabled(true);
        module.onTick(TickEvent.now());
        module.setEnabled(false);

        assertEquals(1, bridge.resets);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private final boolean singlePlayer;
        private int applied;
        private int resets;
        private double lastRange;

        private FakeBridge(boolean singlePlayer) {
            this.singlePlayer = singlePlayer;
        }

        @Override
        public boolean isSinglePlayer() {
            return singlePlayer;
        }

        @Override
        public boolean applyReach(double range) {
            applied++;
            lastRange = range;
            return true;
        }

        @Override
        public boolean resetReach() {
            resets++;
            return true;
        }

        @Override
        public boolean renderStatusText(Object renderContext, String text) {
            return false;
        }

        @Override
        public String environment() {
            return "test";
        }
    }
}
