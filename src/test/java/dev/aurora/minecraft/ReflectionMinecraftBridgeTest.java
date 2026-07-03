package dev.aurora.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionMinecraftBridgeTest {
    @Test
    void writesPrimitiveSelectedHotbarSlot() throws IllegalAccessException {
        IntermediaryInventory inventory = new IntermediaryInventory();

        assertTrue(ReflectionMinecraftBridge.selectHotbarSlotField(inventory, 6));
        assertEquals(6, inventory.field_7545);
    }

    @Test
    void invokesOfficialMappedEntityAttack() {
        OfficialInteractionManager manager = new OfficialInteractionManager();

        assertTrue(ReflectionMinecraftBridge.attackEntity(manager, new AttackPlayer(), new AttackTarget()));
        assertTrue(manager.attacked);
    }

    @Test
    void clearsNamedJumpCooldownField() throws Exception {
        NamedPlayer player = new NamedPlayer();

        assertTrue(ReflectionMinecraftBridge.clearJumpCooldownField(player));
        assertEquals(0, player.jumpingCooldown);
    }

    @Test
    void clearsFabricIntermediaryJumpCooldownFieldFromSuperclass() throws Exception {
        FabricPlayer player = new FabricPlayer();

        assertTrue(ReflectionMinecraftBridge.clearJumpCooldownField(player));
        assertEquals(0, ((FabricLivingEntity) player).field_6228);
    }

    @Test
    void clearsMojmapJumpCooldownField() throws Exception {
        MojmapPlayer player = new MojmapPlayer();

        assertTrue(ReflectionMinecraftBridge.clearJumpCooldownField(player));
        assertEquals(0, player.noJumpDelay);
    }

    @Test
    void clearsOfficialJumpCooldownField() throws Exception {
        OfficialPlayer player = new OfficialPlayer();

        assertTrue(ReflectionMinecraftBridge.clearJumpCooldownField(player));
        assertEquals(0, player.ce);
    }

    @Test
    void pressesFabricIntermediaryJumpKey() throws Exception {
        FabricOptions options = new FabricOptions();

        assertTrue(ReflectionMinecraftBridge.setJumpKeyHeld(options, true));
        assertTrue(options.field_1903.pressed);
        assertTrue(ReflectionMinecraftBridge.setJumpKeyHeld(options, false));
        assertEquals(false, options.field_1903.pressed);
    }

    @Test
    void pressesOfficialJumpKey() throws Exception {
        OfficialOptions options = new OfficialOptions();

        assertTrue(ReflectionMinecraftBridge.setJumpKeyHeld(options, true));
        assertTrue(options.z.pressed);
    }

    @Test
    void pressesNamedUseKey() throws Exception {
        NamedUseOptions options = new NamedUseOptions();

        assertTrue(ReflectionMinecraftBridge.setUseKeyHeld(options, true));
        assertTrue(options.useKey.pressed);
        assertTrue(ReflectionMinecraftBridge.setUseKeyHeld(options, false));
        assertEquals(false, options.useKey.pressed);
    }

    private static final class NamedPlayer {
        private int jumpingCooldown = 10;
    }

    private static final class IntermediaryInventory {
        private int field_7545;
    }

    private static final class AttackPlayer {
    }

    private static final class AttackTarget {
    }

    private static final class OfficialInteractionManager {
        private boolean attacked;

        @SuppressWarnings("unused")
        public void a(AttackPlayer player, AttackTarget target) {
            attacked = player != null && target != null;
        }
    }

    private static class FabricLivingEntity {
        private int field_6228 = 10;
    }

    private static final class FabricPlayer extends FabricLivingEntity {
    }

    private static final class MojmapPlayer {
        private int noJumpDelay = 10;
    }

    private static final class OfficialPlayer {
        private int ce = 10;
    }

    private static final class FabricOptions {
        private final FabricKey field_1903 = new FabricKey();
        @SuppressWarnings("unused")
        private final FabricKey field_1832 = new FabricKey();
    }

    private static final class FabricKey {
        private boolean pressed;

        @SuppressWarnings("unused")
        public void method_23481(boolean pressed) {
            this.pressed = pressed;
        }
    }

    private static final class OfficialOptions {
        private final OfficialKey z = new OfficialKey();
    }

    private static final class OfficialKey {
        private boolean pressed;

        @SuppressWarnings("unused")
        public void a(boolean pressed) {
            this.pressed = pressed;
        }
    }

    private static final class NamedUseOptions {
        private final NamedUseKey useKey = new NamedUseKey();
    }

    private static final class NamedUseKey {
        private boolean pressed;

        @SuppressWarnings("unused")
        public void setPressed(boolean pressed) {
            this.pressed = pressed;
        }
    }
}
