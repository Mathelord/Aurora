package dev.aurora.render;

import dev.aurora.aim.Vec3;
import dev.aurora.minecraft.CameraPose;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Collects world-space primitives and submits them to Minecraft in one render-layer batch. */
public final class WorldGeometryBatch {
    private static final int[][] BOX_FACES = {
            {0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {3, 7, 6, 2}, {0, 4, 7, 3}, {1, 2, 6, 5}
    };
    private static final int[][] BOX_EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private final Object matrixStack;
    private final Object vertexConsumers;
    private final CameraPose camera;
    private final List<Quad> quads = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<Quad> wallhackQuads = new ArrayList<>();

    public WorldGeometryBatch(Object matrixStack, Object vertexConsumers, CameraPose camera) {
        this.matrixStack = matrixStack;
        this.vertexConsumers = vertexConsumers;
        this.camera = camera;
    }

    public static void setDiagnosticSink(Consumer<String> sink) {
        ReflectionWorldGeometry.setDiagnosticSink(sink);
    }

    public static String lastError() {
        return ReflectionWorldGeometry.lastError();
    }

    public void box(Vec3 min, Vec3 max, int fillColor, int lineColor) {
        if (!available() || min == null || max == null) return;
        Vec3[] c = boxCorners(min, max);
        for (int[] face : BOX_FACES) quad(c[face[0]], c[face[1]], c[face[2]], c[face[3]], fillColor);
        for (int[] edge : BOX_EDGES) line(c[edge[0]], c[edge[1]], lineColor);
    }

    /** Like {@link #box}, but the fill is drawn ignoring the depth buffer so it stays visible behind
     * walls (ESP-style); the outline still respects depth and only shows with a clear line of sight. */
    public void espBox(Vec3 min, Vec3 max, int fillColor, int lineColor) {
        if (!available() || min == null || max == null) return;
        Vec3[] c = boxCorners(min, max);
        for (int[] face : BOX_FACES) {
            wallhackQuads.add(new Quad(c[face[0]], c[face[1]], c[face[2]], c[face[3]], fillColor));
        }
        for (int[] edge : BOX_EDGES) line(c[edge[0]], c[edge[1]], lineColor);
    }

    private static Vec3[] boxCorners(Vec3 min, Vec3 max) {
        return new Vec3[] {
                new Vec3(min.x(), min.y(), min.z()), new Vec3(max.x(), min.y(), min.z()),
                new Vec3(max.x(), max.y(), min.z()), new Vec3(min.x(), max.y(), min.z()),
                new Vec3(min.x(), min.y(), max.z()), new Vec3(max.x(), min.y(), max.z()),
                new Vec3(max.x(), max.y(), max.z()), new Vec3(min.x(), max.y(), max.z())
        };
    }

    public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        quad(a, b, c, d, color, color, color, color);
    }

    public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                     int colorA, int colorB, int colorC, int colorD) {
        if (available() && a != null && b != null && c != null && d != null) {
            quads.add(new Quad(a, b, c, d, colorA, colorB, colorC, colorD));
        }
    }

    public void line(Vec3 from, Vec3 to, int color) {
        line(from, to, color, color);
    }

    public void line(Vec3 from, Vec3 to, int fromColor, int toColor) {
        if (available() && from != null && to != null) {
            lines.add(new Line(from, to, fromColor, toColor));
        }
    }

    public int quadCount() {
        return quads.size();
    }

    public int lineCount() {
        return lines.size();
    }

    public boolean flush() {
        boolean ok = true;
        if (!quads.isEmpty() || !lines.isEmpty()) {
            ok = ReflectionWorldGeometry.draw(matrixStack, vertexConsumers, camera, quads, lines);
        }
        if (!wallhackQuads.isEmpty()) {
            ok = ReflectionWorldGeometry.drawThroughWalls(matrixStack, vertexConsumers, camera, wallhackQuads) && ok;
        }
        return ok;
    }

    private boolean available() {
        return camera != null && camera.available();
    }

    record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                int colorA, int colorB, int colorC, int colorD) {
        Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
            this(a, b, c, d, color, color, color, color);
        }
    }

    record Line(Vec3 from, Vec3 to, int fromColor, int toColor) {
        Line(Vec3 from, Vec3 to, int color) {
            this(from, to, color, color);
        }
    }
}
