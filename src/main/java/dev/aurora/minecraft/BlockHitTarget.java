package dev.aurora.minecraft;

import dev.aurora.aim.Vec3;

public record BlockHitTarget(int blockX, int blockY, int blockZ, BlockFace face, Vec3 hitPoint) {
}
