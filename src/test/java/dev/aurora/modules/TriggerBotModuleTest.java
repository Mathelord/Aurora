package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.events.TickEvent;
import dev.aurora.input.RealClickSimulator;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.CombatState;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerBotModuleTest {
    private static final AimTarget TARGET = new AimTarget("target", "Target", new Vec3(1.0D, 1.0D, 1.0D), 4.0D, 0.0D, 0.0D, 20.0D);

    @Test
    void attacksWhenAPlayerIsUnderTheCrosshairAndCooldownIsReady() {
        FakeBridge bridge = new FakeBridge(Optional.of(TARGET), 1.0D);
        RealClickSimulator.init(bridge);
        TriggerBotModule module = new TriggerBotModule(bridge);
        module.setEnabled(true);

        module.onTick(TickEvent.now());

        assertEquals(1, bridge.attacks);
        module.setEnabled(false);
    }

    @Test
    void doesNotAttackBeforeCooldownIsReady() {
        FakeBridge bridge = new FakeBridge(Optional.of(TARGET), 0.2D);
        RealClickSimulator.init(bridge);
        TriggerBotModule module = new TriggerBotModule(bridge);
        module.setEnabled(true);

        module.onTick(TickEvent.now());

        assertEquals(0, bridge.attacks);
        module.setEnabled(false);
    }

    @Test
    void doesNothingWithoutACrosshairTarget() {
        FakeBridge bridge = new FakeBridge(Optional.empty(), 1.0D);
        RealClickSimulator.init(bridge);
        TriggerBotModule module = new TriggerBotModule(bridge);
        module.setEnabled(true);

        module.onTick(TickEvent.now());

        assertEquals(0, bridge.attacks);
        module.setEnabled(false);
    }

    @Test
    void holdMouseSuppressesAttacksUntilTheRealButtonIsHeld() {
        FakeBridge bridge = new FakeBridge(Optional.of(TARGET), 1.0D);
        RealClickSimulator.init(bridge);
        TriggerBotModule module = new TriggerBotModule(bridge);
        module.settings().stream().filter(s -> s.id().equals("hold-mouse")).findFirst().orElseThrow().setValue(1.0D);
        module.setEnabled(true);

        module.onTick(TickEvent.now());
        assertEquals(0, bridge.attacks);

        bridge.leftMouseDown = true;
        module.onTick(TickEvent.now());
        assertTrue(bridge.attacks >= 1);
        module.setEnabled(false);
    }

    @Test
    void airCritWaitsUntilThePlayerIsFalling() {
        FakeBridge bridge = new FakeBridge(Optional.of(TARGET), 1.0D);
        bridge.combatState = airborne(0.0D, 0.1D);
        RealClickSimulator.init(bridge);
        TriggerBotModule module = new TriggerBotModule(bridge);
        module.setEnabled(true);

        module.onTick(TickEvent.now());
        assertEquals(0, bridge.attacks);

        bridge.combatState = airborne(0.2D, -0.1D);
        module.onTick(TickEvent.now());
        assertEquals(1, bridge.attacks);
        module.setEnabled(false);
    }

    @Test
    void airCritHoldsFireUntilFallDistanceRegisters() {
        FakeBridge bridge = new FakeBridge(Optional.of(TARGET), 1.0D);
        bridge.combatState = airborne(0.0D, 0.0D);
        RealClickSimulator.init(bridge);
        TriggerBotModule module = new TriggerBotModule(bridge);
        module.setEnabled(true);

        // Airborne but fallDistance never rises: the server would not crit here, so the module
        // must keep holding rather than fabricate a swing after a few ticks.
        for (int tick = 0; tick < 5; tick++) module.onTick(TickEvent.now());
        assertEquals(0, bridge.attacks);

        // Once a real fall distance appears the crit window opens and the attack fires.
        bridge.combatState = airborne(0.2D, -0.1D);
        module.onTick(TickEvent.now());
        assertEquals(1, bridge.attacks);
        module.setEnabled(false);
    }

    private static CombatState airborne(double fallDistance, double velocityY) {
        return new CombatState(true, false, false, fallDistance, velocityY,
                false, false, false, false, false, false, false);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private final Optional<AimTarget> crosshairTarget;
        private final double cooldown;
        private boolean leftMouseDown;
        private int attacks;
        private CombatState combatState = CombatState.unavailable();

        private FakeBridge(Optional<AimTarget> crosshairTarget, double cooldown) {
            this.crosshairTarget = crosshairTarget;
            this.cooldown = cooldown;
        }

        @Override
        public Optional<AimTarget> crosshairEntity(double range) {
            return crosshairTarget;
        }

        @Override
        public double attackCooldown() {
            return cooldown;
        }

        @Override
        public boolean isMouseButtonDown(int button) {
            return leftMouseDown;
        }

        @Override
        public boolean doAttack() {
            attacks++;
            return true;
        }

        @Override
        public CombatState combatState() {
            return combatState;
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
