package dev.aurora.aim;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class SilentAimSystem {
    private static final double NANOS_TO_SECONDS = 1.0e-9;
    private static final double MIN_VISIBLE_DELTA = 0.001D;
    private static final double MIN_FRAME_TIME = 1.0D / 240.0D;
    private static final double MAX_FRAME_TIME = 1.0D / 20.0D;
    private static final double MAX_STEP_TO_DEGREES_PER_SECOND = 20.0D;
    private static final double MOUSE_STEP_SCALE = 0.15D;
    private static final double MIN_MOUSE_STEP = 0.0001D;
    private static final double HUMAN_DRIFT_RESPONSE = 10.0D;
    private static final double HUMAN_DRIFT_MIN_INTERVAL = 0.08D;
    private static final double HUMAN_DRIFT_MAX_INTERVAL = 0.18D;
    private static final double SPEED_MOD_RESPONSE = 5.5D;
    private static final double SPEED_MOD_MIN_INTERVAL = 0.15D;
    private static final double SPEED_MOD_MAX_INTERVAL = 0.40D;
    private static final SilentAimSystem INSTANCE = new SilentAimSystem();

    private String owner;
    private int priority = Integer.MIN_VALUE;
    private float lastSilentYaw;
    private float lastSilentPitch;
    private boolean hasActiveSilentAim;
    private AimAngles frameTargetAngles;
    private long lastFrameNanos;
    private double yawMoveRemainder;
    private double pitchMoveRemainder;
    private double humanYawDrift;
    private double humanPitchDrift;
    private double targetHumanYawDrift;
    private double targetHumanPitchDrift;
    private double humanDriftTimer;
    private double speedModulator = 1.0D;
    private double targetSpeedModulator = 1.0D;
    private double speedModulatorTimer;
    private RotationSink lastRotationSink;

    private SilentAimSystem() {
    }

    public static SilentAimSystem get() {
        return INSTANCE;
    }

    public synchronized AimAngles apply(AimRuntime runtime, SilentAimRequest request) {
        if (runtime == null || !runtime.available() || request == null || !accept(request)) {
            return null;
        }

        boolean ownerChanged = owner != null && !request.owner().equals(owner);
        if (ownerChanged) {
            resetMotionState();
        }

        owner = request.owner();
        priority = request.priority();

        AimSmoothingProfile profile = request.smoothingProfile().sanitized();
        AimAngles currentAngles = runtime.currentAngles().clampedPitch();
        boolean decoupled = request.decoupled();
        if (decoupled) {
            DecoupledAimState.get().activate(currentAngles);
        } else if (DecoupledAimState.get().isActive()) {
            runtime.rotationSink().apply(DecoupledAimState.get().deactivate());
        }
        AimAngles appliedAngles;
        if (profile.mode() == SmoothingMode.Instant) {
            appliedAngles = targetAngles(currentAngles, request.targetAngles(), request.shortestYawPath());
        } else {
            double frameTime = frameTimeSeconds();
            AimAngles frameTarget = smoothFrameTarget(request.targetAngles(), frameTime, request.targetResponse(), request.shortestYawPath());
            AimAngles humanTarget = applyHumanDrift(frameTarget, profile, frameTime);
            double effectiveMultiplier = request.rotationMultiplier() * tickSpeedModulator(profile, frameTime);
            AimAngles steppedAngles = step(currentAngles, humanTarget, profile, frameTime, effectiveMultiplier, request.shortestYawPath());
            appliedAngles = quantizeToMouseStep(currentAngles, steppedAngles, request.shortestYawPath(), runtime.mouseSensitivity());
        }

        AimAngles continuousAngles = targetAngles(currentAngles, appliedAngles, request.shortestYawPath());
        if (runtime.rotationSink().apply(continuousAngles)) {
            lastRotationSink = runtime.rotationSink();
            if (decoupled) {
                DecoupledAimState.get().updateSilentAngles(continuousAngles);
            }
            lastSilentYaw = continuousAngles.yaw();
            lastSilentPitch = continuousAngles.pitch();
            hasActiveSilentAim = true;
            return continuousAngles;
        }
        return null;
    }

    public synchronized void clearOwner(String owner) {
        if (owner == null || !owner.equals(this.owner)) {
            return;
        }
        clearImmediately();
    }

    public synchronized void clear() {
        clearImmediately();
    }

    public synchronized boolean hasActiveSilentAim() {
        return hasActiveSilentAim;
    }

    public synchronized float lastSilentYaw() {
        return lastSilentYaw;
    }

    public synchronized float lastSilentPitch() {
        return lastSilentPitch;
    }

    private void clearImmediately() {
        if (DecoupledAimState.get().isActive() && lastRotationSink != null) {
            lastRotationSink.apply(DecoupledAimState.get().deactivate());
        } else {
            DecoupledAimState.get().reset();
        }
        owner = null;
        priority = Integer.MIN_VALUE;
        hasActiveSilentAim = false;
        resetMotionState();
        lastRotationSink = null;
    }

    private void resetMotionState() {
        frameTargetAngles = null;
        lastFrameNanos = 0L;
        yawMoveRemainder = 0.0D;
        pitchMoveRemainder = 0.0D;
        humanYawDrift = 0.0D;
        humanPitchDrift = 0.0D;
        targetHumanYawDrift = 0.0D;
        targetHumanPitchDrift = 0.0D;
        humanDriftTimer = 0.0D;
        speedModulator = 1.0D;
        targetSpeedModulator = 1.0D;
        speedModulatorTimer = 0.0D;
    }

    private boolean accept(SilentAimRequest request) {
        return owner == null || request.owner().equals(owner) || request.priority() >= priority;
    }

    private AimAngles step(AimAngles current, AimAngles target, AimSmoothingProfile profile, double frameTime,
                           double multiplier, boolean shortestYawPath) {
        double yawDelta = yawDelta(current.yaw(), target.yaw(), shortestYawPath);
        double pitchDelta = target.pitch() - current.pitch();
        double totalDelta = Math.hypot(yawDelta, pitchDelta);
        if (totalDelta <= MIN_VISIBLE_DELTA || totalDelta <= profile.snapThreshold()) {
            return target;
        }

        double speedMultiplier = Math.max(0.0D, multiplier);
        double response = response(profile, totalDelta) * speedMultiplier;
        double blend = 1.0D - Math.exp(-response * frameTime);
        double maxYawMove = profile.maxYawStep() * MAX_STEP_TO_DEGREES_PER_SECOND * frameTime * speedMultiplier;
        double maxPitchMove = profile.maxPitchStep() * MAX_STEP_TO_DEGREES_PER_SECOND * frameTime * speedMultiplier;
        double yawMove = AimMath.clamp(yawDelta * blend, -maxYawMove, maxYawMove);
        double pitchMove = AimMath.clamp(pitchDelta * blend, -maxPitchMove, maxPitchMove);

        if (profile.mode() == SmoothingMode.Humanized && (Math.abs(yawMove) > MIN_VISIBLE_DELTA || Math.abs(pitchMove) > MIN_VISIBLE_DELTA)) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            yawMove *= 1.0D + rng.nextDouble(-0.13D, 0.13D);
            pitchMove *= 1.0D + rng.nextDouble(-0.10D, 0.10D);
        }

        return new AimAngles((float) (current.yaw() + yawMove), (float) (current.pitch() + pitchMove)).clampedPitch();
    }

    private AimAngles applyHumanDrift(AimAngles target, AimSmoothingProfile profile, double frameTime) {
        if (profile.mode() != SmoothingMode.Humanized || profile.jitter() <= 0.0D) {
            humanYawDrift = 0.0D;
            humanPitchDrift = 0.0D;
            targetHumanYawDrift = 0.0D;
            targetHumanPitchDrift = 0.0D;
            humanDriftTimer = 0.0D;
            return target;
        }

        humanDriftTimer -= frameTime;
        if (humanDriftTimer <= 0.0D) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double jitter = profile.jitter();
            targetHumanYawDrift = random.nextDouble(-jitter, jitter);
            targetHumanPitchDrift = random.nextDouble(-jitter * 0.65D, jitter * 0.65D);
            humanDriftTimer = random.nextDouble(HUMAN_DRIFT_MIN_INTERVAL, HUMAN_DRIFT_MAX_INTERVAL);
        }

        double blend = 1.0D - Math.exp(-HUMAN_DRIFT_RESPONSE * frameTime);
        humanYawDrift += (targetHumanYawDrift - humanYawDrift) * blend;
        humanPitchDrift += (targetHumanPitchDrift - humanPitchDrift) * blend;
        return new AimAngles((float) (target.yaw() + humanYawDrift), (float) (target.pitch() + humanPitchDrift)).clampedPitch();
    }

    private double tickSpeedModulator(AimSmoothingProfile profile, double frameTime) {
        if (profile.mode() != SmoothingMode.Humanized) {
            return 1.0D;
        }

        speedModulatorTimer -= frameTime;
        if (speedModulatorTimer <= 0.0D) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            targetSpeedModulator = rng.nextDouble(0.72D, 1.28D);
            speedModulatorTimer = rng.nextDouble(SPEED_MOD_MIN_INTERVAL, SPEED_MOD_MAX_INTERVAL);
        }

        double blend = 1.0D - Math.exp(-SPEED_MOD_RESPONSE * frameTime);
        speedModulator += (targetSpeedModulator - speedModulator) * blend;
        return speedModulator;
    }

    private AimAngles quantizeToMouseStep(AimAngles current, AimAngles target, boolean shortestYawPath, double sensitivity) {
        double mouseStep = mouseStepDegrees(sensitivity);
        if (mouseStep <= MIN_MOUSE_STEP) {
            return target;
        }

        double yawMove = yawDelta(current.yaw(), target.yaw(), shortestYawPath);
        double pitchMove = target.pitch() - current.pitch();
        double quantizedYawMove = quantizedMove(yawMove, yawMoveRemainder, mouseStep);
        double quantizedPitchMove = quantizedMove(pitchMove, pitchMoveRemainder, mouseStep);
        yawMoveRemainder += yawMove - quantizedYawMove;
        pitchMoveRemainder += pitchMove - quantizedPitchMove;
        yawMoveRemainder = AimMath.clamp(yawMoveRemainder, -mouseStep, mouseStep);
        pitchMoveRemainder = AimMath.clamp(pitchMoveRemainder, -mouseStep, mouseStep);

        return new AimAngles((float) (current.yaw() + quantizedYawMove), (float) (current.pitch() + quantizedPitchMove)).clampedPitch();
    }

    private double quantizedMove(double move, double remainder, double mouseStep) {
        double accumulatedMove = move + remainder;
        return Math.round(accumulatedMove / mouseStep) * mouseStep;
    }

    private double mouseStepDegrees(double sensitivity) {
        if (!Double.isFinite(sensitivity)) {
            return 0.0D;
        }
        double factor = AimMath.clamp(sensitivity, 0.0D, 1.0D) * 0.6D + 0.2D;
        return factor * factor * factor * 8.0D * MOUSE_STEP_SCALE;
    }

    private AimAngles smoothFrameTarget(AimAngles target, double frameTime, double response, boolean shortestYawPath) {
        if (frameTargetAngles == null) {
            frameTargetAngles = target;
            return target;
        }

        double yawDelta = yawDelta(frameTargetAngles.yaw(), target.yaw(), shortestYawPath);
        double pitchDelta = target.pitch() - frameTargetAngles.pitch();
        if (Math.hypot(yawDelta, pitchDelta) <= MIN_VISIBLE_DELTA) {
            frameTargetAngles = target;
            return target;
        }

        double blend = 1.0D - Math.exp(-response * frameTime);
        frameTargetAngles = new AimAngles(
                (float) (frameTargetAngles.yaw() + yawDelta * blend),
                (float) (frameTargetAngles.pitch() + pitchDelta * blend)
        ).clampedPitch();
        return frameTargetAngles;
    }

    private double response(AimSmoothingProfile profile, double totalDelta) {
        return switch (profile.mode()) {
            case Instant -> Double.POSITIVE_INFINITY;
            case Linear -> Math.max(7.0D, profile.maxYawStep() * 1.4D);
            case EaseOut -> 7.0D + profile.speed() * 7.5D + AimMath.clamp(totalDelta / 90.0D, 0.0D, 1.0D) * 2.0D;
            case Humanized -> 7.0D + profile.speed() * 9.0D + AimMath.clamp(totalDelta / 120.0D, 0.0D, 1.0D) * 3.0D;
        };
    }

    private double frameTimeSeconds() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return MIN_FRAME_TIME;
        }
        double frameTime = (now - lastFrameNanos) * NANOS_TO_SECONDS;
        lastFrameNanos = now;
        return AimMath.clamp(frameTime, MIN_FRAME_TIME, MAX_FRAME_TIME);
    }

    private AimAngles targetAngles(AimAngles current, AimAngles target, boolean shortestYawPath) {
        if (!shortestYawPath) {
            return target.clampedPitch();
        }
        return new AimAngles((float) (current.yaw() + AimMath.wrapDegrees(target.yaw() - current.yaw())), target.pitch()).clampedPitch();
    }

    private double yawDelta(float currentYaw, float targetYaw, boolean shortestYawPath) {
        double delta = targetYaw - currentYaw;
        return shortestYawPath ? AimMath.wrapDegrees(delta) : delta;
    }

    public record AimRuntime(boolean available, AimAngles currentAngles, double mouseSensitivity, RotationSink rotationSink) {
        public AimRuntime {
            currentAngles = currentAngles == null ? new AimAngles(0.0F, 0.0F) : currentAngles;
            rotationSink = Objects.requireNonNull(rotationSink, "rotationSink");
        }
    }

    @FunctionalInterface
    public interface RotationSink {
        boolean apply(AimAngles angles);
    }
}
