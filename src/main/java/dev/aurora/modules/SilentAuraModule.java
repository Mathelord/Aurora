package dev.aurora.modules;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.AimPointDrift;
import dev.aurora.aim.AimMath;
import dev.aurora.aim.AimSmoothingProfile;
import dev.aurora.aim.DecoupledAimState;
import dev.aurora.aim.SilentAimRequest;
import dev.aurora.aim.SilentAimSystem;
import dev.aurora.aim.SmoothingMode;
import dev.aurora.aim.StrafeAimAssist;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.combat.CriticalHitDetector;
import dev.aurora.input.RealClickSimulator;
import dev.aurora.input.CritSprintReset;
import dev.aurora.minecraft.CombatState;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.TargetPose;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class SilentAuraModule extends AbstractModule {
    private static final String AIM_OWNER = "silent-aura";
    private static final double TARGET_RESPONSE = 18.0D;
    private static final double STRAFE_TARGET_RESPONSE = 52.0D;
    private static final double PERFECT_COOLDOWN_THRESHOLD = 0.99D;
    private static final double PERFECT_EARLY_COOLDOWN_THRESHOLD = 0.848D;

    private final MinecraftBridge minecraft;
    private final ModuleSetting rangeMin;
    private final ModuleSetting rangeMax;
    private final ModuleSetting fov;
    private final ModuleSetting ignoreWalls;
    private final ModuleSetting priority;
    private final ModuleSetting holdMouse;
    private final ModuleSetting critMode;
    private final ModuleSetting perfectEarlyChance;
    private final ModuleSetting perfectLateChance;
    private final ModuleSetting missChance;
    private final ModuleSetting pauseOnUse;
    private final ModuleSetting decoupled;
    private final ModuleSetting strength;
    private final ModuleSetting speed;
    private final ModuleSetting strafeAimIncrease;
    private final ModuleSetting strafeAimBoost;
    private final CritSprintReset critSprintReset;
    private final CriticalHitDetector criticalHitDetector = new CriticalHitDetector();
    private final AimPointDrift chinDrift = new AimPointDrift(0.10D, 0.07D, 0.16D, 600L, 1_700L);
    private final AimPointDrift microDrift = new AimPointDrift(0.025D, 0.018D, 0.24D, 150L, 350L);

    private String targetId;
    private String pendingAttackTargetId;
    private long pendingAttackAt;
    private boolean missRolled;
    private PerfectTiming perfectTiming = PerfectTiming.Full;
    private double currentRange;

    public SilentAuraModule(MinecraftBridge minecraft) {
        super("silent-aura", "Silent Aura", "Combat", "Decoupled targeting, steering and timed attacks.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.critSprintReset = new CritSprintReset(minecraft);
        // Distance at which the aura starts aiming/tracking a target. This is NOT a hit reach: the
        // actual swing is gated by the game's vanilla crosshair raycast (see realClickAttack), so you
        // begin aiming from here but can only land hits at legit reach. Randomized between the two
        // bounds per target acquisition (see rollRange) instead of a fixed distance, so the aura
        // doesn't notice every target at exactly the same range.
        rangeMin = setting("range-min", "Aim Range Min", 4.0D, 1.0D, 8.0D, 0.1D)
                .description("Minimum randomized distance at which Silent Aura starts tracking a target. Actual hits remain limited by the normal crosshair reach.");
        rangeMax = setting("range-max", "Aim Range Max", 5.0D, 1.0D, 8.0D, 0.1D)
                .description("Maximum randomized distance at which Silent Aura starts tracking a target. Actual hits remain limited by the normal crosshair reach.");
        fov = setting("fov", "FOV", 180.0D, 1.0D, 360.0D, 1.0D)
                .description("Angular field of view around your crosshair in which targets can be acquired.");
        ignoreWalls = booleanSetting("ignore-walls", "Ignore Walls", false)
                .description("Allows Silent Aura to track players even when blocks obstruct the line of sight.");
        priority = optionSetting("priority", "Priority", TargetPriority.PreferAge.ordinal(), names(TargetPriority.values()))
                .description("Controls how Silent Aura chooses between multiple valid targets.");
        holdMouse = booleanSetting("hold-mouse", "Hold Left Mouse", false)
                .description("Requires the physical left mouse button to be held before aiming or attacking.");
        critMode = optionSetting("crit-mode", "Crit Mode", CritMode.AirCrit.ordinal(), names(CritMode.values()))
                .description("Off disables critical timing. AirCrit attacks normally on ground and waits for a valid critical window while airborne.");
        perfectEarlyChance = setting("perfect-early", "Perfect Early %", 6.0D, 0.0D, 100.0D, 1.0D)
                .description("Chance that a swing attacks at Eclipse's early 84.8% cooldown threshold.");
        perfectLateChance = setting("perfect-late", "Perfect Late %", 6.0D, 0.0D, 100.0D, 1.0D)
                .description("Chance that a swing waits an additional 80-180 ms after full cooldown.");
        missChance = setting("miss-chance", "Miss Chance %", 0.0D, 0.0D, 100.0D, 1.0D)
                .description("Chance to swing once per cooldown when an enemy is nearby but outside the crosshair.");
        pauseOnUse = booleanSetting("pause-on-use", "Pause While Using", true)
                .description("Pauses attacks while eating, drinking, blocking, or using another item.");
        decoupled = booleanSetting("decoupled", "Decoupled Aim", true)
                .description("Separates visible camera movement from the silent server-facing aim rotation.");
        strength = setting("strength", "Aim Strength", 0.78D, 0.05D, 1.0D, 0.01D)
                .description("Controls how strongly Silent Aura pulls its aim toward the selected target.");
        speed = setting("speed", "Aim Speed", 0.78D, 0.05D, 1.0D, 0.01D)
                .description("Controls how quickly Silent Aura approaches the target rotation.");
        strafeAimIncrease = booleanSetting("strafe-aim-increase", "Strafe Aim Increase", true)
                .description("Adaptively increases aim speed while movement crosses sideways through the aim direction.");
        strafeAimBoost = setting("strafe-aim-boost", "Strafe Aim Boost", 0.35D, 0.0D, 1.5D, 0.05D)
                .description("Maximum adaptive aim-speed increase while strafing.")
                .visibleWhen(() -> enabled(strafeAimIncrease));
    }

    /** The id of the entity this aura is currently locked onto, or {@code null} when it has no target
     * or is disabled. Consumed by the target-ring renderer so it can anchor to the live target. */
    public String currentAimTargetId() {
        return enabled() ? targetId : null;
    }

    public boolean suppressPhysicalHeldAttack() {
        return enabled() && enabled(holdMouse);
    }

    @Override
    public void onEnable() {
        clearState();
    }

    @Override
    public void onDisable() {
        clearState();
        SilentAimSystem.get().clearOwner(AIM_OWNER);
    }

    @Override
    public void onTick(TickEvent event) {
        update(true);
    }

    @Override
    public void onRender(RenderEvent event) {
        update(false);
    }

    private void update(boolean allowAttack) {
        AimContext context = minecraft.aimContext(currentRange, enabled(ignoreWalls));
        if (!context.available() || context.screenOpen() || enabled(holdMouse) && !minecraft.isMouseButtonDown(0)) {
            clearAim();
            return;
        }

        AimTarget target = acquireTarget(context);
        if (target == null) {
            clearAim();
            if (allowAttack && !(enabled(pauseOnUse) && minecraft.isUsingItem())) {
                maybeMissSwing();
            }
            return;
        }

        if (!target.id().equals(targetId)) {
            currentRange = rollRange();
        }
        targetId = target.id();
        // Eclipse advances its rotation state from the render loop only. Keeping the tick path out
        // of the smoother is important: otherwise tick and render callbacks feed two unrelated
        // clocks into acceleration, random speed modulation, mouse quantization, and aim drift.
        if (!allowAttack) {
            applyAim(context, target);
        }

        if (allowAttack) {
            if (enabled(pauseOnUse) && minecraft.isUsingItem()) {
                clearPendingAttack();
            } else if (minecraft.crosshairEntity(0.0D).isEmpty()) {
                clearPendingAttack();
                maybeMissSwing();
            } else {
                attackWhenReady(target);
            }
        }
    }

    private void applyAim(AimContext context, AimTarget target) {
        AimAngles currentAngles = new AimAngles((float) context.yaw(), (float) context.pitch());
        dev.aurora.aim.Vec3 aimPoint = movingAimPoint(target);
        AimAngles targetAngles = anglesTo(minecraft.playerEyePosition(), aimPoint);
        StrafeAimAssist.Result strafe = enabled(strafeAimIncrease)
                ? StrafeAimAssist.plan(minecraft.playerVelocity(), currentAngles, targetAngles,
                strafeAimBoost.value(), TARGET_RESPONSE, STRAFE_TARGET_RESPONSE)
                : new StrafeAimAssist.Result(TARGET_RESPONSE, 1.0D);
        SilentAimSystem.get().apply(
                new SilentAimSystem.AimRuntime(
                        true,
                        currentAngles,
                        context.mouseSensitivity(),
                        angles -> minecraft.applyAimRotation(angles.yaw(), angles.pitch())
                ),
                SilentAimRequest.builder(AIM_OWNER, targetAngles)
                        .priority(100)
                        .targetPoint(aimPoint)
                        .smoothingProfile(smoothingProfile())
                        .targetResponse(strafe.targetResponse())
                        .rotationMultiplier(strength.value() * strafe.rotationMultiplier())
                        .decoupled(enabled(decoupled))
                        .globalDecoupledAllowed(false)
                        .build()
        );
    }

    private AimTarget acquireTarget(AimContext context) {
        double referenceYaw = context.yaw();
        double referencePitch = context.pitch();
        if (DecoupledAimState.get().isActive()) {
            AimAngles visual = DecoupledAimState.get().visualAngles();
            referenceYaw = visual.yaw();
            referencePitch = visual.pitch();
        }
        final double yaw = referenceYaw;
        final double pitch = referencePitch;

        // Retention is intentionally not FOV-gated: once a target is locked, keep it as long as it
        // stays valid and in range (aimContext already enforces range). With decoupled aim the FOV
        // cone is measured from the frozen visual facing, so a strafing enemy would otherwise leave
        // the cone while the camera holds still and the aura would stop rotating until you manually
        // turned. FOV only limits which NEW targets can be acquired below.
        if (targetPriority() == TargetPriority.PreferAge && targetId != null) {
            AimTarget current = context.targets().stream()
                    .filter(target -> target.id().equals(targetId))
                    .findFirst()
                    .orElse(null);
            if (current != null) {
                return current;
            }
        }

        Comparator<AimTarget> comparator = switch (targetPriority()) {
            case PreferAge, ClosestAngle -> Comparator.comparingDouble(target -> angularDistance(target, yaw, pitch));
            case ClosestDistance -> Comparator.comparingDouble(AimTarget::distanceSquared);
            case LowestHealth -> Comparator.comparingDouble(AimTarget::health);
        };
        return context.targets().stream()
                .filter(target -> withinFov(target, yaw, pitch))
                .min(comparator)
                .orElse(null);
    }

    private void attackWhenReady(AimTarget target) {
        if (enabled(pauseOnUse) && minecraft.isUsingItem()) {
            clearPendingAttack();
            return;
        }
        long now = System.currentTimeMillis();
        double requiredCooldown = perfectTiming == PerfectTiming.Early
                ? PERFECT_EARLY_COOLDOWN_THRESHOLD : PERFECT_COOLDOWN_THRESHOLD;
        CombatState combat = minecraft.combatState();
        double currentCooldown = minecraft.attackCooldown();
        CriticalHitDetector.Detection critical = criticalHitDetector.detect(combat, currentCooldown);
        if (critMode() == CritMode.AirCrit && critical.airborne() && critical.environmentEligible()) {
            requiredCooldown = Math.min(requiredCooldown, CriticalHitDetector.CRITICAL_COOLDOWN);
        }
        if (currentCooldown < requiredCooldown) {
            if (currentCooldown < 0.92D) {
                missRolled = false;
            }
            clearPendingAttack();
            return;
        }
        if (!target.id().equals(pendingAttackTargetId)) {
            pendingAttackTargetId = target.id();
            pendingAttackAt = now + (perfectTiming == PerfectTiming.Late
                    ? ThreadLocalRandom.current().nextLong(80L, 181L) : 0L);
        }
        if (now < pendingAttackAt) {
            return;
        }
        if (!canAttackWithCritTiming(critical)) {
            return;
        }
        if (critical.needsSprintReset() && !critSprintReset.readyToCrit(combat)) {
            return;
        }
        if (realClickAttack(target)) {
            critSprintReset.complete();
            perfectTiming = rollPerfectTiming();
            missRolled = false;
        }
        clearPendingAttack();
    }

    /**
     * Drives the hit through the vanilla left-click handler instead of a direct attack call. Unlike a
     * force-set crosshair, this only fires when the game's own (reach-limited) crosshair raycast is
     * already on the chosen target: the silent aim rotates the player onto the entity so the vanilla
     * raycast lands there naturally, which means we can only ever hit what a real player could reach.
     */
    private boolean realClickAttack(AimTarget target) {
        AimTarget crosshair = minecraft.crosshairEntity(0.0D).orElse(null);
        if (crosshair == null || !crosshair.id().equals(target.id())) {
            return false;
        }
        return RealClickSimulator.leftClick();
    }

    private void maybeMissSwing() {
        if (missChance.value() <= 0.0D || minecraft.attackCooldown() < 0.92D) {
            missRolled = false;
            return;
        }
        if (missRolled) {
            return;
        }
        missRolled = true;
        // Eclipse considers any valid nearby player, including one hidden behind a wall. Visibility
        // controls aiming, not whether an otherwise plausible miss can be rolled.
        AimContext nearby = minecraft.aimContext(currentRange + 2.0D, true);
        if (nearby.available() && !nearby.targets().isEmpty()
                && ThreadLocalRandom.current().nextInt(100) < (int) Math.round(missChance.value())) {
            RealClickSimulator.leftClick();
        }
    }

    private AimSmoothingProfile smoothingProfile() {
        return new AimSmoothingProfile(SmoothingMode.Humanized, speed.value(), 74.0D, 58.0D, 0.0D, 0.0D);
    }

    private dev.aurora.aim.Vec3 movingAimPoint(AimTarget target) {
        TargetPose pose = minecraft.targetPose(target.id()).orElse(null);
        double width = pose == null ? 0.6D : pose.width();
        double height = pose == null ? 1.8D : pose.height();
        dev.aurora.aim.Vec3 eye = minecraft.playerEyePosition();
        dev.aurora.aim.Vec3 point = chinDrift.apply(target.id(), target.targetPoint(), eye, width, height);
        return microDrift.apply(target.id(), point, eye, width, height);
    }

    private static AimAngles anglesTo(dev.aurora.aim.Vec3 from, dev.aurora.aim.Vec3 to) {
        double deltaX = to.x() - from.x();
        double deltaY = to.y() - from.y();
        double deltaZ = to.z() - from.z();
        double horizontal = Math.hypot(deltaX, deltaZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontal));
        return new AimAngles((float) AimMath.wrapDegrees(yaw), pitch);
    }

    private boolean canAttackWithCritTiming(CriticalHitDetector.Detection critical) {
        return critMode() == CritMode.Off || !critical.available() || !critical.airborne()
                || !critical.environmentEligible() || critical.timingWindow();
    }

    private CritMode critMode() {
        return enumSetting(critMode, CritMode.values());
    }

    private boolean withinFov(AimTarget target, double yaw, double pitch) {
        return fov.value() >= 360.0D || angularDistance(target, yaw, pitch) <= fov.value();
    }

    private static double angularDistance(AimTarget target, double yaw, double pitch) {
        return Math.hypot(dev.aurora.aim.AimMath.wrapDegrees(target.yaw() - yaw), target.pitch() - pitch);
    }

    private TargetPriority targetPriority() {
        return enumSetting(priority, TargetPriority.values());
    }

    private PerfectTiming rollPerfectTiming() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble(100.0D) < perfectEarlyChance.value()) {
            return PerfectTiming.Early;
        }
        if (random.nextDouble(100.0D) < perfectLateChance.value()) {
            return PerfectTiming.Late;
        }
        return PerfectTiming.Full;
    }

    private double rollRange() {
        double min = Math.min(rangeMin.value(), rangeMax.value());
        double max = Math.max(rangeMin.value(), rangeMax.value());
        return max <= min ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }

    private void clearAim() {
        targetId = null;
        chinDrift.reset();
        microDrift.reset();
        clearPendingAttack();
        SilentAimSystem.get().clearOwner(AIM_OWNER);
    }

    private void clearState() {
        targetId = null;
        missRolled = false;
        criticalHitDetector.reset();
        perfectTiming = rollPerfectTiming();
        currentRange = rollRange();
        chinDrift.reset();
        microDrift.reset();
        clearPendingAttack();
    }

    private void clearPendingAttack() {
        critSprintReset.complete();
        pendingAttackTargetId = null;
        pendingAttackAt = 0L;
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }

    private static <E extends Enum<E>> E enumSetting(ModuleSetting setting, E[] values) {
        int index = (int) Math.round(setting.value());
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    private static <E extends Enum<E>> List<String> names(E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private enum TargetPriority {
        PreferAge,
        ClosestAngle,
        ClosestDistance,
        LowestHealth
    }

    private enum CritMode {
        Off,
        AirCrit
    }

    private enum PerfectTiming {
        Early,
        Full,
        Late
    }
}
