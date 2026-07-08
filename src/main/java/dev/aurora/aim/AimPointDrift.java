package dev.aurora.aim;

import java.util.concurrent.ThreadLocalRandom;

/** Smooth, target-sticky aim-point movement used to avoid tracking one exact body coordinate. */
public final class AimPointDrift {
    private final double horizontalRange;
    private final double verticalRange;
    private final double response;
    private final long minIntervalMillis;
    private final long maxIntervalMillis;

    private String targetId;
    private long nextUpdateAt;
    private Vec3 current = Vec3.ZERO;
    private Vec3 desired = Vec3.ZERO;

    public AimPointDrift(double horizontalRange, double verticalRange, double response,
                         long minIntervalMillis, long maxIntervalMillis) {
        this.horizontalRange = Math.max(0.0D, horizontalRange);
        this.verticalRange = Math.max(0.0D, verticalRange);
        this.response = clamp(response, 0.0D, 1.0D);
        this.minIntervalMillis = Math.max(1L, minIntervalMillis);
        this.maxIntervalMillis = Math.max(this.minIntervalMillis, maxIntervalMillis);
    }

    public Vec3 apply(String id, Vec3 targetPoint, Vec3 observerPoint, double width, double height) {
        if (id == null || targetPoint == null || observerPoint == null) {
            return targetPoint;
        }

        long now = System.currentTimeMillis();
        if (!id.equals(targetId)) {
            targetId = id;
            current = Vec3.ZERO;
            chooseDesired(targetPoint, observerPoint, width, height, now);
        } else if (now >= nextUpdateAt) {
            chooseDesired(targetPoint, observerPoint, width, height, now);
        }

        current = current.add(desired.subtract(current).multiply(response));
        return targetPoint.add(current);
    }

    public void reset() {
        targetId = null;
        nextUpdateAt = 0L;
        current = Vec3.ZERO;
        desired = Vec3.ZERO;
    }

    private void chooseDesired(Vec3 targetPoint, Vec3 observerPoint, double width, double height, long now) {
        Vec3 direction = targetPoint.subtract(observerPoint);
        double horizontalLength = Math.hypot(direction.x(), direction.z());
        Vec3 right = horizontalLength <= 1.0e-9D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(-direction.z() / horizontalLength, 0.0D, direction.x() / horizontalLength);
        double widthScale = clamp(width / 0.6D, 0.55D, 1.35D);
        double heightScale = clamp(height / 1.8D, 0.6D, 1.25D);
        desired = right.multiply(randomCentered(horizontalRange * widthScale))
                .add(0.0D, randomCentered(verticalRange * heightScale), 0.0D);
        nextUpdateAt = now + ThreadLocalRandom.current().nextLong(minIntervalMillis, maxIntervalMillis + 1L);
    }

    private static double randomCentered(double range) {
        return ThreadLocalRandom.current().nextDouble(-range, range);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
