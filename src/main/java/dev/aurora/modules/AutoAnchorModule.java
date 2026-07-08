package dev.aurora.modules;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.AimMath;
import dev.aurora.aim.AimSmoothingProfile;
import dev.aurora.aim.SilentAimRequest;
import dev.aurora.aim.SilentAimSystem;
import dev.aurora.aim.SmoothingMode;
import dev.aurora.aim.Vec3;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.BlockFace;
import dev.aurora.minecraft.BlockHitTarget;
import dev.aurora.minecraft.BlockType;
import dev.aurora.minecraft.ItemType;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Eclipse-style anchor sequence: acquire a placement, aim, place, optionally cover, charge and detonate. */
public final class AutoAnchorModule extends AbstractModule {
    private static final String AIM_OWNER = "auto-anchor";
    private static final double REACH = 4.7D;
    private static final double AIM_THRESHOLD = 8.0D;
    private static final int AIM_TIMEOUT_TICKS = 12;

    private enum Phase { IDLE, AIM_ANCHOR, WAIT_ANCHOR, AIM_COVER, WAIT_COVER,
        AIM_CHARGE, WAIT_CHARGE, AIM_DETONATE, WAIT_DETONATION }

    private final MinecraftBridge minecraft;
    private final ModuleSetting mustBeBound;
    private final ModuleSetting safeAnchor;
    private final ModuleSetting decoupledAim;
    private final ModuleSetting aimSpeed;
    private final ModuleSetting autoRange;
    private final ModuleSetting autoCooldown;
    private final ModuleSetting restoreSlot;

    private Phase phase = Phase.IDLE;
    private BlockHitTarget anchorPlacementHit;
    private BlockHitTarget coverPlacementHit;
    private BlockPos anchorPos;
    private BlockPos coverPos;
    private Vec3 aimTarget;
    private int aimTicks;
    private int waitTicks;
    private int cooldownTicks;
    private int previousSlot = -1;

