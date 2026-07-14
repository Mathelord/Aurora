package dev.aurora.minecraft;

import dev.aurora.aim.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public interface MinecraftBridge {
    boolean isSinglePlayer();

    /** Whether a local player and client world are currently loaded. */
    default boolean isInGame() {
        return false;
    }

    boolean applyReach(double range);

    boolean resetReach();

    default AimContext aimContext(double range, boolean ignoreWalls) {
        return AimContext.unavailable();
    }

    /** Names of every player currently known to the client (server tab list plus any players loaded
     * in the world), for friend-name autocomplete in the control panel. */
    default List<String> onlinePlayerNames() {
        return List.of();
    }

    /**
     * Nearby players that are friended, within {@code range} blocks. These are deliberately excluded
     * from {@link #aimContext} (so combat modules never act on a friend) but ESP still needs them to
     * paint friend boxes, so it queries them separately here. Implementations register the returned
     * targets for {@link #targetPose} lookups the same way {@link #aimContext} does.
     */
    default List<AimTarget> friendTargets(double range) {
        return List.of();
    }

    /** Nearby renderable entities for the Nametags HUD. */
    default List<NametagTarget> nametagTargets(double range) {
        return List.of();
    }

    default void captureWorldProjection(Object matrixStack) {
    }

    /**
     * Starts a 3D render frame. Implementations may use this boundary to share interpolated entity
     * poses between world-render modules without carrying those poses into the next frame.
     */
    default void beginWorldFrame() {
    }

    default ScreenPosition projectToScreen(Object renderContext, Vec3 worldPosition) {
        return ScreenPosition.invisible();
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

    /** Like {@link #applyEntityRotation}, but also sets the previous-tick rotation so the camera's
     * interpolated look angle (and its third-person offset direction) lands exactly on {@code
     * yaw}/{@code pitch}. Used by Free Look / Freecam while overriding the camera in every
     * perspective. */
    default boolean applyCameraEntityRotation(Object entity, float yaw, float pitch) {
        return applyEntityRotation(entity, yaw, pitch);
    }

    /** Overrides the render camera's world-space position, used by Freecam to detach the view from
     * the player. Returns whether the write succeeded. */
    default boolean applyCameraPosition(Object camera, double x, double y, double z) {
        return false;
    }

    /** Forces the camera's "detached" (third-person) flag so, while Freecam holds the view away from
     * the player in first-person mode, the game still renders the player body and hides the held-item
     * hand. Returns whether the write succeeded. */
    default boolean setCameraDetached(Object camera, boolean detached) {
        return false;
    }

    /** Ordinal of the current camera perspective (0 = first person, 1 = third-person back, 2 =
     * third-person front), or -1 if unavailable. Used by Free Look / Freecam to save and restore the
     * player's view mode. */
    default int cameraPerspective() {
        return -1;
    }

    /** Switches the camera perspective by ordinal (see {@link #cameraPerspective()}). */
    default boolean setCameraPerspective(int ordinal) {
        return false;
    }

    /** Zeroes a player movement input (forward/strafe/jump/sneak/sprint) so the body stays put while
     * Freecam flies the camera. Returns whether the input was actually cleared. */
    default boolean freezeMovementInput(Object input) {
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

    /** Whether vanilla block placement may replace the block currently occupying this position. */
    default boolean isBlockReplaceableAt(int x, int y, int z) {
        return blockType(x, y, z) == BlockType.AIR;
    }

    default boolean hasEntityCollision(Vec3 min, Vec3 max) {
        return false;
    }

    default Optional<AimTarget> nearestEndCrystal(Vec3 min, Vec3 max, Vec3 referencePoint) {
        return Optional.empty();
    }

    /** Returns whether another player within {@code range} is holding {@code item} in either hand. */
    default boolean isNearbyPlayerHolding(ItemType item, double range) {
        return false;
    }

    default int findHotbarItem(ItemType item) {
        return -1;
    }

    /** Total count of an item in the nine hotbar slots. */
    default int hotbarItemCount(ItemType item) {
        return findHotbarItem(item) >= 0 ? 1 : 0;
    }

    /** Respawn-anchor charge count at a block position, or -1 when the block is not an anchor. */
    default int respawnAnchorCharges(int x, int y, int z) {
        return -1;
    }

    /** First sword in the hotbar, or -1 when none is present. */
    default int findHotbarSword() {
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

    /** Draws a compact rounded HUD surface using the bridge's rectangle primitive. */
    default boolean fillRounded(Object renderContext, int left, int top, int right, int bottom,
                                int radius, int color) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return false;
        }
        int corner = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (corner == 0) {
            return fill(renderContext, left, top, right, bottom, color);
        }

        boolean rendered = fill(renderContext, left + corner, top, right - corner, bottom, color);
        rendered |= fill(renderContext, left + 1, top + 1, right - 1, bottom - 1, color);
        rendered |= fill(renderContext, left, top + corner, right, bottom - corner, color);
        return rendered;
    }

    /**
     * Draws a rounded glass surface backed by a real blur of the current game framebuffer.
     * Implementations return {@code false} when GPU effects are unavailable so callers can fall
     * back to {@link #fillRounded(Object, int, int, int, int, int, int)}.
     */
    default boolean drawFrostedPanel(Object renderContext, int left, int top, int right, int bottom,
                                     int radius, float blurRadius, int tintColor, int borderColor) {
        return false;
    }

    /** Marks the start of a HUD render pass so expensive effects can share one scene capture. */
    default void beginHudFrame(Object renderContext) {
    }

    /** Scaled dimensions used by HUD drawing, or {@link HudSize#unavailable()} when unknown. */
    default HudSize hudSize(Object renderContext) {
        return HudSize.unavailable();
    }

    default boolean drawHudLine(Object renderContext, double startX, double startY,
                                double endX, double endY, int color) {
        return false;
    }

    default boolean drawText(Object renderContext, String text, int x, int y, int color) {
        return false;
    }

    default boolean drawScaledText(Object renderContext, String text, int x, int y, double scale, int color) {
        return drawText(renderContext, text, x, y, color);
    }

    /**
     * Draws several colored runs under one scale transform. {@link TextRun#xOffset()} is expressed
     * in unscaled font pixels, relative to {@code x}. Implementations with matrix access should
     * override this method so a tag needs only one matrix push/translate/scale/pop sequence.
     */
    default boolean drawScaledTextBatch(Object renderContext, List<TextRun> runs,
                                        double x, double y, double scale) {
        if (runs == null || runs.isEmpty() || !Double.isFinite(scale) || scale <= 0.0D) {
            return false;
        }
        boolean rendered = true;
        for (TextRun run : runs) {
            if (run == null || run.text() == null || run.text().isEmpty()) {
                continue;
            }
            rendered &= drawScaledText(renderContext, run.text(),
                    (int) Math.round(x + run.xOffset() * scale), (int) Math.round(y),
                    scale, run.color());
        }
        return rendered;
    }

    /** One colored string within a scaled HUD text batch. */
    record TextRun(String text, int xOffset, int color) {
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

    /** Like {@link #crosshairEntity(double)}, but only returns player entities. */
    default Optional<AimTarget> crosshairPlayer(double range) {
        return crosshairEntity(range);
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
    default boolean setBackwardKeyHeld(boolean held) { return false; }
    default boolean setLeftKeyHeld(boolean held) { return false; }
    default boolean setRightKeyHeld(boolean held) { return false; }

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

    /** Current local-player eye position used for world-space aim calculations. */
    default Vec3 playerEyePosition() {
        return playerPosition().add(0.0D, 1.62D, 0.0D);
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

    /** Restores a valid vanilla gamma value through the option's normal setter when available. */
    default boolean restoreGamma(double value) {
        return setGamma(value);
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
