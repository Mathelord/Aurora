package dev.aurora.minecraft;

import dev.aurora.aim.Vec3;

public record AimTarget(
        String id,
        String displayName,
        Vec3 targetPoint,
        double distanceSquared,
        double yaw,
        double pitch,
        double health
) {
}
