package dev.aurora.aim;

public final class AimMath {
    private AimMath() {
    }

    public static AimAngles anglesTo(Vec3 from, double eyeHeight, Vec3 point) {
        if (from == null || point == null) {
            return new AimAngles(0.0F, 0.0F);
        }
        double deltaX = point.x() - from.x();
        double deltaZ = point.z() - from.z();
        double deltaY = point.y() - (from.y() + eyeHeight);
        float yaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D);
        double horizontal = Math.hypot(deltaX, deltaZ);
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontal));
        return new AimAngles(yaw, pitch).clamped();
    }

    public static Vec3 worldMovement(double sideways, double forward, float yaw) {
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(
                sideways * cos - forward * sin,
                0.0D,
                forward * cos + sideways * sin
        );
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }
        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }
}
