package dev.aurora.aim;

public final class DecoupledMovementSteering {
    private static final double DIGITAL_DIRECTION_THRESHOLD = 0.38268343D;

    private DecoupledMovementSteering() {
    }

    public static Keys steer(Keys input, double rotationDegrees) {
        if (input == null || Math.abs(rotationDegrees) <= 0.001D) {
            return input;
        }
        double sideways = axis(input.left(), input.right());
        double forward = axis(input.forward(), input.backward());
        if (sideways == 0.0D && forward == 0.0D) {
            return input;
        }
        double radians = Math.toRadians(rotationDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double steeredSideways = sideways * cos - forward * sin;
        double steeredForward = forward * cos + sideways * sin;
        return new Keys(
                steeredForward > DIGITAL_DIRECTION_THRESHOLD,
                steeredForward < -DIGITAL_DIRECTION_THRESHOLD,
                steeredSideways > DIGITAL_DIRECTION_THRESHOLD,
                steeredSideways < -DIGITAL_DIRECTION_THRESHOLD,
                input.jump(),
                input.sneak(),
                input.sprint()
        );
    }

    private static double axis(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0D;
        }
        return positive ? 1.0D : -1.0D;
    }

    public record Keys(boolean forward, boolean backward, boolean left, boolean right,
                       boolean jump, boolean sneak, boolean sprint) {
    }
}
