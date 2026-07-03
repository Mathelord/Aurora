package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.CombatState;
import dev.aurora.minecraft.EnchantmentType;
import dev.aurora.minecraft.ItemType;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HitSwapModuleTest {
    private static final AimTarget TARGET = new AimTarget("target", "Target", new Vec3(1.0D, 1.0D, 1.0D), 4.0D, 0.0D, 0.0D, 20.0D);

    @Test
    void swapsToAnAxeWhenTheTargetIsBlockingWithAShield() {
        FakeBridge bridge = new FakeBridge();
        bridge.crosshairTarget = Optional.of(TARGET);
        bridge.leftMouseDown = true;
        bridge.blockingWithShield.put("target", true);
        bridge.hotbarItems.put(ItemType.DIAMOND_AXE, 3);
        HitSwapModule module = new HitSwapModule(bridge);
        module.setEnabled(true);

        module.onAttackAttempt();

        assertEquals(3, bridge.selectedSlot);
        module.setEnabled(false);
    }

    @Test
    void tickDoesNotSwapBeforeAnAttack() {
        FakeBridge bridge = new FakeBridge();
        bridge.crosshairTarget = Optional.of(TARGET);
        bridge.leftMouseDown = true;
        bridge.blockingWithShield.put("target", true);
        bridge.hotbarItems.put(ItemType.DIAMOND_AXE, 3);
        HitSwapModule module = new HitSwapModule(bridge);
        module.setEnabled(true);

        module.onTick(TickEvent.now());

        assertEquals(-1, bridge.selectedSlot);
        module.setEnabled(false);
    }

    @Test
    void doesNotSwapToAnAxeWhenAxesAreDisabled() {
        FakeBridge bridge = new FakeBridge();
        bridge.crosshairTarget = Optional.of(TARGET);
        bridge.leftMouseDown = true;
        bridge.blockingWithShield.put("target", true);
        bridge.hotbarItems.put(ItemType.DIAMOND_AXE, 3);
        HitSwapModule module = new HitSwapModule(bridge);
        module.settings().stream().filter(s -> s.id().equals("axes")).findFirst().orElseThrow().setValue(0.0D);
        module.setEnabled(true);

        module.onAttackAttempt();

        assertEquals(-1, bridge.selectedSlot);
        module.setEnabled(false);
    }

    @Test
    void doesNotSwapToAnAxeWhenShieldIsNotBeingBlocked() {
        FakeBridge bridge = new FakeBridge();
        bridge.crosshairTarget = Optional.of(TARGET);
        bridge.hotbarItems.put(ItemType.DIAMOND_AXE, 3);
        HitSwapModule module = new HitSwapModule(bridge);
        module.setEnabled(true);

        module.onAttackAttempt();

        assertEquals(-1, bridge.selectedSlot);
        module.setEnabled(false);
    }

    @Test
    void maceSwapWaitsForFallWhenSmashOnlyIsEnabled() {
        FakeBridge bridge = new FakeBridge();
        bridge.crosshairTarget = Optional.of(TARGET);
        bridge.leftMouseDown = true;
        bridge.onGround = true;
        bridge.hotbarItems.put(ItemType.MACE, 5);
        HitSwapModule module = new HitSwapModule(bridge);
        module.settings().stream().filter(s -> s.id().equals("maces")).findFirst().orElseThrow().setValue(1.0D);
        module.settings().stream().filter(s -> s.id().equals("smash-only")).findFirst().orElseThrow().setValue(1.0D);
        module.settings().stream().filter(s -> s.id().equals("ground-check")).findFirst().orElseThrow().setValue(0.0D);
        module.setEnabled(true);

        module.onAttackAttempt();
        assertEquals(-1, bridge.selectedSlot);

        bridge.onGround = false;
        bridge.combatState = new CombatState(true, false, false, 0.5D, -0.2D,
                false, false, false, false, false, false, false);
        bridge.leftMouseDown = false;
        module.onAttackAttempt();
        bridge.leftMouseDown = true;
        module.onAttackAttempt();

        assertEquals(5, bridge.selectedSlot);
        module.setEnabled(false);
    }

    @Test
    void requireBreachBlocksAnUnenchantedMace() {
        FakeBridge bridge = new FakeBridge();
        bridge.crosshairTarget = Optional.of(TARGET);
        bridge.leftMouseDown = true;
        bridge.hotbarItems.put(ItemType.MACE, 5);
        HitSwapModule module = new HitSwapModule(bridge);
        module.settings().stream().filter(s -> s.id().equals("maces")).findFirst().orElseThrow().setValue(1.0D);
        module.settings().stream().filter(s -> s.id().equals("require-breach")).findFirst().orElseThrow().setValue(1.0D);
        module.setEnabled(true);

        module.onAttackAttempt();

        assertEquals(-1, bridge.selectedSlot);
        module.setEnabled(false);
    }

    @Test
    void stunSlamWaitDoesNotDelayHotbarRestore() throws InterruptedException {
        FakeBridge bridge = new FakeBridge();
        bridge.selectedSlot = 0;
        bridge.onGround = true;
        bridge.crosshairTarget = Optional.of(TARGET);
        bridge.blockingWithShield.put("target", true);
        bridge.hotbarItems.put(ItemType.DIAMOND_AXE, 3);
        bridge.hotbarItems.put(ItemType.MACE, 5);
        HitSwapModule module = new HitSwapModule(bridge);
        set(module, "stun-slam", 1.0D);
        set(module, "restore-delay-ms-min", 50.0D);
        set(module, "restore-delay-ms-max", 50.0D);
        module.setEnabled(true);

        module.onAttackAttempt();
        assertEquals(3, bridge.selectedSlot);
        Thread.sleep(75L);
        module.onTick(TickEvent.now());

        assertEquals(0, bridge.selectedSlot);
        module.setEnabled(false);
    }

    private static final class FakeBridge implements MinecraftBridge {
        private Optional<AimTarget> crosshairTarget = Optional.empty();
        private boolean leftMouseDown;
        private boolean onGround;
        private int selectedSlot = -1;
        private CombatState combatState = CombatState.unavailable();
        private final Map<String, Boolean> blockingWithShield = new HashMap<>();
        private final Map<ItemType, Integer> hotbarItems = new HashMap<>();
        private final Map<Integer, Map<EnchantmentType, Integer>> enchantments = new HashMap<>();

        @Override
        public Optional<AimTarget> crosshairEntity(double range) {
            return crosshairTarget;
        }

        @Override
        public boolean isMouseButtonDown(int button) {
            return leftMouseDown;
        }

        @Override
        public boolean isOnGround() {
            return onGround;
        }

        @Override
        public CombatState combatState() {
            return combatState;
        }

        @Override
        public boolean isTargetBlockingWithShield(String targetId) {
            return blockingWithShield.getOrDefault(targetId, false);
        }

        @Override
        public int findHotbarItem(ItemType item) {
            return hotbarItems.getOrDefault(item, -1);
        }

        @Override
        public int hotbarEnchantmentLevel(int slot, EnchantmentType type) {
            return enchantments.getOrDefault(slot, Map.of()).getOrDefault(type, 0);
        }

        @Override
        public int selectedHotbarSlot() {
            return selectedSlot;
        }

        @Override
        public boolean selectHotbarSlot(int slot) {
            selectedSlot = slot;
            return true;
        }

        @Override
        public boolean attackTarget(String targetId) {
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

    private static void set(HitSwapModule module, String id, double value) {
        module.settings().stream()
                .filter(setting -> setting.id().equals(id))
                .findFirst().orElseThrow()
                .setValue(value);
    }

}
