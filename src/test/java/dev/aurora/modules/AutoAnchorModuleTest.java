package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.events.TickEvent;
import dev.aurora.api.ModuleSetting;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.BlockFace;
import dev.aurora.minecraft.BlockHitTarget;
import dev.aurora.minecraft.BlockType;
import dev.aurora.minecraft.ItemType;
import dev.aurora.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoAnchorModuleTest {
    @Test
    void automaticRangeIsFixedAndCooldownIsHiddenWhenBound() {
        FakeBridge bridge = new FakeBridge();
        AutoAnchorModule module = new AutoAnchorModule(bridge);

        assertTrue(module.settings().stream().noneMatch(setting -> setting.id().equals("auto-range")));
        ModuleSetting cooldown = setting(module, "auto-cooldown-ticks");
        assertTrue(cooldown.visible());

        set(module, "must-be-bound", 1.0D);
        assertFalse(cooldown.visible());
        set(module, "must-be-bound", 0.0D);
        set(module, "safe-anchor", 0.0D);
        module.setEnabled(true);
        module.onTick(TickEvent.now());
        assertEquals(12.0D, bridge.lastAimRange);
    }

    @Test
    void boundModeOnlyRepeatsAfterKeyIsHeldForHalfASecond() {
        FakeBridge bridge = new FakeBridge();
        AtomicLong time = new AtomicLong();
        AutoAnchorModule module = new AutoAnchorModule(bridge, time::get);
        set(module, "must-be-bound", 1.0D);
        module.setKeybind(71);
        bridge.heldKey = 71;
        bridge.counts.put(ItemType.RESPAWN_ANCHOR, 0);

        module.setEnabled(true);
        assertTrue(module.enabled());
        bridge.heldKey = -1;
        module.onTick(TickEvent.now());
        assertFalse(module.enabled());

        bridge.heldKey = 71;
        bridge.counts.put(ItemType.RESPAWN_ANCHOR, 1);
        module.setEnabled(true);
        time.set(500_000_000L);
        bridge.counts.put(ItemType.RESPAWN_ANCHOR, 0);
        module.onTick(TickEvent.now());

        assertFalse(module.holdToActivate());
        assertTrue(module.enabled());
    }

    @Test
    void playerFacingDiagonalIsPreferredOverPlacementBehindTarget() {
        Vec3 player = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 target = new Vec3(2.0D, 64.0D, 0.0D);
        double playerFacingDiagonal = AutoAnchorModule.playerSidePlacementPenalty(player, target, 1.62D, 1.03D);
        double straightBehind = AutoAnchorModule.playerSidePlacementPenalty(player, target, 3.1D, 0.0D);
        double side = AutoAnchorModule.playerSidePlacementPenalty(player, target, 2.0D, 1.1D);

        assertTrue(playerFacingDiagonal < straightBehind);
        assertTrue(playerFacingDiagonal < side);
    }

    @Test
    void diagonalCoverIsPreferredWhenPlayerIsDiagonalFromAnchor() {
        double diagonal = AutoAnchorModule.coverDirectionScore(3.0D, 3.0D, 1, 1);
        double east = AutoAnchorModule.coverDirectionScore(3.0D, 3.0D, 1, 0);
        double south = AutoAnchorModule.coverDirectionScore(3.0D, 3.0D, 0, 1);

        assertTrue(diagonal < east);
        assertTrue(diagonal < south);
    }

    @Test
    void refusesPlacementWithoutSolidSupport() {
        FakeBridge bridge = new FakeBridge();
        bridge.solidSupport = false;
        AutoAnchorModule module = new AutoAnchorModule(bridge);
        set(module, "must-be-bound", 1.0D);

        module.setEnabled(true);
        module.onTick(TickEvent.now());

        assertFalse(module.enabled());
        assertEquals(0, bridge.useCount);
    }

    @Test
    void refusesPlacementWhenEntityObstructsRealCrosshair() {
        FakeBridge bridge = new FakeBridge();
        AutoAnchorModule module = new AutoAnchorModule(bridge);
        set(module, "must-be-bound", 1.0D);
        set(module, "safe-anchor", 0.0D);

        module.setEnabled(true);
        bridge.crosshairBlocked = true;
        for (int tick = 0; tick < 14; tick++) module.onTick(TickEvent.now());

        assertFalse(module.enabled());
        assertEquals(0, bridge.useCount);
    }

    @Test
    void abandonsUnconfirmedAnchorAfterSixTicks() {
        FakeBridge bridge = new FakeBridge();
        AutoAnchorModule module = new AutoAnchorModule(bridge);
        set(module, "must-be-bound", 1.0D);
        set(module, "safe-anchor", 0.0D);

        module.setEnabled(true);
        module.onTick(TickEvent.now());
        for (int tick = 0; tick < 5; tick++) module.onTick(TickEvent.now());
        assertTrue(module.enabled());

        module.onTick(TickEvent.now());
        assertFalse(module.enabled());
    }

    @Test
    void completesConfirmedPlaceChargeDetonateSequenceAndRestoresSlot() {
        FakeBridge bridge = new FakeBridge();
        AutoAnchorModule module = new AutoAnchorModule(bridge);
        set(module, "must-be-bound", 1.0D);
        set(module, "safe-anchor", 0.0D);

        module.setEnabled(true);
        module.onTick(TickEvent.now()); // place
        bridge.anchorPresent = true;
        module.onTick(TickEvent.now()); // confirm placement
        module.onTick(TickEvent.now()); // charge
        bridge.charges = 1;
        module.onTick(TickEvent.now()); // confirm charge
        module.onTick(TickEvent.now()); // detonate
        bridge.anchorPresent = false;
        module.onTick(TickEvent.now()); // confirm detonation

        assertFalse(module.enabled());
        assertEquals(3, bridge.useCount);
        assertEquals(4, bridge.selectedSlot);
    }

    @Test
    void placesAnchorIntoReplaceableBlockHitByCrosshair() {
        FakeBridge bridge = new FakeBridge();
        bridge.replaceableBlock = true;
        AutoAnchorModule module = new AutoAnchorModule(bridge);
        set(module, "must-be-bound", 1.0D);
        set(module, "safe-anchor", 0.0D);

        module.setEnabled(true);
        module.onTick(TickEvent.now());

        assertTrue(module.enabled());
        assertEquals(1, bridge.useCount);
    }

    private static void set(AutoAnchorModule module, String id, double value) {
        setting(module, id).setValue(value);
    }

    private static ModuleSetting setting(AutoAnchorModule module, String id) {
        return module.settings().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
    }

    private static final class FakeBridge implements MinecraftBridge {
        private final EnumMap<ItemType, Integer> slots = new EnumMap<>(ItemType.class);
        private final EnumMap<ItemType, Integer> counts = new EnumMap<>(ItemType.class);
        private boolean anchorPresent;
        private int charges;
        private int selectedSlot = 4;
        private int useCount;
        private boolean solidSupport = true;
        private boolean crosshairBlocked;
        private boolean replaceableBlock;
        private double lastAimRange;
        private int heldKey = -1;

        private FakeBridge() {
            slots.put(ItemType.RESPAWN_ANCHOR, 0);
            slots.put(ItemType.GLOWSTONE, 1);
            slots.put(ItemType.TOTEM_OF_UNDYING, 2);
            counts.put(ItemType.RESPAWN_ANCHOR, 1);
            counts.put(ItemType.GLOWSTONE, 1);
            counts.put(ItemType.TOTEM_OF_UNDYING, 1);
        }

        @Override public boolean isInGame() { return true; }
        @Override public boolean isKeyDown(int keyCode) { return keyCode == heldKey; }
        @Override public Vec3 playerPosition() { return new Vec3(10.5D, 62.38D, 9.5D); }
        @Override public Vec3 playerEyePosition() { return new Vec3(10.5D, 64.0D, 9.5D); }
        @Override public AimContext aimContext(double range, boolean ignoreWalls) {
            lastAimRange = range;
            double pitch = anchorPresent ? -Math.toDegrees(Math.atan2(0.92D, 1.0D)) : 0.0D;
            return new AimContext(true, false, 0.0D, pitch, 0.5D, java.util.List.of());
        }
        @Override public Optional<BlockHitTarget> crosshairBlock() {
            if (crosshairBlocked) return Optional.empty();
            if (anchorPresent) {
                return Optional.of(new BlockHitTarget(10, 64, 10, BlockFace.UP, new Vec3(10.5, 64.92, 10.5)));
            }
            if (replaceableBlock) {
                return Optional.of(new BlockHitTarget(10, 64, 10, BlockFace.UP, new Vec3(10.5, 64.125, 10.5)));
            }
            return Optional.of(new BlockHitTarget(10, 63, 10, BlockFace.UP, new Vec3(10.5, 64, 10.5)));
        }
        @Override public BlockType blockType(int x, int y, int z) {
            if (anchorPresent && y == 64) return BlockType.RESPAWN_ANCHOR;
            if (replaceableBlock && y == 64) return BlockType.OTHER;
            return BlockType.AIR;
        }
        @Override public boolean isBlockReplaceableAt(int x, int y, int z) {
            return y == 64 && (replaceableBlock || !anchorPresent);
        }
        @Override public boolean isBlockSolidAt(int x, int y, int z) { return solidSupport && y == 63; }
        @Override public int findHotbarItem(ItemType item) { return slots.getOrDefault(item, -1); }
        @Override public int hotbarItemCount(ItemType item) { return counts.getOrDefault(item, 0); }
        @Override public int respawnAnchorCharges(int x, int y, int z) { return anchorPresent ? charges : -1; }
        @Override public int selectedHotbarSlot() { return selectedSlot; }
        @Override public boolean selectHotbarSlot(int slot) { selectedSlot = slot; return true; }
        @Override public boolean doItemUse() { useCount++; return true; }
        @Override public boolean isSinglePlayer() { return false; }
        @Override public boolean applyReach(double range) { return false; }
        @Override public boolean resetReach() { return false; }
        @Override public boolean renderStatusText(Object context, String text) { return false; }
        @Override public String environment() { return "test"; }
    }
}