    public AutoAnchorModule(MinecraftBridge minecraft) {
        super("auto-anchor", "Auto Anchor", "Combat",
                "Automatically aims, places, charges, and detonates respawn anchors near players.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        mustBeBound = booleanSetting("must-be-bound", "Must Be Bound", false)
                .description("Runs one crosshair-selected combo and disables. Off continuously targets nearby players.");
        safeAnchor = booleanSetting("safe-anchor", "Safe Anchor", true)
                .description("Places glowstone cover between you and the anchor before charging when possible.");
        decoupledAim = booleanSetting("decoupled-aim", "Decoupled Aim", true)
                .description("Rotates the server-facing player while keeping the visible camera independent.");
        aimSpeed = setting("aim-speed", "Aim Speed", 1.0D, 0.1D, 5.0D, 0.05D);
        autoRange = setting("auto-range", "Auto Range", 6.0D, 1.0D, 12.0D, 0.1D);
        autoCooldown = setting("auto-cooldown-ticks", "Auto Cooldown", 8.0D, 0.0D, 60.0D, 1.0D);
        restoreSlot = booleanSetting("restore-slot", "Restore Slot", true);
    }

    @Override public void onEnable() {
        resetSequence();
        previousSlot = minecraft.selectedHotbarSlot();
        if (on(mustBeBound)) startBoundSequence();
    }

    @Override public void onDisable() {
        SilentAimSystem.get().clearOwner(AIM_OWNER);
        restorePreviousSlot();
        resetSequence();
    }

    @Override public void onRender(RenderEvent event) {
        if (aimTarget == null || phase == Phase.IDLE) {
            SilentAimSystem.get().clearOwner(AIM_OWNER);
            return;
        }
        AimContext context = minecraft.aimContext(REACH, true);
        if (!context.available()) return;
        AimAngles targetAngles = anglesTo(minecraft.playerEyePosition(), aimTarget);
        double configuredSpeed = aimSpeed.value();
        SilentAimSystem.get().apply(new SilentAimSystem.AimRuntime(true,
                        new AimAngles((float) context.yaw(), (float) context.pitch()), context.mouseSensitivity(),
                        angles -> minecraft.applyAimRotation(angles.yaw(), angles.pitch())),
                SilentAimRequest.builder(AIM_OWNER, targetAngles).priority(115).targetPoint(aimTarget)
                        .smoothingProfile(new AimSmoothingProfile(SmoothingMode.Humanized, Math.min(configuredSpeed, 1.0D),
                                180.0D, 90.0D, 0.25D, 0.02D))
                        .targetResponse(32.0D * Math.max(1.0D, configuredSpeed))
                        .rotationMultiplier(2.4D * configuredSpeed).shortestYawPath(true)
                        .decoupled(on(decoupledAim)).globalDecoupledAllowed(false).build());
    }

    @Override public void onTick(TickEvent event) {
        if (!minecraft.isInGame() || minecraft.hasOpenScreen()) return;
        if (phase == Phase.IDLE) {
            if (on(mustBeBound)) return;
            if (cooldownTicks > 0) cooldownTicks--;
            else startAutomaticSequence();
            return;
        }
        switch (phase) {
            case AIM_ANCHOR -> aimAndUse(ItemType.RESPAWN_ANCHOR, anchorPlacementHit, Phase.WAIT_ANCHOR);
            case WAIT_ANCHOR -> waitAnchor();
            case AIM_COVER -> aimAndUse(ItemType.GLOWSTONE, coverPlacementHit, Phase.WAIT_COVER);
            case WAIT_COVER -> waitCover();
            case AIM_CHARGE -> aimAndUse(ItemType.GLOWSTONE, anchorHit(), Phase.WAIT_CHARGE);
            case WAIT_CHARGE -> waitCharge();
            case AIM_DETONATE -> aimAndDetonate();
            case WAIT_DETONATION -> waitDetonation();
            default -> { }
        }
    }

    private void startBoundSequence() {
        if (!hasItems()) { finishAttempt(); return; }
        Optional<BlockHitTarget> hit = minecraft.crosshairBlock();
        if (hit.isEmpty()) { finishAttempt(); return; }
        BlockHitTarget support = hit.get();
        BlockPos pos = offset(support, support.face());
        if (!canPlace(pos)) { finishAttempt(); return; }
        begin(pos, support);
    }

    private void startAutomaticSequence() {
        if (!hasItems()) { cooldownTicks = cooldown(); return; }
        AimContext context = minecraft.aimContext(autoRange.value(), false);
        AimTarget target = !context.available() ? null : context.targets().stream()
                .min(Comparator.comparingDouble(AimTarget::distanceSquared)).orElse(null);
        Candidate candidate = target == null ? null : findCandidate(target);
        if (candidate == null) { cooldownTicks = Math.max(1, cooldown()); return; }
        begin(candidate.pos(), candidate.hit());
    }

    private Candidate findCandidate(AimTarget target) {
        Vec3 point = target.targetPoint();
        int cx = floor(point.x());
        int cy = floor(point.y() - 1.0D);
        int cz = floor(point.z());
        Candidate best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 player = minecraft.playerPosition();
        for (int y = cy - 1; y <= cy + 1; y++) for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos support = new BlockPos(x, y - 1, z);
            if (!canPlace(pos) || !minecraft.isBlockSolidAt(support.x(), support.y(), support.z())) continue;
            Vec3 top = placementAimPoint(x, y, z, point);
            if (distance(minecraft.playerEyePosition(), top) > REACH) continue;
            double targetDistance = Math.hypot(x + 0.5D - point.x(), z + 0.5D - point.z());
            double selfDistance = Math.hypot(x + 0.5D - player.x(), z + 0.5D - player.z());
            double score = Math.abs(targetDistance - 1.1D) * 12.0D
                    + Math.max(0.0D, 2.0D - selfDistance) * 18.0D
                    + playerSidePlacementPenalty(player, point, x + 0.5D, z + 0.5D)
                    + Math.abs(y + 0.5D - point.y()) * 3.0D;
            if (score < bestScore) {
                bestScore = score;
                best = new Candidate(pos, new BlockHitTarget(x, y - 1, z, BlockFace.UP, top));
            }
        }
        return best;
    }

