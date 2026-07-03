package dev.aurora.aim;

public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);

    public Vec3 add(double x, double y, double z) {
        return new Vec3(this.x + x, this.y + y, this.z + z);
    }

    public Vec3 add(Vec3 other) {
        return add(other.x, other.y, other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 multiply(double scale) {
        return new Vec3(x * scale, y * scale, z * scale);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double squaredDistanceTo(Vec3 other) {
        double deltaX = x - other.x;
        double deltaY = y - other.y;
        double deltaZ = z - other.z;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    public Vec3 normalize() {
        double length = length();
        return length <= 1.0e-9 ? ZERO : multiply(1.0D / length);
    }
}
