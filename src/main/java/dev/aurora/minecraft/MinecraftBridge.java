package dev.aurora.minecraft;

import dev.aurora.aim.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public interface MinecraftBridge {
    boolean isSinglePlayer();

    boolean applyReach(double range);

    boolean resetReach();

    default AimContext aimContext(double range, boolean ignoreWalls) {
        return AimContext.unavailable();
    }

    default boolean applyAimRotation(double yaw, double pitch) {
        return false;
    }

    default double mouseSensitivity() {
        return 0.5D;
    }

    boolean renderStatusText(Object renderContext, String text);

    default ClickGuiInput clickGuiInput() {
        return ClickGuiInput.unavailable();
    }

    default void setClickGuiInputCaptured(boolean captured) {
    }

    default void suppressClickGuiGameplayInput() {
    }

    default boolean isKeyDown(int keyCode) {
        return false;
    }

    default boolean hasOpenScreen() {
        return false;
    }

    default boolean isLocalPlayer(Object entity) {
        return false;
    }

    default boolean applyCameraRotation(Object camera, float yaw, float pitch) {
        return false;
    }

    default boolean applyEntityRotation(Object entity, float yaw, float pitch) {
        return false;
    }

    default boolean attackTarget(String targetId) {
        return false;
    }

    /**
     * Forces the game's crosshair target to the given entity (by {@link AimTarget#id()}), so a
     * subsequent {@link #doAttack()} / {@link #doItemUse()} acts on it even though the player is not
     * visually looking at it. This is what lets a silent/decoupled aura drive a real click at the
     * intended target. Returns {@code false} if the entity is unknown or could not be set.
     */
    default boolean setCrosshairTarget(String targetId) {
        return false;
    }

    /**
     * Runs the vanilla left-click attack handler ({@code doAttack}) against the current crosshair
     * target: the same code path a physical left click takes (swing + interaction), rather than a
     * manually issued attack. Returns whether the game reported a hit.
     */
    default boolean doAttack() {
        return false;
    }

    /** Runs the vanilla right-click use handler ({@code doItemUse}) against the current crosshair target. */
    default boolean doItemUse() {
        return false;
    }

    default Optional<BlockHitTarget> crosshairBlock() {
        return Optional.empty();
    }

    default BlockType blockType(int x, int y, int z) {
        return BlockType.OTHER;
    }

    default boolean hasEntityCollision(Vec3 min, Vec3 max) {
        return false;
    }

    default Optional<AimTarget> nearestEndCrystal(Vec3 min, Vec3 max, Vec3 referencePoint) {
        return Optional.empty();
    }

    default int findHotbarItem(ItemType item) {
        return -1;
    }

    /** Level of {@code type} on the hotbar stack in {@code slot} (0-8), or 0 if absent/unavailable. */
    default int hotbarEnchantmentLevel(int slot, EnchantmentType type) {
        return 0;
    }

    /** Whether the entity behind {@code targetId} (a value previously returned by {@link
     * #aimContext} or {@link #crosshairEntity}) is currently blocking with a shield. Used to decide
     * when a weapon swap should prefer an axe to break the block. */
    default boolean isTargetBlockingWithShield(String targetId) {
        return false;
    }

    /** Whether the target has a shield equipped in either hand, regardless of whether it is
     * currently raised. */
    default boolean isTargetHoldingShield(String targetId) {
        return isTargetBlockingWithShield(targetId);
    }

    default boolean hasHotbarItem(ItemType item) {
        return findHotbarItem(item) >= 0;
    }

    default int selectedHotbarSlot() {
        return -1;
    }

    default boolean selectHotbarSlot(int slot) {
        return false;
    }

    default boolean isHoldingItem(ItemType item) {
        int selected = selectedHotbarSlot();
        return selected >= 0 && selected == findHotbarItem(item);
    }

    default boolean useItemOnBlock(BlockHitTarget hit) {
        return false;
    }

    default double attackCooldown() {
        return 0.0D;
    }

    default boolean isMouseButtonDown(int button) {
        return false;
    }

    default boolean isUsingItem() {
        return false;
    }

    default boolean steerMovementInput(Object input, float visualYaw, float silentYaw) {
        return false;
    }

    default boolean swingMainHand() {
        return false;
    }

    default boolean stopUsingItem() {
        return false;
    }

    default boolean fill(Object renderContext, int left, int top, int right, int bottom, int color) {
        return false;
    }

    default boolean drawText(Object renderContext, String text, int x, int y, int color) {
        return false;
    }

    /** Width in pixels {@code text} would render at, using the game's current font. Needed to
     * center/truncate text in the click GUI; returns an estimate (0) if unavailable. */
    default int textWidth(String text) {
        return 0;
    }

    /**
     * Resolves whatever entity the player's real (unmodified) crosshair is currently on — unlike
     * {@link #aimContext}, this never reflects a silently-aimed target. Used by modules that should
     * only act on what the player is actually looking at (TriggerBot, BackTrack).
     */
    default Optional<AimTarget> crosshairEntity(double range) {
        return Optional.empty();
    }

    default boolean isOnGround() {
        return false;
    }

    default boolean isSprinting() {
        return false;
    }

    default boolean setSprinting(boolean sprinting) {
        return false;
    }

    default CombatState combatState() {
        return CombatState.unavailable();
    }

    default Vec3 playerVelocity() {
        return Vec3.ZERO;
    }

    default boolean setForwardKeyHeld(boolean held) {
        return false;
    }

    default boolean isForwardKeyPhysicallyDown() {
        return false;
    }

    /** Presses or releases the jump key as if it were physically held. */
    default boolean setJumpKeyHeld(boolean held) {
        return false;
    }

    /** Presses or releases the use key as if it were physically held. */
    default boolean setUseKeyHeld(boolean held) {
        return false;
    }

    /** The local player's network entity id (distinct from its UUID), or -1 if unavailable. Used to
     * match incoming entity-targeted packets (e.g. knockback) against the local player. */
    default int localEntityId() {
        return -1;
    }

    default Vec3 playerPosition() {
        return Vec3.ZERO;
    }

    /**
     * Resolves the current render-space pose (feet position and dimensions) of a target previously
     * reported by {@link #aimContext} or {@link #crosshairEntity}, matched by {@link AimTarget#id()}.
     * Returns empty if the entity is unknown or no longer tracked. Used to anchor world renderers
     * such as the target ring to a live target.
     */
    default Optional<TargetPose> targetPose(String targetId) {
        return Optional.empty();
    }

    default OptionalInt targetEntityId(String targetId) {
        return OptionalInt.empty();
    }

    /** The current look-from pose, for projecting world points to screen space. Uses the player's
     * real angles, or the decoupled "visual" angle while Silent Aim is active, so renderers always
     * line up with what's actually on screen. */
    default CameraPose cameraPose() {
        return CameraPose.unavailable();
    }

    /** Zeros the local player's jump cooldown, if the game tracks one. */
    default boolean clearJumpCooldown() {
        return false;
    }

    /** Current game brightness (gamma) option value, or empty if unavailable. Used by Fullbright. */
    default Optional<Double> gamma() {
        return Optional.empty();
    }

    /** Overrides the game brightness (gamma) option, bypassing its validated 0..1 range so a
     * Fullbright value can be applied. Returns whether the write succeeded. */
    default boolean setGamma(double value) {
        return false;
    }

    /** The client's remaining item-use (right-click) cooldown in ticks, or -1 if unavailable. */
    default int itemUseCooldown() {
        return -1;
    }

    /** Overwrites the client's item-use cooldown, used by FastPlace to shorten placement delay. */
    default boolean setItemUseCooldown(int ticks) {
        return false;
    }

    /** Whether the item held in the main hand places a block (a {@code BlockItem}). */
    default boolean isHoldingBlockItem() {
        return false;
    }

    /** Whether the local player is sneaking. */
    default boolean isSneaking() {
        return false;
    }

    /**
     * The hotbar slot (0-8) holding the fastest suitable tool for mining the block at the given
     * position, or -1 if no tool scores better than punching by hand. Used by AutoTool.
     */
    default int bestMiningToolSlot(int x, int y, int z) {
        return -1;
    }

    /** The translation key of the item held in the main hand (e.g. {@code "item.minecraft.bow"}),
     * or empty if the hand is empty or unavailable. Used by Trajectories to classify the projectile. */
    default Optional<String> heldItemId() {
        return Optional.empty();
    }

    /** Whether the block at the given position has a non-empty collision shape, i.e. it would stop a
     * projectile. Used by Trajectories to terminate a simulated arc. */
    default boolean isBlockSolidAt(int x, int y, int z) {
        return false;
    }

    /** Bounding boxes of every non-local, alive entity within {@code range} blocks of {@code center}.
     * Used by Trajectories to detect which entity a simulated arc would strike. */
    default List<EntityBox> nearbyEntityBoxes(Vec3 center, double range) {
        return List.of();
    }

    String environment();
}
