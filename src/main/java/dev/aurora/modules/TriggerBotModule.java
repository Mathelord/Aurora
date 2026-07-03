package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.combat.CriticalHitDetector;
import dev.aurora.input.RealClickSimulator;
import dev.aurora.input.CritSprintReset;
import dev.aurora.minecraft.CombatState;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Swings (and attacks) the instant a player is under your real crosshair — no aiming involved,
 * unlike Silent Aura/Aim Assist, it only ever acts on whatever you are already looking at.
 */
public final class TriggerBotModule extends AbstractModule {
    private static final double MISS_SWING_COOLDOWN_THRESHOLD = 0.92D;
    private static final double PERFECT_COOLDOWN_THRESHOLD = 0.99D;
    private static final double PERFECT_EARLY_COOLDOWN_THRESHOLD = 0.848D;

    private final MinecraftBridge minecraft;
    private final ModuleSetting critMode;
    private final ModuleSetting perfectEarlyChance;
    private final ModuleSetting perfectLateChance;
    private final ModuleSetting missChanceMin;
    private final ModuleSetting missChanceMax;
    private final ModuleSetting pauseOnConsume;
    private final ModuleSetting holdMouse;
    private final CritSprintReset critSprintReset;
    private final CriticalHitDetector criticalHitDetector = new CriticalHitDetector();

    private boolean missRolled;
    private boolean lateDelayQueued;
    private long lateReadyAt;
    private PerfectTiming perfectTiming = PerfectTiming.Full;

    public TriggerBotModule(MinecraftBridge minecraft) {
        super("trigger-bot", "Trigger Bot", "Combat", "Automatically swings when a player is under your crosshair.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        critSprintReset = new CritSprintReset(minecraft);
        critMode = optionSetting("crit-mode", "Crit Mode", CritMode.AirCrit.ordinal(), optionNames(CritMode.values()))
                .description("Off disables critical timing. AirCrit attacks normally on ground and waits for a valid critical window while airborne.");
        perfectEarlyChance = setting("perfect-early", "Perfect Early %", 0.0D, 0.0D, 100.0D, 1.0D)
                .description("Chance that a swing attacks at Eclipse's early 84.8% cooldown threshold.");
        perfectLateChance = setting("perfect-late", "Perfect Late %", 0.0D, 0.0D, 100.0D, 1.0D)
                .description("Chance that a swing waits an additional 80-180 ms after full cooldown.");
        missChanceMin = setting("miss-chance-min", "Miss Chance Min %", 0.0D, 0.0D, 100.0D, 1.0D)
                .description("Minimum randomized chance to perform an intentional miss swing when no target is under the crosshair.");
        missChanceMax = setting("miss-chance-max", "Miss Chance Max %", 0.0D, 0.0D, 100.0D, 1.0D)
                .description("Maximum randomized chance to perform an intentional miss swing when no target is under the crosshair.");
        pauseOnConsume = booleanSetting("pause-on-consume", "Pause While Using", true)
                .description("Pauses attacks while eating, drinking, blocking, or using another item.");
        holdMouse = booleanSetting("hold-mouse", "Hold Left Mouse", false)
                .description("Requires the physical left mouse button to be held before Trigger Bot can attack.");
    }

    public boolean suppressPhysicalHeldAttack() {
        return enabled() && enabled(holdMouse);
    }

    @Override
    public void onEnable() {
        missRolled = false;
        criticalHitDetector.reset();
        perfectTiming = rollPerfectTiming();
        clearPendingAttack();
    }

    @Override
    public void onDisable() {
        clearPendingAttack();
        criticalHitDetector.reset();
    }

    @Override
    public void onTick(TickEvent event) {
        if (enabled(holdMouse) && !minecraft.isMouseButtonDown(0)) {
            missRolled = false;
            clearPendingAttack();
            return;
        }
        Optional<AimTarget> target = minecraft.crosshairEntity(0.0D);
        if (target.isEmpty()) {
            clearPendingAttack();
            maybeMissSwing();
            return;
        }
        if (enabled(pauseOnConsume) && minecraft.isUsingItem()) {
            clearPendingAttack();
            return;
        }
        CombatState combat = minecraft.combatState();
        double cooldown = minecraft.attackCooldown();
        CriticalHitDetector.Detection critical = criticalHitDetector.detect(combat, cooldown);
        double threshold = perfectTiming == PerfectTiming.Early
                ? PERFECT_EARLY_COOLDOWN_THRESHOLD : PERFECT_COOLDOWN_THRESHOLD;
        if (critMode() == CritMode.AirCrit && critical.airborne() && critical.environmentEligible()) {
            threshold = Math.min(threshold, CriticalHitDetector.CRITICAL_COOLDOWN);
        }
        if (cooldown < threshold) {
            if (cooldown < MISS_SWING_COOLDOWN_THRESHOLD) {
                missRolled = false;
            }
            return;
        }
        if (!canAttackWithCritTiming(critical)) return;
        if (perfectTiming == PerfectTiming.Late && !lateDelayQueued) {
            lateDelayQueued = true;
            lateReadyAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(80L, 181L);
            return;
        }
        if (lateDelayQueued && System.currentTimeMillis() < lateReadyAt) return;
        if (critical.needsSprintReset() && !critSprintReset.readyToCrit(combat)) return;
        boolean attacked = RealClickSimulator.leftClick();
        critSprintReset.complete();
        if (attacked) {
            missRolled = false;
            perfectTiming = rollPerfectTiming();
            lateDelayQueued = false;
            lateReadyAt = 0L;
        }
    }

    private void maybeMissSwing() {
        if (missChanceMax.value() <= 0.0D || minecraft.attackCooldown() < MISS_SWING_COOLDOWN_THRESHOLD) {
            missRolled = false;
            return;
        }
        if (missRolled) {
            return;
        }
        missRolled = true;
        if (ThreadLocalRandom.current().nextDouble(100.0D) < rollMissChance()) {
            minecraft.swingMainHand();
        }
    }

    private double rollMissChance() {
        double min = Math.min(missChanceMin.value(), missChanceMax.value());
        double max = Math.max(missChanceMin.value(), missChanceMax.value());
        return max <= min ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }

    private boolean canAttackWithCritTiming(CriticalHitDetector.Detection critical) {
        return critMode() == CritMode.Off || !critical.available() || !critical.airborne()
                || !critical.environmentEligible() || critical.timingWindow();
    }

    private CritMode critMode() {
        return enumSetting(critMode, CritMode.values());
    }

    private PerfectTiming rollPerfectTiming() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble(100.0D) < perfectEarlyChance.value()) return PerfectTiming.Early;
        if (random.nextDouble(100.0D) < perfectLateChance.value()) return PerfectTiming.Late;
        return PerfectTiming.Full;
    }

    private void clearPendingAttack() {
        critSprintReset.complete();
        lateDelayQueued = false;
        lateReadyAt = 0L;
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }

    private static <E extends Enum<E>> E enumSetting(ModuleSetting setting, E[] values) {
        int index = (int) Math.round(setting.value());
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    private static <E extends Enum<E>> java.util.List<String> optionNames(E[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }

    private enum CritMode { Off, AirCrit }
    private enum PerfectTiming { Early, Full, Late }
}
