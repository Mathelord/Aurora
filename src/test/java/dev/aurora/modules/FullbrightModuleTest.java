package dev.aurora.modules;

import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FullbrightModuleTest {
    @Test
    void setsBrightnessToOneHundredPercentWhenDisabled() {
        FakeBridge bridge = new FakeBridge(0.73D);
        FullbrightModule module = new FullbrightModule(bridge);

        module.setEnabled(true);
        module.onTick(TickEvent.now());
        module.setEnabled(false);

        assertEquals(1.0D, bridge.gamma);
        assertEquals(1, bridge.restores);
    }

    @Test
    void staleTickAfterDisableDoesNotOverwriteRestoredGamma() {
        FakeBridge bridge = new FakeBridge(0.31D);
        FullbrightModule module = new FullbrightModule(bridge);

        module.setEnabled(true);
        module.setEnabled(false);
        // Simulates a tick dispatched after ModuleManager observed enabled=true, but whose callback
        // did not run until the control-panel thread had disabled the module.
        module.onTick(TickEvent.now());

        assertEquals(1.0D, bridge.gamma);
        assertEquals(1, bridge.restores);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private double gamma;
        private int restores;

        private FakeBridge(double gamma) {
            this.gamma = gamma;
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
        public Optional<Double> gamma() {
            return Optional.of(gamma);
        }

        @Override
        public boolean setGamma(double value) {
            gamma = value;
            return true;
        }

        @Override
        public boolean restoreGamma(double value) {
            restores++;
            gamma = value;
            return true;
        }

        @Override
        public String environment() {
            return "test";
        }
    }
}
