package dev.aurora.minecraft;

/** Render-space pose of a targeted entity: its feet position and bounding-box dimensions. Used by
 * world renderers (e.g. the target ring) that need to draw around whatever a targeting module has
 * locked onto. */
public record TargetPose(double feetX, double feetY, double feetZ, double width, double height) {
}
