package dev.aurora.modules;

import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoJumpDelayModuleTest {
    @Test
    void clearsTheJumpCooldownEveryTickWhileEnabled() {
        FakeBridge bridge = new FakeBridge();
        NoJumpDelayModule module = new NoJumpDelayModule(bridge);
        module.setEnabled(true);

        module.onTick(TickEvent.now());
        module.onTick(TickEvent.now());

        assertEquals(2, bridge.clears);
        module.setEnabled(false);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private int clears;

        @Override
        public boolean clearJumpCooldown() {
            clears++;
            return true;
        }

        @Override
        public boolean isSinglePlayer() {
            return false;
        }

        @Override
        public boolean applyReach(double range) {
            return false;
        }

        @Override
        public boolean resetReach() {
            return false;
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
