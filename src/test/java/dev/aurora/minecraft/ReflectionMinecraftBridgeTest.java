package dev.aurora.minecraft;

import dev.aurora.aim.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionMinecraftBridgeTest {

    @Test
    void resolvesAimPointAtEclipseChinHeight() {
        Vec3 point = ReflectionMinecraftBridge.chinAimPoint(2.0D, 10.0D, -3.0D,
                new PlayerSizedEntity());

        assertEquals(2.0D, point.x(), 0.0001D);
        assertEquals(11.296D, point.y(), 0.0001D);
        assertEquals(-3.0D, point.z(), 0.0001D);
    }

    @Test
    void drawsHudLineAsRotatedQuadsOnModernGuiMatrixStack() {
        ReflectionMinecraftBridge bridge = new ReflectionMinecraftBridge();
        FakeDrawContext context = new FakeDrawContext();

        assertTrue(bridge.drawHudLine(context, 20.0D, 30.0D, 32.0D, 39.0D, 0xFFFFFFFF));
        assertTrue(context.matrices.pushed);
        assertTrue(context.matrices.popped);
        assertEquals(20.0F, context.matrices.x, 0.0001F);
        assertEquals(30.0F, context.matrices.y, 0.0001F);
        assertEquals(2, context.fills);
    }

    @Test
    void readsScaledHudSizeFromMinecraft1214DrawContext() {
        ReflectionMinecraftBridge bridge = new ReflectionMinecraftBridge();

        HudSize size = bridge.hudSize(new SizedDrawContext());

        assertTrue(size.available());
        assertEquals(320, size.width());
        assertEquals(180, size.height());
    }

    @Test
    void reportsHudSizeUnavailableForUnknownContext() {
        HudSize size = new ReflectionMinecraftBridge().hudSize(new Object());

        assertEquals(HudSize.unavailable(), size);
    }

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

    @Test
    void restoresGammaThroughSimpleOptionSetter() throws Exception {
        IntermediaryGammaOption option = new IntermediaryGammaOption();

        assertTrue(ReflectionMinecraftBridge.restoreGammaOption(option, 0.73D));
        assertEquals(0.73D, option.field_37868);
        assertTrue(option.setterCalled);
    }

    @Test
    void overridesProtectedFabricIntermediaryCameraPosition() {
        ReflectionMinecraftBridge bridge = new ReflectionMinecraftBridge();
        FabricCamera camera = new FabricCamera();

        assertTrue(bridge.applyCameraPosition(camera, 10.0D, 64.5D, -3.0D));
        assertEquals(10.0D, camera.x, 1.0e-6);
        assertEquals(64.5D, camera.y, 1.0e-6);
        assertEquals(-3.0D, camera.z, 1.0e-6);
    }

    @Test
    void overridesProtectedFabricIntermediaryCameraRotation() {
        ReflectionMinecraftBridge bridge = new ReflectionMinecraftBridge();
        FabricCamera camera = new FabricCamera();

        assertTrue(bridge.applyCameraRotation(camera, 120.0F, -25.0F));
        assertEquals(120.0F, camera.yaw, 1.0e-4);
        assertEquals(-25.0F, camera.pitch, 1.0e-4);
    }

    @Test
    void cameraPositionIgnoresRelativeMoveByOverload() {
        // moveBy also takes three doubles; only setPos must be chosen so the camera lands absolutely.
        ReflectionMinecraftBridge bridge = new ReflectionMinecraftBridge();
        FabricCamera camera = new FabricCamera();

        bridge.applyCameraPosition(camera, 5.0D, 5.0D, 5.0D);
        assertEquals(0, camera.moveByCalls);
    }

    @Test
    void setsPreviousRotationFieldsForInterpolatedCameraAngle() {
        ReflectionMinecraftBridge bridge = new ReflectionMinecraftBridge();
        IntermediaryRotatable entity = new IntermediaryRotatable();

        assertTrue(bridge.applyCameraEntityRotation(entity, 42.0F, -8.0F));
        assertEquals(42.0F, entity.field_6031, 1.0e-4); // yaw
        assertEquals(-8.0F, entity.field_5965, 1.0e-4); // pitch
        assertEquals(42.0F, entity.field_5982, 1.0e-4); // prevYaw
        assertEquals(-8.0F, entity.field_6004, 1.0e-4); // prevPitch
    }

    @Test
    void forcesProtectedFabricIntermediaryDetachedFlag() {
        ReflectionMinecraftBridge bridge = new ReflectionMinecraftBridge();
        FabricCamera camera = new FabricCamera();

        assertTrue(bridge.setCameraDetached(camera, true));
        assertTrue(camera.field_18719);
    }

    /** Mirrors net.minecraft.client.render.Camera: the mutators are protected (invisible to
     * getMethods()) and named with Fabric intermediary ids. */
    private static class FabricCamera {
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        private boolean field_18719; // detached
        private int moveByCalls;

        protected void method_19327(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        protected void method_19325(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        protected void method_19328(double x, double y, double z) {
            moveByCalls++;
        }
    }

    private static final class IntermediaryRotatable {
        private float field_6031; // yaw
        private float field_5965; // pitch
        private float field_5982; // prevYaw
        private float field_6004; // prevPitch
    }

    private static final class NamedPlayer {
        private int jumpingCooldown = 10;
    }

    private static final class IntermediaryGammaOption {
        private Double field_37868 = 16.0D;
        private boolean setterCalled;

        @SuppressWarnings("unused")
        public void method_41748(Object value) {
            setterCalled = true;
            field_37868 = (Double) value;
        }
    }

    private static final class PlayerSizedEntity {
        @SuppressWarnings("unused")
        public double getHeight() {
            return 1.8D;
        }

        @SuppressWarnings("unused")
        public double getEyeHeight() {
            return 1.62D;
        }
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

    private static final class FakeDrawContext {
        private final FakeGuiMatrices matrices = new FakeGuiMatrices();
        private int fills;

        public FakeGuiMatrices getMatrices() {
            return matrices;
        }

        public void fill(int left, int top, int right, int bottom, int color) {
            fills++;
        }
    }

    private static final class SizedDrawContext {
        public int getScaledWindowWidth() {
            return 320;
        }

        public int getScaledWindowHeight() {
            return 180;
        }
    }

    private static final class FakeGuiMatrices {
        private boolean pushed;
        private boolean popped;
        private float x;
        private float y;

        public void pushMatrix() {
            pushed = true;
        }

        public void popMatrix() {
            popped = true;
        }

        public void translate(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public void rotate(float radians) {
        }
    }
}
