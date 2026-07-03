package dev.aurora.aim;

public record AimSmoothingProfile(
        SmoothingMode mode,
        double speed,
        double maxYawStep,
        double maxPitchStep,
        double snapThreshold,
        double jitter
) {
    public static AimSmoothingProfile instant() {
        return new AimSmoothingProfile(SmoothingMode.Instant, 1.0D, 180.0D, 90.0D, 180.0D, 0.0D);
    }

    public static AimSmoothingProfile fastHuman() {
        return new AimSmoothingProfile(SmoothingMode.Humanized, 0.78D, 74.0D, 58.0D, 0.0D, 0.45D);
    }

    public static AimSmoothingProfile clutchHuman() {
        return new AimSmoothingProfile(SmoothingMode.Humanized, 0.86D, 96.0D, 76.0D, 0.0D, 0.18D);
    }

    public AimSmoothingProfile sanitized() {
        return new AimSmoothingProfile(
                mode == null ? SmoothingMode.Instant : mode,
                AimMath.clamp(speed, 0.05D, 1.0D),
                AimMath.clamp(maxYawStep, 1.0D, 180.0D),
                AimMath.clamp(maxPitchStep, 1.0D, 90.0D),
                AimMath.clamp(snapThreshold, 0.0D, 30.0D),
                AimMath.clamp(jitter, 0.0D, 2.5D)
        );
    }
}
