package dev.aurora.render;

import dev.aurora.aim.Vec3;
import dev.aurora.minecraft.TargetPose;

/**
 * Draws a configurable ring around whatever entity a targeting module is locked onto. The ring is a
 * vertical wall that fades out toward the top, with an optional filled floor cap. Colors are either a
 * fixed two-stop {@link TargetRingColorPreset} gradient or an animated rainbow that wraps around the
 * ring and cycles over time.
 *
 * <p>The instance is stateful: it animates the rainbow hue from real frame time and smooths ring
 * size changes so it grows/shrinks gently between targets of different sizes instead of snapping.
 * Aurora's {@link WorldGeometryBatch} takes one color per quad, so the wall is subdivided into
 * vertical bands (for the top fade) and per-segment quads (for the horizontal gradient).
 */
public final class TargetRingRenderer {
    private static final double TWO_PI = Math.PI * 2.0;

    private static final int SEGMENTS = 72;          // ring smoothness
    private static final int WALL_BANDS = 4;          // vertical subdivisions for the top fade
    private static final double WALL_TOP_ALPHA = 0.0; // wall opacity multiplier at the top edge
    private static final int FLOOR_BANDS = 5;
    private static final double FLOOR_ALPHA_RATIO = 0.45;

    // Size smoothing response (per second). Higher = snappier size changes.
    private static final double SIZE_RESPONSE = 11.0;
    private static final double MIN_FRAME_TIME = 1.0 / 240.0;
    private static final double MAX_FRAME_TIME = 1.0 / 20.0;

    // Rainbow.
    private static final double HUE_SPREAD = 1.0;    // how many rainbows wrap around the ring
    private static final float SATURATION = 0.85f;
    private static final float BRIGHTNESS = 1.0f;

    private String currentTargetId;
    private boolean hasCenter;
    private double renderedRadius;
    private double renderedHeight;
    private double hue;
    private long lastFrameNanos;

    /** Immutable snapshot of the ring's appearance for a single frame. */
    public record Config(boolean wall, boolean floor, boolean rainbow, TargetRingColorPreset preset,
                         double radiusPadding, double heightScale, int opacity, double rainbowSpeed) {
    }

    public void reset() {
        currentTargetId = null;
        hasCenter = false;
    }

    public void render(WorldGeometryBatch geometry, String targetId, TargetPose pose, Config config) {
        if (geometry == null || pose == null || config == null) {
            reset();
            return;
        }

        double dt = frameDelta();
        hue = (hue + config.rainbowSpeed() * dt) % 1.0;

        double targetRadius = pose.width() * 0.5 + config.radiusPadding();
        double targetHeight = pose.height() * config.heightScale();
        if (!targetId.equals(currentTargetId) || !hasCenter) {
            currentTargetId = targetId;
            renderedRadius = targetRadius;
            renderedHeight = targetHeight;
            hasCenter = true;
        } else {
            double factor = 1.0 - Math.exp(-SIZE_RESPONSE * dt);
            renderedRadius += (targetRadius - renderedRadius) * factor;
            renderedHeight += (targetHeight - renderedHeight) * factor;
        }

        if (renderedRadius <= 0.05) {
            return;
        }

        int baseAlpha = Math.max(0, Math.min(255, config.opacity()));
        double feetY = pose.feetY() + 0.02;
        if (config.floor()) {
            renderFloor(geometry, pose.feetX(), feetY, pose.feetZ(), renderedRadius,
                    (int) Math.round(baseAlpha * FLOOR_ALPHA_RATIO), config);
        }
        if (config.wall()) {
            renderWall(geometry, pose.feetX(), feetY, pose.feetZ(), renderedRadius,
                    Math.max(0.1, renderedHeight), baseAlpha, config);
        }
    }