    private void begin(BlockPos pos, BlockHitTarget hit) {
        anchorPos = pos;
        anchorPlacementHit = hit;
        phase = Phase.AIM_ANCHOR;
        aimTarget = hit.hitPoint();
        aimTicks = waitTicks = 0;
    }

    private void aimAndUse(ItemType item, BlockHitTarget hit, Phase waiting) {
        if (hit == null || !select(item)) { finishAttempt(); return; }
        boolean placement = waiting == Phase.WAIT_ANCHOR || waiting == Phase.WAIT_COVER;
        if (placement
                && (!solidSupport(hit) || !canPlace(offset(hit, hit.face())))) {
            finishAttempt();
            return;
        }
        aimTarget = hit.hitPoint();
        if (!isAimed()) { if (++aimTicks > AIM_TIMEOUT_TICKS) finishAttempt(); return; }
        if (!realCrosshairMatches(hit, placement)) {
            if (++aimTicks > AIM_TIMEOUT_TICKS) finishAttempt();
            return;
        }
        if (!minecraft.doItemUse()) { finishAttempt(); return; }
        phase = waiting;
        aimTicks = waitTicks = 0;
    }

    private void waitAnchor() {
        if (block(anchorPos) == BlockType.RESPAWN_ANCHOR) { beginCoverOrCharge(); return; }
        confirmPlacement(ItemType.RESPAWN_ANCHOR, anchorPlacementHit);
        if (++waitTicks > 8) finishAttempt();
    }

    private void beginCoverOrCharge() {
        Candidate cover = findCover();
        if (cover != null) {
            coverPos = cover.pos(); coverPlacementHit = cover.hit(); phase = Phase.AIM_COVER; aimTarget = cover.hit().hitPoint();
        } else {
            phase = Phase.AIM_CHARGE; aimTarget = anchorHit().hitPoint();
        }
        aimTicks = waitTicks = 0;
    }

    private Candidate findCover() {
        if (!on(safeAnchor) || minecraft.hotbarItemCount(ItemType.GLOWSTONE) < 2) return null;
        Vec3 player = minecraft.playerPosition();
        return List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST).stream()
                .sorted(Comparator.comparingDouble(face -> -face.offsetX() * (player.x() - anchorPos.x() - 0.5D)
                        - face.offsetZ() * (player.z() - anchorPos.z() - 0.5D)))
                .map(face -> {
                    BlockPos pos = new BlockPos(anchorPos.x() + face.offsetX(), anchorPos.y(), anchorPos.z() + face.offsetZ());
                    BlockPos support = new BlockPos(pos.x(), pos.y() - 1, pos.z());
                    Vec3 hit = new Vec3(pos.x() + 0.5D, pos.y(), pos.z() + 0.5D);
                    return canPlace(pos) && minecraft.isBlockSolidAt(support.x(), support.y(), support.z())
                            && distance(minecraft.playerEyePosition(), hit) <= REACH
                            ? new Candidate(pos, new BlockHitTarget(support.x(), support.y(), support.z(), BlockFace.UP, hit)) : null;
                }).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private void waitCover() {
        if (block(coverPos) == BlockType.GLOWSTONE || ++waitTicks > 8) {
            phase = Phase.AIM_CHARGE; aimTarget = anchorHit().hitPoint(); aimTicks = waitTicks = 0; return;
        }
        confirmPlacement(ItemType.GLOWSTONE, coverPlacementHit);
    }

    private void waitCharge() {
        if (minecraft.respawnAnchorCharges(anchorPos.x(), anchorPos.y(), anchorPos.z()) > 0) {
            phase = Phase.AIM_DETONATE; aimTarget = anchorHit().hitPoint(); aimTicks = waitTicks = 0; return;
        }
        confirmBlockUse(ItemType.GLOWSTONE, anchorHit());
        if (++waitTicks > 8) finishAttempt();
    }

