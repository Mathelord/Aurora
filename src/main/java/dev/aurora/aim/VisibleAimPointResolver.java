package dev.aurora.aim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class VisibleAimPointResolver {
    private static final double NEIGHBOR_RADIUS = 0.24D;
    private static final int MIN_VISIBLE_NEIGHBORS = 2;

    private VisibleAimPointResolver() {
    }

    public static Vec3 bestVisiblePoint(Vec3 eye, TargetShape target, double range, double reachBonus, Predicate<Vec3> visiblePoint) {
        if (eye == null || target == null) {
            return null;
        }
        Predicate<Vec3> visibility = visiblePoint == null ? point -> true : visiblePoint;
        double maxDistanceSquared = Math.pow(range + reachBonus, 2.0D);
        List<Vec3> visiblePoints = candidatePoints(target).stream()
                .filter(point -> point.squaredDistanceTo(eye) <= maxDistanceSquared)
                .filter(visibility)
                .toList();

        return visiblePoints.stream()
                .filter(point -> visibleNeighborCount(point, visiblePoints) >= MIN_VISIBLE_NEIGHBORS)
                .min(Comparator.comparingDouble(point -> pointScore(point, target, visiblePoints)))
                .orElse(null);
    }

    public static List<Vec3> candidatePoints(TargetShape target) {
        List<Vec3> points = new ArrayList<>();
        double height = target.height();
        double width = target.width();
        double eyeHeight = Math.min(height, Math.max(0.0D, target.eyeHeight()));
        double bodyY = Math.min(height * 0.5D, eyeHeight * 0.5D + height * 0.15D);
        double[] heights = normalizedHeights(height, eyeHeight, bodyY);
        double side = Math.max(0.04D, width * 0.30D);
        double[][] offsets = {
                {0.0D, 0.0D},
                {-side * 0.45D, 0.0D},
                {side * 0.45D, 0.0D},
                {0.0D, -side * 0.45D},
                {0.0D, side * 0.45D},
                {-side, 0.0D},
                {side, 0.0D},
                {0.0D, -side},
                {0.0D, side},
                {-side * 0.7D, -side * 0.7D},
                {-side * 0.7D, side * 0.7D},
                {side * 0.7D, -side * 0.7D},
                {side * 0.7D, side * 0.7D}
        };

        double yaw = Math.toRadians(target.yaw());
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));

        for (double y : heights) {
            for (double[] offset : offsets) {
                points.add(target.position()
                        .add(right.multiply(offset[0]))
                        .add(forward.multiply(offset[1]))
                        .add(0.0D, y, 0.0D));
            }
        }
        return points;
    }

    private static double[] normalizedHeights(double height, double eyeHeight, double bodyY) {
        return new double[]{
                bodyY,
                height * 0.42D,
                height * 0.34D,
                height * 0.26D,
                height * 0.18D,
                Math.max(0.08D, height * 0.11D),
                Math.max(0.08D, height * 0.07D),
                Math.max(0.08D, eyeHeight - 0.06D)
        };
    }

    private static int visibleNeighborCount(Vec3 point, List<Vec3> visiblePoints) {
        int count = 0;
        double radiusSquared = NEIGHBOR_RADIUS * NEIGHBOR_RADIUS;
        for (Vec3 visiblePoint : visiblePoints) {
            if (point == visiblePoint) {
                continue;
            }
            if (point.squaredDistanceTo(visiblePoint) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    private static double pointScore(Vec3 point, TargetShape target, List<Vec3> visiblePoints) {
        double height = Math.max(target.height(), 1.0e-6D);
        double relativeY = AimMath.clamp((point.y() - target.position().y()) / height, 0.0D, 1.0D);
        double preferredBodyY = 0.48D;
        double bodyScore = Math.abs(relativeY - preferredBodyY);
        double centerScore = horizontalCenterDistance(point, target) / Math.max(target.width(), 1.0e-6D);
        double clusterBonus = Math.min(visibleNeighborCount(point, visiblePoints), 8) * 0.08D;
        return bodyScore + centerScore * 0.35D - clusterBonus;
    }

    private static double horizontalCenterDistance(Vec3 point, TargetShape target) {
        return Math.hypot(point.x() - target.position().x(), point.z() - target.position().z());
    }

    public record TargetShape(Vec3 position, double width, double height, double eyeHeight, double yaw) {
        public TargetShape {
            position = position == null ? Vec3.ZERO : position;
            width = Math.max(0.01D, width);
            height = Math.max(0.01D, height);
            eyeHeight = AimMath.clamp(eyeHeight, 0.0D, height);
        }
    }
}
