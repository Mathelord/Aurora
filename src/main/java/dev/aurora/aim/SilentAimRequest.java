package dev.aurora.aim;

public record SilentAimRequest(
        String owner,
        int priority,
        AimAngles targetAngles,
        Vec3 targetPoint,
        AimSmoothingProfile smoothingProfile,
        double targetResponse,
        double rotationMultiplier,
        boolean shortestYawPath,
        AimDebugMode debugMode,
        Vec3 debugMovement,
        double debugStrength,
        boolean decoupled,
        boolean globalDecoupledAllowed
) {
    public SilentAimRequest {
        owner = owner == null || owner.isBlank() ? "unknown" : owner;
        targetAngles = targetAngles == null
                ? new AimAngles(0.0F, 0.0F)
                : shortestYawPath ? targetAngles.clamped() : targetAngles.clampedPitch();
        smoothingProfile = smoothingProfile == null ? AimSmoothingProfile.instant() : smoothingProfile.sanitized();
        targetResponse = Math.max(0.0D, targetResponse);
        rotationMultiplier = Math.max(0.0D, rotationMultiplier);
        debugMode = debugMode == null ? AimDebugMode.Off : debugMode;
        debugMovement = debugMovement == null ? Vec3.ZERO : debugMovement;
        debugStrength = AimMath.clamp(debugStrength, 0.0D, 1.0D);
    }

    public static Builder builder(String owner, AimAngles targetAngles) {
        return new Builder(owner, targetAngles);
    }

    public static final class Builder {
        private final String owner;
        private final AimAngles targetAngles;
        private int priority;
        private Vec3 targetPoint;
        private AimSmoothingProfile smoothingProfile = AimSmoothingProfile.instant();
        private double targetResponse = 18.0D;
        private double rotationMultiplier = 1.0D;
        private boolean shortestYawPath = true;
        private AimDebugMode debugMode = AimDebugMode.Off;
        private Vec3 debugMovement = Vec3.ZERO;
        private double debugStrength;
        private boolean decoupled;
        private boolean globalDecoupledAllowed = true;

        private Builder(String owner, AimAngles targetAngles) {
            this.owner = owner;
            this.targetAngles = targetAngles;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder targetPoint(Vec3 targetPoint) {
            this.targetPoint = targetPoint;
            return this;
        }

        public Builder smoothingProfile(AimSmoothingProfile smoothingProfile) {
            this.smoothingProfile = smoothingProfile;
            return this;
        }

        public Builder targetResponse(double targetResponse) {
            this.targetResponse = targetResponse;
            return this;
        }

        public Builder rotationMultiplier(double rotationMultiplier) {
            this.rotationMultiplier = rotationMultiplier;
            return this;
        }

        public Builder shortestYawPath(boolean shortestYawPath) {
            this.shortestYawPath = shortestYawPath;
            return this;
        }

        public Builder debug(AimDebugMode mode, Vec3 movement, double strength) {
            this.debugMode = mode;
            this.debugMovement = movement;
            this.debugStrength = strength;
            return this;
        }

        public Builder decoupled(boolean decoupled) {
            this.decoupled = decoupled;
            return this;
        }

        public Builder globalDecoupledAllowed(boolean globalDecoupledAllowed) {
            this.globalDecoupledAllowed = globalDecoupledAllowed;
            return this;
        }

        public SilentAimRequest build() {
            return new SilentAimRequest(
                    owner,
                    priority,
                    targetAngles,
                    targetPoint,
                    smoothingProfile,
                    targetResponse,
                    rotationMultiplier,
                    shortestYawPath,
                    debugMode,
                    debugMovement,
                    debugStrength,
                    decoupled,
                    globalDecoupledAllowed
            );
        }
    }
}