    private void aimAndDetonate() {
        int slot = detonatorSlot();
        if (slot < 0 || !minecraft.selectHotbarSlot(slot)) { finishAttempt(); return; }
        BlockHitTarget hit = anchorHit(); aimTarget = hit.hitPoint();
        if (!isAimed()) { if (++aimTicks > AIM_TIMEOUT_TICKS) finishAttempt(); return; }
        if (!realCrosshairMatches(hit, false)) {
            if (++aimTicks > AIM_TIMEOUT_TICKS) finishAttempt();
            return;
        }
        if (!minecraft.doItemUse()) { finishAttempt(); return; }
        phase = Phase.WAIT_DETONATION;
        waitTicks = aimTicks = 0;
    }

    private void waitDetonation() {
        if (block(anchorPos) != BlockType.RESPAWN_ANCHOR) { finishAttempt(); return; }
        if (waitTicks < 12 && isAimed()) {
            int slot = detonatorSlot();
            BlockHitTarget hit = anchorHit();
            if (slot >= 0 && minecraft.selectHotbarSlot(slot) && realCrosshairMatches(hit, false)) {
                minecraft.doItemUse();
            }
        }
        if (++waitTicks > 12) finishAttempt();
    }

    private void confirmPlacement(ItemType item, BlockHitTarget hit) {
        if (waitTicks < 12 && isAimed() && solidSupport(hit)
                && canPlace(offset(hit, hit.face())) && select(item) && realCrosshairMatches(hit, true)) {
            minecraft.doItemUse();
        }
    }

    private void confirmBlockUse(ItemType item, BlockHitTarget hit) {
        if (waitTicks < 12 && isAimed() && select(item) && realCrosshairMatches(hit, false)) {
            minecraft.doItemUse();
        }
    }

    private boolean hasItems() {
        int anchors = 1;
        int glowstone = 1;
        if (on(safeAnchor)) glowstone++;
        return minecraft.hotbarItemCount(ItemType.RESPAWN_ANCHOR) >= anchors
                && minecraft.hotbarItemCount(ItemType.GLOWSTONE) >= glowstone && detonatorSlot() >= 0;
    }

    private int detonatorSlot() {
        int totem = minecraft.findHotbarItem(ItemType.TOTEM_OF_UNDYING);
        return totem >= 0 ? totem : minecraft.findHotbarSword();
    }

    private boolean select(ItemType item) {
        int slot = minecraft.findHotbarItem(item);
        return slot >= 0 && minecraft.selectHotbarSlot(slot);
    }

    private boolean canPlace(BlockPos pos) {
        return block(pos) == BlockType.AIR && !minecraft.hasEntityCollision(
                new Vec3(pos.x(), pos.y(), pos.z()), new Vec3(pos.x() + 1, pos.y() + 1, pos.z() + 1));
    }

    private boolean solidSupport(BlockHitTarget hit) {
        return hit != null && minecraft.isBlockSolidAt(hit.blockX(), hit.blockY(), hit.blockZ());
    }

    private boolean realCrosshairMatches(BlockHitTarget expected, boolean requireFace) {
        Optional<BlockHitTarget> actual = minecraft.crosshairBlock();
        if (expected == null || actual.isEmpty()) return false;
        BlockHitTarget hit = actual.get();
        return hit.blockX() == expected.blockX()
                && hit.blockY() == expected.blockY()
                && hit.blockZ() == expected.blockZ()
                && (!requireFace || hit.face() == expected.face());
    }

    private boolean isAimed() {
        AimContext context = minecraft.aimContext(REACH, true);
        if (!context.available() || aimTarget == null) return false;
        AimAngles wanted = anglesTo(minecraft.playerEyePosition(), aimTarget);
        return Math.abs(AimMath.wrapDegrees(context.yaw() - wanted.yaw())) <= AIM_THRESHOLD
                && Math.abs(context.pitch() - wanted.pitch()) <= AIM_THRESHOLD;
    }

