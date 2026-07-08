package dev.aurora.render;

import dev.aurora.aim.Vec3;

/** Draws a player-sized box at a fixed world position — the "ghost" of where an entity was last
 * known to the server, used by BackTrack and Blink. */
public final class GhostBoxRenderer {
    private static final double HALF_WIDTH = 0.3D;
    private static final double HEIGHT = 1.8D;
    private static final int FILL_ALPHA = 0x50;
    private static final int BORDER_ALPHA = 0xE0;

    private GhostBoxRenderer() {
    }

    public static void render(WorldGeometryBatch geometry, Vec3 position, int colorRgb) {
        render(geometry, position, HALF_WIDTH * 2.0D, HEIGHT, colorRgb);
    }

    public static void render(WorldGeometryBatch geometry, Vec3 position,
                              double width, double height, int colorRgb) {
        if (geometry == null || position == null) return;
        double halfWidth = Math.max(0.0D, width) / 2.0D;
        Vec3 min = new Vec3(position.x() - halfWidth, position.y(), position.z() - halfWidth);
        Vec3 max = new Vec3(position.x() + halfWidth, position.y() + Math.max(0.0D, height),
                position.z() + halfWidth);
        geometry.box(min, max, withAlpha(colorRgb, FILL_ALPHA), withAlpha(colorRgb, BORDER_ALPHA));
    }

    private static int withAlpha(int colorRgb, int alpha) {
        return (alpha << 24) | (colorRgb & 0x00FFFFFF);
    }
}