    private void renderWall(WorldGeometryBatch geometry, double cx, double feetY, double cz,
                            double radius, double height, int baseAlpha, Config config) {
        int topAlpha = (int) Math.round(baseAlpha * WALL_TOP_ALPHA);
        for (int i = 1; i <= SEGMENTS; i++) {
            double prevT = (double) (i - 1) / SEGMENTS;
            double t = (double) i / SEGMENTS;
            double a0 = TWO_PI * prevT;
            double a1 = TWO_PI * t;
            double x0 = cx + Math.cos(a0) * radius;
            double z0 = cz + Math.sin(a0) * radius;
            double x1 = cx + Math.cos(a1) * radius;
            double z1 = cz + Math.sin(a1) * radius;
            for (int band = 0; band < WALL_BANDS; band++) {
                double yBottom = feetY + height * ((double) band / WALL_BANDS);
                double yTop = feetY + height * ((double) (band + 1) / WALL_BANDS);
                double bottomFrac = (double) band / WALL_BANDS;
                double topFrac = (double) (band + 1) / WALL_BANDS;
                int bottomAlpha = (int) Math.round(baseAlpha + (topAlpha - baseAlpha) * bottomFrac);
                int upperAlpha = (int) Math.round(baseAlpha + (topAlpha - baseAlpha) * topFrac);
                geometry.quad(
                        new Vec3(x0, yBottom, z0),
                        new Vec3(x0, yTop, z0),
                        new Vec3(x1, yTop, z1),
                        new Vec3(x1, yBottom, z1),
                        colorAt(prevT, bottomAlpha, config),
                        colorAt(prevT, upperAlpha, config),
                        colorAt(t, upperAlpha, config),
                        colorAt(t, bottomAlpha, config));
            }
            // Bright outline along the feet.
            geometry.line(new Vec3(x0, feetY, z0), new Vec3(x1, feetY, z1),
                    colorAt(prevT, baseAlpha, config), colorAt(t, baseAlpha, config));
        }
    }

    private void renderFloor(WorldGeometryBatch geometry, double cx, double y, double cz,
                             double radius, int alpha, Config config) {
        for (int band = 0; band < FLOOR_BANDS; band++) {
            double innerRadius = radius * ((double) band / FLOOR_BANDS);
            double outerRadius = radius * ((double) (band + 1) / FLOOR_BANDS);
            for (int i = 1; i <= SEGMENTS; i++) {
                double prevT = (double) (i - 1) / SEGMENTS;
                double t = (double) i / SEGMENTS;
                double a0 = TWO_PI * prevT;
                double a1 = TWO_PI * t;
                geometry.quad(
                        new Vec3(cx + Math.cos(a0) * innerRadius, y, cz + Math.sin(a0) * innerRadius),
                        new Vec3(cx + Math.cos(a0) * outerRadius, y, cz + Math.sin(a0) * outerRadius),
                        new Vec3(cx + Math.cos(a1) * outerRadius, y, cz + Math.sin(a1) * outerRadius),
                        new Vec3(cx + Math.cos(a1) * innerRadius, y, cz + Math.sin(a1) * innerRadius),
                        colorAt(prevT, alpha, config), colorAt(prevT, alpha, config),
                        colorAt(t, alpha, config), colorAt(t, alpha, config));
            }
        }
    }

    private int colorAt(double position, int alpha, Config config) {
        if (!config.rainbow()) {
            return config.preset().colorAt(position, alpha);
        }
        double h = (hue + HUE_SPREAD * position) % 1.0;
        return hsvToArgb(h, SATURATION, BRIGHTNESS, alpha);
    }

    private double frameDelta() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return MIN_FRAME_TIME;
        }
        double dt = (now - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = now;
        return Math.max(MIN_FRAME_TIME, Math.min(MAX_FRAME_TIME, dt));
    }

    private static int hsvToArgb(double hue, double saturation, double value, int alpha) {
        double h = (hue - Math.floor(hue)) * 6.0;
        double f = h - Math.floor(h);
        double p = value * (1.0 - saturation);
        double q = value * (1.0 - saturation * f);
        double t = value * (1.0 - saturation * (1.0 - f));
        double r;
        double g;
        double b;
        switch ((int) h) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return (Math.max(0, Math.min(255, alpha)) << 24)
                | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value * 255.0)));
    }
}
