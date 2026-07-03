package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.CombatState;
import dev.aurora.minecraft.EnchantmentType;
import dev.aurora.minecraft.ItemType;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Swaps into an axe or mace on attack, mirroring the target's held/blocking state: axes break an
 * enemy's raised shield, maces (optionally gated to falling, for a guaranteed smash) follow up.
 * With Stun Slam on, a shield-breaking axe hit queues an automatic mace follow-up the moment the
 * target's stun window opens.
 *
 * <p>Purely reactive to the physical left mouse button plus whatever the game's real crosshair is
 * on — like the original Eclipse module, it never attacks by itself, it only swaps the hotbar slot
 * out from under a click that's about to happen (real, TriggerBot, or Silent Aura) and swaps back
 * shortly after.
 */
public final class HitSwapModule extends AbstractModule {
    private static final ItemType[] AXE_TIERS = {
            ItemType.NETHERITE_AXE, ItemType.DIAMOND_AXE, ItemType.IRON_AXE,
            ItemType.STONE_AXE, ItemType.GOLDEN_AXE, ItemType.WOODEN_AXE
    };
    private static final long STUN_SLAM_WINDOW_MS = 1600L;
    private static final long STUN_SLAM_FOLLOW_UP_DELAY_MS = 50L;
    private static final long NO_WEAPON_RETRY_MS = 250L;
    private static final long SWAP_FAILED_RETRY_MS = 180L;

    private final MinecraftBridge minecraft;
    private final ModuleSetting delayMsMin;
    private final ModuleSetting delayMsMax;
    private final ModuleSetting restoreDelayMsMin;
    private final ModuleSetting restoreDelayMsMax;
    private final ModuleSetting chance;
    private final ModuleSetting axes;
    private final ModuleSetting maces;
    private final ModuleSetting smashOnly;
    private final ModuleSetting groundCheck;
    private final ModuleSetting requireBreach;
    private final ModuleSetting requireDensity;
    private final ModuleSetting stunSlam;

    private long nextHitAt;
    private long restoreAt;
    private int restoreSlot = -1;
    private String stunSlamTargetId;
    private long stunSlamReadyAt;
    private long stunSlamUntil;

    public HitSwapModule(MinecraftBridge minecraft) {
        super("hit-swap", "Hit Swap", "Combat",
                "Swaps into another weapon on attack, breaking shields with axes and following up with maces.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        delayMsMin = setting("delay-ms-min", "Delay Min", 220.0D, 0.0D, 2000.0D, 5.0D)
                .description("Minimum delay between weapon swaps, in milliseconds.");
        delayMsMax = setting("delay-ms-max", "Delay Max", 280.0D, 0.0D, 2000.0D, 5.0D)
                .description("Maximum delay between weapon swaps, in milliseconds.");
        restoreDelayMsMin = setting("restore-delay-ms-min", "Restore Delay Min", 150.0D, 50.0D, 800.0D, 5.0D)
                .description("Minimum delay before switching back to the previous hotbar slot after a swap.");
        restoreDelayMsMax = setting("restore-delay-ms-max", "Restore Delay Max", 210.0D, 50.0D, 800.0D, 5.0D)
                .description("Maximum delay before switching back to the previous hotbar slot after a swap.");
        chance = setting("chance", "Chance %", 100.0D, 0.0D, 100.0D, 1.0D)
                .description("Probability that Hit Swap fires on any given hit.");
        axes = booleanSetting("axes", "Axes", true)
                .description("Swaps to an axe when the target is blocking with a shield.");
        maces = booleanSetting("maces", "Maces", false)
                .description("Swaps to a mace on attack. Useful for breach swapping.");
        smashOnly = booleanSetting("smash-only", "Smash Only", false)
                .description("Only swaps to a mace while falling, ensuring a smash attack.");
        groundCheck = booleanSetting("ground-check", "Ground Check", true)
                .description("Prevents mace swaps while you are on the ground.");
        requireBreach = booleanSetting("require-breach", "Require Breach", false)
                .description("Only swaps to maces enchanted with Breach.");
        requireDensity = booleanSetting("require-density", "Require Density", false)
                .description("Only swaps to maces enchanted with Density.");
        stunSlam = booleanSetting("stun-slam", "Stun Slam", false)
                .description("Follows a shield-breaking axe hit with an automatic mace slam once the target's stun window opens.");
    }

    @Override
    public void onEnable() {
        nextHitAt = 0L;
        restoreAt = 0L;
        restoreSlot = -1;
        clearStunSlam();
    }

    @Override
    public void onDisable() {
        clearStunSlam();
        restoreImmediately();
    }

    @Override
    public void onTick(TickEvent event) {
        long now = System.currentTimeMillis();
        expireStunSlam(now);
        updateRestore();
        if (processStunSlamFollowUp(now)) {
            return;
        }
    }

    /** Runs synchronously at the start of Minecraft's vanilla attack method, before it reads the
     * selected hotbar stack. Tick polling is too late on clients that process input before tick
     * callbacks. */
    public void onAttackAttempt() {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextHitAt) {
            return;
        }
        minecraft.crosshairEntity(0.0D).ifPresent(target -> prepareWeaponForTarget(target, now));
    }

