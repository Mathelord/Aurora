package dev.aurora.aim;

public record AimAngles(float yaw, float pitch) {
    public AimAngles clamped() {
        return new AimAngles((float) AimMath.wrapDegrees(yaw), (float) AimMath.clamp(pitch, -90.0D, 90.0D));
    }

    public AimAngles clampedPitch() {
        return new AimAngles(yaw, (float) AimMath.clamp(pitch, -90.0D, 90.0D));
    }

    public Vec3 direction() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double pitchCos = Math.cos(pitchRad);
        return new Vec3(
                -Math.sin(yawRad) * pitchCos,
                -Math.sin(pitchRad),
                Math.cos(yawRad) * pitchCos
        );
    }
}