    private void finishAttempt() {
        SilentAimSystem.get().clearOwner(AIM_OWNER);
        restorePreviousSlot();
        resetSequence();
        if (on(mustBeBound)) { if (enabled()) setEnabled(false); }
        else cooldownTicks = Math.max(1, cooldown());
    }

    private void restorePreviousSlot() {
        if (on(restoreSlot) && previousSlot >= 0) minecraft.selectHotbarSlot(previousSlot);
    }

    private void resetSequence() {
        phase = Phase.IDLE; anchorPlacementHit = coverPlacementHit = null; anchorPos = coverPos = null;
        aimTarget = null; aimTicks = waitTicks = cooldownTicks = 0;
    }

    private BlockHitTarget anchorHit() {
        return new BlockHitTarget(anchorPos.x(), anchorPos.y(), anchorPos.z(), BlockFace.UP,
                new Vec3(anchorPos.x() + 0.5D, anchorPos.y() + 0.92D, anchorPos.z() + 0.5D));
    }

    private BlockType block(BlockPos pos) { return pos == null ? BlockType.OTHER : minecraft.blockType(pos.x(), pos.y(), pos.z()); }
    private int cooldown() { return (int) Math.round(autoCooldown.value()); }
    private static boolean on(ModuleSetting setting) { return setting.value() >= 0.5D; }
    private static double distance(Vec3 first, Vec3 second) { return Math.sqrt(first.squaredDistanceTo(second)); }
    private static Vec3 placementAimPoint(int x, int y, int z, Vec3 target) {
        double centerX = x + 0.5D;
        double centerZ = z + 0.5D;
        double awayX = centerX - target.x();
        double awayZ = centerZ - target.z();
        double length = Math.hypot(awayX, awayZ);
        if (length <= 1.0e-6D) return new Vec3(centerX, y, centerZ);
        double offset = 0.18D;
        return new Vec3(centerX + awayX / length * offset, y, centerZ + awayZ / length * offset);
    }
    static double playerSidePlacementPenalty(Vec3 player, Vec3 target, double candidateX, double candidateZ) {
        double playerToTargetX = target.x() - player.x();
        double playerToTargetZ = target.z() - player.z();
        double targetToCandidateX = candidateX - target.x();
        double targetToCandidateZ = candidateZ - target.z();
        double approachLength = Math.hypot(playerToTargetX, playerToTargetZ);
        double candidateLength = Math.hypot(targetToCandidateX, targetToCandidateZ);
        if (approachLength <= 1.0e-6D || candidateLength <= 1.0e-6D) return 35.0D;
        double alignment = (playerToTargetX * targetToCandidateX + playerToTargetZ * targetToCandidateZ)
                / (approachLength * candidateLength);
        // Prefer the player-facing diagonal: beside the opponent and slightly toward the local
        // player. This keeps the support face visible without selecting a position behind them.
        double clampedAlignment = Math.max(-1.0D, Math.min(1.0D, alignment));
        return Math.abs(clampedAlignment + 0.35D) * 35.0D
                + Math.max(0.0D, clampedAlignment - 0.15D) * 45.0D;
    }
    private static int floor(double value) { return (int) Math.floor(value); }
    private static BlockPos offset(BlockHitTarget hit, BlockFace face) {
        return new BlockPos(hit.blockX() + face.offsetX(), hit.blockY() + face.offsetY(), hit.blockZ() + face.offsetZ());
    }
    private static AimAngles anglesTo(Vec3 eye, Vec3 point) {
        return AimMath.anglesTo(new Vec3(eye.x(), eye.y(), eye.z()), 0.0D, point);
    }
    private record BlockPos(int x, int y, int z) { }
    private record Candidate(BlockPos pos, BlockHitTarget hit) { }
}