    private void prepareWeaponForTarget(AimTarget target, long now) {
        boolean pendingStunSlam = isPendingStunSlam(target, now);
        if (pendingStunSlam && !isReadyStunSlam(target, now)) {
            return;
        }

        boolean stunSlamFollowUp = pendingStunSlam;
        if (now < nextHitAt && !stunSlamFollowUp) {
            return;
        }

        WeaponPick pick = selectWeapon(target, now);
        if (pick == null) {
            if (!(enabled(maces) && !canUseMaceNow())) {
                nextHitAt = now + NO_WEAPON_RETRY_MS;
            }
            return;
        }

        if (!stunSlamFollowUp && !rollChance(now)) {
            return;
        }
        if (!swapToWeapon(pick, now)) {
            return;
        }

        if (pick.kind() == WeaponKind.Axe) {
            if (enabled(stunSlam) && hasAllowedMace() && minecraft.isTargetBlockingWithShield(target.id())) {
                queueStunSlam(target, now);
            }
        } else if (isPendingStunSlam(target, now)) {
            clearStunSlam();
        }
        nextHitAt = now + nextHitDelayMs();
    }

    private boolean processStunSlamFollowUp(long now) {
        if (stunSlamTargetId == null) {
            return false;
        }
        if (now < stunSlamReadyAt) {
            return true;
        }

        Optional<AimTarget> targetOpt = minecraft.crosshairEntity(0.0D)
                .filter(candidate -> candidate.id().equals(stunSlamTargetId));
        if (targetOpt.isEmpty()) {
            clearStunSlam();
            return false;
        }
        AimTarget target = targetOpt.get();

        int maceSlot = findMaceSlot(true);
        if (maceSlot < 0) {
            if (hasAllowedMace() && !canUseMaceNow()) {
                return true;
            }
            clearStunSlam();
            nextHitAt = now + NO_WEAPON_RETRY_MS;
            return true;
        }

        if (!swapToWeapon(new WeaponPick(WeaponKind.Mace, maceSlot), now)) {
            return true;
        }

        clearStunSlam();
        minecraft.attackTarget(target.id());
        nextHitAt = now + nextHitDelayMs();
        return true;
    }

    private WeaponPick selectWeapon(AimTarget target, long now) {
        boolean shieldBlocking = enabled(axes) && minecraft.isTargetBlockingWithShield(target.id());
        boolean pendingStunSlam = isPendingStunSlam(target, now);

        if (pendingStunSlam && isReadyStunSlam(target, now)) {
            int maceSlot = findMaceSlot(true);
            if (maceSlot >= 0) {
                return new WeaponPick(WeaponKind.Mace, maceSlot);
            }
        }

        if (shieldBlocking) {
            int axeSlot = findAxeSlot();
            if (axeSlot >= 0) {
                return new WeaponPick(WeaponKind.Axe, axeSlot);
            }
        }

        if (enabled(maces)) {
            int maceSlot = findMaceSlot(false);
            if (maceSlot >= 0) {
                return new WeaponPick(WeaponKind.Mace, maceSlot);
            }
        }

        return null;
    }

    private int findAxeSlot() {
        for (ItemType tier : AXE_TIERS) {
            int slot = minecraft.findHotbarItem(tier);
            if (slot >= 0) {
                return slot;
            }
        }
        return -1;
    }

