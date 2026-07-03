package dev.aurora.modules;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.AimSmoothingProfile;
import dev.aurora.aim.SilentAimRequest;
import dev.aurora.aim.SilentAimSystem;
import dev.aurora.aim.StrafeAimAssist;
import dev.aurora.aim.Vec3;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class AimAssistModule extends AbstractModule {
    private static final String AIM_OWNER = "aim-assist";
    private static final double TARGET_RESPONSE = 18.0D;
    private static final double STRAFE_TARGET_RESPONSE = 52.0D;
    private static final double MIN_STRENGTH = 0.05D;
    private static final double MAX_STRENGTH = 1.0D;
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 2_000_000_000L;

    private final MinecraftBridge minecraft;
    private final Consumer<String> diagnosticSink;
    private final ModuleSetting rangeMin;
    private final ModuleSetting rangeMax;
    private final ModuleSetting fov;
    private final ModuleSetting ignoreWalls;
    private final ModuleSetting priority;
    private final ModuleSetting holdMouse;
    private final ModuleSetting pauseOnConsume;
    private final ModuleSetting strengthMin;
    private final ModuleSetting strengthMax;
    private final ModuleSetting strafeAimIncrease;
    private final ModuleSetting strafeAimBoost;
    private String targetId;
    private double currentRange;
    private double currentStrength;
    private String lastDiagnostic;
    private long lastDiagnosticNanos;

    public AimAssistModule(MinecraftBridge minecraft) {
        this(minecraft, message -> {
        });
    }

    public AimAssistModule(MinecraftBridge minecraft, Consumer<String> diagnosticSink) {
        super("aim-assist", "Aim Assist", "Combat", "Smoothly assists your visible rotation toward players.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.diagnosticSink = Objects.requireNonNull(diagnosticSink, "diagnosticSink");
        this.rangeMin = setting("range-min", "Range Min", 4.0D, 1.0D, 8.0D, 0.1D)
                .description("Minimum randomized distance at which Aim Assist can acquire and follow a player.");
        this.rangeMax = setting("range-max", "Range Max", 5.0D, 1.0D, 8.0D, 0.1D)
                .description("Maximum randomized distance at which Aim Assist can acquire and follow a player.");
        this.fov = setting("fov", "FOV", 90.0D, 1.0D, 360.0D, 1.0D)
                .description("Angular field of view around your crosshair in which targets can be acquired.");
        this.ignoreWalls = booleanSetting("ignore-walls", "Ignore Walls", true)
                .description("Allows Aim Assist to select players even when blocks obstruct the line of sight.");
        this.priority = optionSetting("priority", "Priority", TargetPriority.PreferAge.ordinal(), optionNames(TargetPriority.values()))
                .description("Controls how Aim Assist chooses between multiple valid targets.");
        this.holdMouse = booleanSetting("hold-mouse", "Hold Left Mouse", false)
                .description("Requires the physical left mouse button to be held before Aim Assist tracks a target.");
        this.pauseOnConsume = booleanSetting("pause-on-consume", "Pause While Using", true)
                .description("Pauses aiming while eating, drinking, blocking, or using another item.");
        this.strengthMin = setting("strength-min", "Strength Min", 0.6D, MIN_STRENGTH, MAX_STRENGTH, 0.025D)
                .description("Minimum randomized strength your view is pulled toward the selected target.");
        this.strengthMax = setting("strength-max", "Strength Max", 0.75D, MIN_STRENGTH, MAX_STRENGTH, 0.025D)
                .description("Maximum randomized strength your view is pulled toward the selected target.");
        this.strafeAimIncrease = booleanSetting("strafe-aim-increase", "Strafe Aim Increase", true)
                .description("Adaptively increases aim speed while movement crosses sideways through the aim direction.");
        this.strafeAimBoost = setting("strafe-aim-boost", "Strafe Aim Boost", 0.35D, 0.0D, 1.5D, 0.05D)
                .description("Maximum adaptive aim-speed increase while strafing.")
                .visibleWhen(() -> enabled(strafeAimIncrease));
    }

    @Override
    public void onEnable() {
        currentRange = rollRange();
        currentStrength = rollStrength();
        clearAim();
    }

    @Override
    public void onDisable() {
        clearAim();
        SilentAimSystem.get().clearOwner(AIM_OWNER);
    }

    @Override
    public void onRender(RenderEvent event) {
        updateAim();
    }

    @Override
    public void onTick(TickEvent event) {
        updateAim();
    }

    private void updateAim() {
        if (enabled(holdMouse) && !minecraft.isMouseButtonDown(0)) {
            clearAim();
            return;
        }
        if (enabled(pauseOnConsume) && minecraft.isUsingItem()) {
            clearAim();
            return;
        }
        AimContext context = minecraft.aimContext(currentRange, enabled(ignoreWalls));
        if (!context.available()) {
            diagnostic("Aim assist paused: Minecraft client, player, or world was not available.");
            clearAim();
            return;
        }
        if (context.screenOpen()) {
            clearAim();
            return;
        }

        AimTarget target = acquireTarget(context).orElse(null);
        if (target == null) {
            diagnostic("Aim assist scanning: no player target within range/FOV. Reflected players: " + context.targets().size() + ".");
            clearAim();
            return;
        }

        if (!target.id().equals(targetId)) {
            currentRange = rollRange();
            currentStrength = rollStrength();
        }
        targetId = target.id();
        AimAngles currentAngles = new AimAngles((float) context.yaw(), (float) context.pitch());
        AimAngles targetAngles = new AimAngles((float) target.yaw(), (float) context.pitch());
        StrafeAimAssist.Result strafe = enabled(strafeAimIncrease)
                ? StrafeAimAssist.plan(minecraft.playerVelocity(), currentAngles, targetAngles,
                strafeAimBoost.value(), TARGET_RESPONSE, STRAFE_TARGET_RESPONSE)
                : new StrafeAimAssist.Result(TARGET_RESPONSE, 1.0D);
        AimAngles applied = SilentAimSystem.get().apply(
                new SilentAimSystem.AimRuntime(
                        true,
                        currentAngles,
                        context.mouseSensitivity(),
                        angles -> minecraft.applyAimRotation(angles.yaw(), angles.pitch())
                ),
                SilentAimRequest.builder(AIM_OWNER, targetAngles)
                        .priority(80)
                        .targetPoint(target.targetPoint() == null ? Vec3.ZERO : target.targetPoint())
                        .smoothingProfile(AimSmoothingProfile.fastHuman())
                        .targetResponse(strafe.targetResponse())
                        .rotationMultiplier(currentStrength * strafe.rotationMultiplier())
                        .globalDecoupledAllowed(false)
                        .build()
        );
        if (applied == null) {
            diagnostic("Aim assist found target '" + target.displayName() + "' but could not write player rotation.");
        }
    }

    private Optional<AimTarget> acquireTarget(AimContext context) {
        if (targetPriority() == TargetPriority.PreferAge) {
            Optional<AimTarget> current = context.targets().stream()
                    .filter(target -> isTargetCandidate(context, target))
                    .filter(target -> target.id().equals(targetId))
                    .findFirst();
            if (current.isPresent()) {
                return current;
            }
            return context.targets().stream()
                    .filter(target -> isTargetCandidate(context, target))
                    .min(Comparator.comparingDouble(target -> angleDistance(context, target)));
        }

        return context.targets().stream()
                .filter(target -> isTargetCandidate(context, target))
                .min(targetPriority().comparator(context));
    }

    private boolean isTargetCandidate(AimContext context, AimTarget target) {
        if (target == null) {
            return false;
        }
        if (fov.value() >= 360.0D) {
            return true;
        }
        return angleDistance(context, target) <= fov.value();
    }

    private TargetPriority targetPriority() {
        return enumSetting(priority, TargetPriority.values());
    }

    private double rollRange() {
        double min = Math.min(rangeMin.value(), rangeMax.value());
        double max = Math.max(rangeMin.value(), rangeMax.value());
        return max <= min ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }

    private double rollStrength() {
        double min = Math.min(strengthMin.value(), strengthMax.value());
        double max = Math.max(strengthMin.value(), strengthMax.value());
        return max <= min ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }

    private void clearAim() {
        targetId = null;
        SilentAimSystem.get().clearOwner(AIM_OWNER);
    }

    private void diagnostic(String message) {
        long now = System.nanoTime();
        if (message.equals(lastDiagnostic) && now - lastDiagnosticNanos < DIAGNOSTIC_INTERVAL_NANOS) {
            return;
        }
        lastDiagnostic = message;
        lastDiagnosticNanos = now;
        diagnosticSink.accept(message);
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }

    private static <E extends Enum<E>> E enumSetting(ModuleSetting setting, E[] values) {
        int index = (int) Math.round(setting.value());
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    private static <E extends Enum<E>> List<String> optionNames(E[] values) {
        return java.util.Arrays.stream(values)
                .map(Enum::name)
                .toList();
    }

    private static double angleDistance(AimContext context, AimTarget target) {
        double yawDelta = wrapDegrees(target.yaw() - context.yaw());
        double pitchDelta = target.pitch() - context.pitch();
        return Math.hypot(yawDelta, pitchDelta);
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }
        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    private enum TargetPriority {
        LowestDistance {
            @Override
            Comparator<AimTarget> comparator(AimContext context) {
                return Comparator.comparingDouble(AimTarget::distanceSquared);
            }
        },
        HighestDistance {
            @Override
            Comparator<AimTarget> comparator(AimContext context) {
                return Comparator.comparingDouble(AimTarget::distanceSquared).reversed();
            }
        },
        LowestHealth {
            @Override
            Comparator<AimTarget> comparator(AimContext context) {
                return Comparator.comparingDouble(AimTarget::health);
            }
        },
        HighestHealth {
            @Override
            Comparator<AimTarget> comparator(AimContext context) {
                return Comparator.comparingDouble(AimTarget::health).reversed();
            }
        },
        ClosestAngle {
            @Override
            Comparator<AimTarget> comparator(AimContext context) {
                return Comparator.comparingDouble(target -> angleDistance(context, target));
            }
        },
        PreferAge {
            @Override
            Comparator<AimTarget> comparator(AimContext context) {
                return ClosestAngle.comparator(context);
            }
        };

        abstract Comparator<AimTarget> comparator(AimContext context);
    }
}
