package dev.aurora.aim;

/** Eclipse-style adaptive aim acceleration while the player moves sideways across the aim line. */
public final class StrafeAimAssist {
    private static final double MIN_SPEED = 0.03D;
    private static final double REFERENCE_SPEED = 0.215D;

    private StrafeAimAssist() {
    }

    public static Result plan(Vec3 movement, AimAngles current, AimAngles target, double boost,
                              double baseResponse, double strafeResponse) {
        if (movement == null || current == null || target == null) return new Result(baseResponse, 1.0D);
        double speed = Math.hypot(movement.x(), movement.z());
        if (speed < MIN_SPEED) return new Result(baseResponse, 1.0D);
        double moveX = movement.x() / speed;
        double moveZ = movement.z() / speed;
        double targetYaw = Math.toRadians(target.yaw());
        double aimX = -Math.sin(targetYaw);
        double aimZ = Math.cos(targetYaw);
        double alignment = Math.abs(clamp(moveX * aimX + moveZ * aimZ, -1.0D, 1.0D));
        double strafe = Math.sqrt(Math.max(0.0D, 1.0D - alignment * alignment));
        double movementFactor = clamp(speed / REFERENCE_SPEED, 0.0D, 1.0D);
        double yawDelta = Math.abs(wrap(target.yaw() - current.yaw()));
        double catchup = clamp((yawDelta / 60.0D) * movementFactor, 0.0D, 1.0D);
        double boostFactor = clamp(strafe * movementFactor * (1.0D + clamp(yawDelta / 45.0D, 0.0D, 1.0D)), 0.0D, 5.0D);
        return new Result(baseResponse + (Math.max(baseResponse, strafeResponse) - baseResponse) * catchup,
                1.0D + Math.max(0.0D, boost) * boostFactor);
    }

    private static double wrap(double value) {
        double wrapped = value % 360.0D;
        if (wrapped >= 180.0D) wrapped -= 360.0D;
        if (wrapped < -180.0D) wrapped += 360.0D;
        return wrapped;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Result(double targetResponse, double rotationMultiplier) { }
}
