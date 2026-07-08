package dev.aurora.modules;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.Vec3;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.input.RealClickSimulator;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.CombatState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SilentAuraModuleTest {
    private final StubBridge bridge = new StubBridge();
    private final SilentAuraModule module = new SilentAuraModule(bridge);
    private final StubCrystalComboController crystalComboController = new StubCrystalComboController();

    @BeforeEach
    void wireClickSimulator() {
        RealClickSimulator.init(bridge);
        module.bindCrystalComboController(crystalComboController);
    }

    @AfterEach
    void disableModule() {
        module.setEnabled(false);
    }

    @Test
    void aimsAndAttacksSelectedTargetWhenCooldownIsReady() {
        set("perfect-early", 0.0D);
        set("perfect-late", 0.0D);
        module.setEnabled(true);

        module.onRender(RenderEvent.now(null));
        module.onTick(TickEvent.now());

        assertTrue(bridge.rotationApplied);
        assertEquals("target", bridge.attackedTarget);
    }

    @Test
    void advancesAimOnRenderOnly() {
        module.setEnabled(true);

        module.onTick(TickEvent.now());
        assertFalse(bridge.rotationApplied);

        module.onRender(RenderEvent.now(null));
        assertTrue(bridge.rotationApplied);
    }

    @Test
    void doesNotAttackWhenTargetIsOutOfVanillaReach() {
        set("perfect-early", 0.0D);
        set("perfect-late", 0.0D);
        // Target is in aim range, but the reach-limited vanilla crosshair is not on it.
        bridge.crosshairEntityId = null;
        module.setEnabled(true);

        module.onRender(RenderEvent.now(null));
        module.onTick(TickEvent.now());

        assertTrue(bridge.rotationApplied);
        assertNull(bridge.attackedTarget);
    }

    @Test
    void missUsesRealClickWhenNearbyTargetIsOutsideCrosshair() {
        set("miss-chance", 100.0D);
        bridge.crosshairEntityId = null;
        module.setEnabled(true);

        module.onTick(TickEvent.now());

        assertEquals(1, bridge.clicks);
        assertNull(bridge.attackedTarget);
    }

    @Test
    void queuesCrystalComboAfterSuccessfulAttackWhenControllerAllowsIt() {
        set("perfect-early", 0.0D);
        set("perfect-late", 0.0D);
        module.setEnabled(true);

        module.onTick(TickEvent.now());

        assertEquals("target", crystalComboController.queuedTargetId);
    }

    @Test
    void exposesSimplifiedEclipseSettings() {
        Set<String> ids = module.settings().stream().map(ModuleSetting::id).collect(Collectors.toSet());

        assertTrue(ids.contains("crit-mode"));
        assertTrue(ids.contains("perfect-early"));
        assertTrue(ids.contains("perfect-late"));
        assertTrue(ids.contains("strafe-aim-increase"));
        assertTrue(ids.contains("strafe-aim-boost"));
        assertFalse(ids.contains("auto-attack"));
        assertFalse(ids.contains("combat-mode"));
        assertFalse(ids.contains("cooldown"));
        assertFalse(ids.contains("reaction-min"));
        assertFalse(ids.contains("reaction-max"));
        assertFalse(ids.contains("smoothing"));
        assertFalse(ids.contains("jitter"));
    }

    @Test
    void airCritDoesNotAttackDuringGroundFlaggedJumpTakeoff() {
        set("perfect-early", 0.0D);
        set("perfect-late", 0.0D);
        bridge.combatState = new CombatState(true, true, false, 0.0D, 0.32D,
                false, false, false, false, false, false, false);
        module.setEnabled(true);

        module.onTick(TickEvent.now());
        assertNull(bridge.attackedTarget);

        bridge.combatState = new CombatState(true, false, false, 0.2D, -0.1D,
                false, false, false, false, false, false, false);
        module.onTick(TickEvent.now());
        assertEquals("target", bridge.attackedTarget);
    }

    private void set(String id, double value) {
        ModuleSetting setting = module.settings().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
        setting.setValue(value);
    }

    private static final class StubBridge implements MinecraftBridge {
        private static final AimTarget TARGET =
                new AimTarget("target", "Target", new Vec3(2.0D, 1.0D, 0.0D), 4.0D, 20.0D, -4.0D, 20.0D);

        private boolean rotationApplied;
        // The entity the reach-limited vanilla crosshair currently rests on (null = nothing in reach).
        private String crosshairEntityId = "target";
        private String attackedTarget;
        private int clicks;
        private CombatState combatState = CombatState.unavailable();

        @Override
        public AimContext aimContext(double range, boolean ignoreWalls) {
            return new AimContext(true, false, 0.0D, 0.0D, 0.5D, List.of(TARGET));
        }

        @Override
        public Optional<AimTarget> crosshairEntity(double range) {
            return "target".equals(crosshairEntityId) ? Optional.of(TARGET) : Optional.empty();
        }

        @Override
        public CombatState combatState() {
            return combatState;
        }

        @Override
        public boolean applyAimRotation(double yaw, double pitch) {
            rotationApplied = true;
            return true;
        }

        @Override
        public double attackCooldown() {
            return 1.0D;
        }

        @Override
        public boolean doAttack() {
            // A real left click attacks whatever the vanilla (reach-limited) crosshair is on.
            clicks++;
            attackedTarget = crosshairEntityId;
            return true;
        }

        @Override
        public boolean isSinglePlayer() {
            return true;
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

    private static final class StubCrystalComboController implements CrystalComboController {
        private String queuedTargetId;

        @Override
        public boolean canStartSilentAuraCombo(String targetId) {
            return "target".equals(targetId);
        }

        @Override
        public boolean queueSilentAuraCombo(String targetId) {
            queuedTargetId = targetId;
            return true;
        }
    }
}
