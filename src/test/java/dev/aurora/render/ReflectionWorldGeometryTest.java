package dev.aurora.render;

import dev.aurora.aim.Vec3;
import dev.aurora.minecraft.CameraPose;
import net.minecraft.class_1921;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionWorldGeometryTest {
    @Test
    void drawsQuadsThroughMinecraft1214IntermediaryMethods() {
        FakeMatrices matrices = new FakeMatrices();
        FakeProviders providers = new FakeProviders();
        CameraPose camera = new CameraPose(true, Vec3.ZERO, 0.0F, 0.0F, 70.0, 1920, 1080);
        WorldGeometryBatch.Quad quad = new WorldGeometryBatch.Quad(
                new Vec3(0, 0, 0), new Vec3(1, 0, 0),
                new Vec3(1, 1, 0), new Vec3(0, 1, 0), 0x804080C0);

        boolean rendered = ReflectionWorldGeometry.draw(
                matrices, providers, camera, List.of(quad), List.of());

        assertTrue(rendered);
        assertEquals(4, providers.consumer.vertices);
        assertEquals(4, providers.consumer.colors);
        assertEquals(1, providers.flushes);
    }

    @Test
    void preservesPerVertexColorsAndFlushesOnlyTheOwnedLayer() {
        FakeMatrices matrices = new FakeMatrices();
        FakeProviders providers = new FakeProviders();
        CameraPose camera = new CameraPose(true, Vec3.ZERO, 0.0F, 0.0F, 70.0, 1920, 1080);
        WorldGeometryBatch.Quad quad = new WorldGeometryBatch.Quad(
                new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(1, 1, 0), new Vec3(0, 1, 0),
                0x10203040, 0x50607080, 0x90A0B0C0, 0xD0E0F000);

        assertTrue(ReflectionWorldGeometry.draw(matrices, providers, camera, List.of(quad), List.of()));

        assertEquals(List.of(0x10203040, 0x50607080, 0x90A0B0C0, 0xD0E0F000), providers.consumer.argb);
        assertEquals(1, providers.flushes);
    }

    @Test
    void drawsWithMinecraft12111RenderLayersAndRawVertexMethods() {
        FakeMatrices matrices = new FakeMatrices();
        ModernProviders providers = new ModernProviders();
        CameraPose camera = new CameraPose(true, new Vec3(10, 20, 30), 0.0F, 0.0F, 70.0, 1920, 1080);
        WorldGeometryBatch.Line line = new WorldGeometryBatch.Line(
                new Vec3(11, 22, 33), new Vec3(14, 26, 38), 0xFF112233, 0xFF445566);

        assertTrue(ReflectionWorldGeometry.draw(matrices, providers, camera, List.of(), List.of(line)));

        assertEquals(List.of(new Vec3(1, 2, 3), new Vec3(4, 6, 8)), providers.consumer.vertices);
        assertEquals(2, providers.consumer.normals);
        assertEquals(1, providers.flushes);
    }

    public static final class FakeMatrices {
        private final FakeEntry entry = new FakeEntry();

        public FakeEntry method_23760() {
            return entry;
        }
    }

    public static final class FakeEntry {
    }

    public static final class FakeProviders {
        private final FakeConsumer consumer = new FakeConsumer();
        private int flushes;

        public FakeConsumer getBuffer(class_1921 ignored) {
            return consumer;
        }

        public void method_22994(class_1921 ignored) {
            flushes++;
        }
    }

    public static final class FakeConsumer {
        private int vertices;
        private int colors;
        private final List<Integer> argb = new ArrayList<>();

        public FakeConsumer method_56824(FakeEntry ignored, float x, float y, float z) {
            vertices++;
            return this;
        }

        public FakeConsumer method_1336(int red, int green, int blue, int alpha) {
            colors++;
            argb.add((alpha << 24) | (red << 16) | (green << 8) | blue);
            return this;
        }

        public FakeConsumer method_60831(FakeEntry ignored, float x, float y, float z) {
            return this;
        }
    }

    public static final class ModernProviders {
        private final ModernConsumer consumer = new ModernConsumer();
        private int flushes;

        public ModernConsumer method_73477(class_1921 ignored) {
            return consumer;
        }

        public void method_22994(class_1921 ignored) {
            flushes++;
        }
    }

    public static final class ModernConsumer {
        private final List<Vec3> vertices = new ArrayList<>();
        private int normals;

        public ModernConsumer method_22912(float x, float y, float z) {
            vertices.add(new Vec3(x, y, z));
            return this;
        }

        public ModernConsumer method_1336(int red, int green, int blue, int alpha) {
            return this;
        }

        public ModernConsumer method_22914(float x, float y, float z) {
            normals++;
            return this;
        }
    }
}