    private int findMaceSlot(boolean forStunSlam) {
        if (!enabled(maces) && !forStunSlam) {
            return -1;
        }
        if (!canUseMaceNow()) {
            return -1;
        }
        int slot = minecraft.findHotbarItem(ItemType.MACE);
        if (slot < 0) {
            return -1;
        }
        if (!maceEnchantmentsAllow(slot)) {
            return -1;
        }
        return slot;
    }

    /** Whether a mace exists in the hotbar and satisfies the enchantment requirements, ignoring the
     * ground/falling gate — used to decide whether Stun Slam is worth queuing at all versus just
     * waiting on movement. */
    private boolean hasAllowedMace() {
        int slot = minecraft.findHotbarItem(ItemType.MACE);
        return slot >= 0 && maceEnchantmentsAllow(slot);
    }

    private boolean maceEnchantmentsAllow(int slot) {
        if (enabled(requireBreach) && minecraft.hotbarEnchantmentLevel(slot, EnchantmentType.BREACH) <= 0) {
            return false;
        }
        return !enabled(requireDensity) || minecraft.hotbarEnchantmentLevel(slot, EnchantmentType.DENSITY) > 0;
    }

    private boolean canUseMaceNow() {
        if (enabled(groundCheck) && minecraft.isOnGround()) {
            return false;
        }
        if (!enabled(smashOnly)) {
            return true;
        }
        CombatState state = minecraft.combatState();
        return !minecraft.isOnGround() && state.velocityY() < 0.0D && state.fallDistance() > 0.0D;
    }

    private boolean swapToWeapon(WeaponPick pick, long now) {
        int previousSlot = minecraft.selectedHotbarSlot();
        if (previousSlot == pick.slot()) {
            return true;
        }
        if (!minecraft.selectHotbarSlot(pick.slot())) {
            nextHitAt = now + SWAP_FAILED_RETRY_MS;
            return false;
        }
        if (restoreSlot < 0) {
            restoreSlot = previousSlot;
        }
        restoreAt = now + nextRestoreDelayMs();
        return true;
    }

    private void updateRestore() {
        if (restoreSlot < 0 || System.currentTimeMillis() < restoreAt) {
            return;
        }
        restoreImmediately();
    }

    private void restoreImmediately() {
        if (restoreSlot < 0) {
            return;
        }
        int slot = restoreSlot;
        restoreSlot = -1;
        restoreAt = 0L;
        if (minecraft.selectedHotbarSlot() != slot) {
            minecraft.selectHotbarSlot(slot);
        }
    }

    private boolean rollChance(long now) {
        if (chance.value() >= 100.0D) {
            return true;
        }
        if (ThreadLocalRandom.current().nextInt(100) < (int) Math.round(chance.value())) {
            return true;
        }
        nextHitAt = now + nextHitDelayMs();
        return false;
    }

    private void queueStunSlam(AimTarget target, long now) {
        stunSlamTargetId = target.id();
        stunSlamReadyAt = now + STUN_SLAM_FOLLOW_UP_DELAY_MS;
        stunSlamUntil = now + STUN_SLAM_WINDOW_MS;
    }

    private boolean isPendingStunSlam(AimTarget target, long now) {
        return stunSlamTargetId != null && now <= stunSlamUntil && stunSlamTargetId.equals(target.id());
    }

    private boolean isReadyStunSlam(AimTarget target, long now) {
        return isPendingStunSlam(target, now) && now >= stunSlamReadyAt;
    }

    private void expireStunSlam(long now) {
        if (stunSlamTargetId != null && now > stunSlamUntil) {
            clearStunSlam();
        }
    }

    private void clearStunSlam() {
        stunSlamTargetId = null;
        stunSlamReadyAt = 0L;
        stunSlamUntil = 0L;
    }

    private int nextHitDelayMs() {
        return randomBetween((int) Math.round(delayMsMin.value()), (int) Math.round(delayMsMax.value()));
    }

    private int nextRestoreDelayMs() {
        return randomBetween((int) Math.round(restoreDelayMsMin.value()), (int) Math.round(restoreDelayMsMax.value()));
    }

    private static int randomBetween(int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }

    private enum WeaponKind {
        Axe,
        Mace
    }

    private record WeaponPick(WeaponKind kind, int slot) {
    }
}
