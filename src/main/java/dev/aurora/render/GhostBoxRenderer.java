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
        if (geometry == null || position == null) return;
        Vec3 min = new Vec3(position.x() - HALF_WIDTH, position.y(), position.z() - HALF_WIDTH);
        Vec3 max = new Vec3(position.x() + HALF_WIDTH, position.y() + HEIGHT, position.z() + HALF_WIDTH);
        geometry.box(min, max, withAlpha(colorRgb, FILL_ALPHA), withAlpha(colorRgb, BORDER_ALPHA));
    }

    private static int withAlpha(int colorRgb, int alpha) {
        return (alpha << 24) | (colorRgb & 0x00FFFFFF);
    }
}
