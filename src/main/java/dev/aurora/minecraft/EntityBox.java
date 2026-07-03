package dev.aurora.minecraft;

import dev.aurora.aim.Vec3;

/** The world-space axis-aligned bounding box of an entity, tagged with the same id the aim/ESP
 * pipeline uses for it, so callers can cross-reference the two. */
public record EntityBox(String id, Vec3 min, Vec3 max) {
    /** Whether {@code point} lies within this box grown outward by {@code padding} on every side. */
    public boolean contains(Vec3 point, double padding) {
        return point.x() >= min.x() - padding && point.x() <= max.x() + padding
                && point.y() >= min.y() - padding && point.y() <= max.y() + padding
                && point.z() >= min.z() - padding && point.z() <= max.z() + padding;
    }
}
