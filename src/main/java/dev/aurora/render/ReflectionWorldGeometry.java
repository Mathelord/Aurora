package dev.aurora.render;

import dev.aurora.aim.Vec3;
import dev.aurora.minecraft.CameraPose;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Reflection adapter for Minecraft 1.21.4's world-space vertex/render-layer APIs. */
final class ReflectionWorldGeometry {
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static volatile Consumer<String> diagnosticSink = message -> System.err.println("[Aurora] " + message);
    private static volatile String lastError = "";
    private static final List<String> RENDER_LAYER_CLASSES = List.of(
            "net.minecraft.client.render.RenderLayer", "net.minecraft.class_1921", "gmj"
    );
    private static final List<String> RENDER_PHASE_CLASSES = List.of(
            "net.minecraft.client.render.RenderPhase", "net.minecraft.class_4668"
    );
    private static final List<String> VERTEX_FORMATS_CLASSES = List.of(
            "net.minecraft.client.render.VertexFormats", "net.minecraft.class_290"
    );
    private static final List<String> VERTEX_FORMAT_CLASSES = List.of(
            "net.minecraft.client.render.VertexFormat", "net.minecraft.class_293"
    );
    private static final List<String> DRAW_MODE_CLASSES = List.of(
            "net.minecraft.client.render.VertexFormat$DrawMode", "net.minecraft.class_293$class_5596"
    );
    private static final List<String> MULTI_PHASE_PARAMETERS_CLASSES = List.of(
            "net.minecraft.client.render.RenderLayer$MultiPhaseParameters", "net.minecraft.class_1921$class_4688"
    );
    private static final List<String> MULTI_PHASE_PARAMETERS_BUILDER_CLASSES = List.of(
            "net.minecraft.client.render.RenderLayer$MultiPhaseParameters$Builder",
            "net.minecraft.class_1921$class_4688$class_4689"
    );

    private static volatile Object noDepthQuadLayer;
    private static volatile boolean noDepthQuadLayerAttempted;

    private ReflectionWorldGeometry() {
    }

    static void setDiagnosticSink(Consumer<String> sink) {
        diagnosticSink = sink == null ? message -> System.err.println("[Aurora] " + message) : sink;
    }

    static String lastError() {
        return lastError;
    }

    static boolean draw(Object matrices, Object providers, CameraPose camera,
                        List<WorldGeometryBatch.Quad> quads, List<WorldGeometryBatch.Line> lines) {
        if (matrices == null || providers == null || camera == null || !camera.available()) return false;
        try {
            Object entry = invokeNoArgs(matrices, List.of("peek", "method_23760", "c"));
            ClassLoader loader = matrices.getClass().getClassLoader();
            Class<?> renderLayerClass = loadAny(loader, RENDER_LAYER_CLASSES);
            Method getBuffer = method(providers.getClass(), List.of("getBuffer", "method_24145"), 1,
                    candidate -> candidate.getParameterTypes()[0].isAssignableFrom(renderLayerClass));

            if (!quads.isEmpty()) {
                Object fillLayer = invokeStaticNoArgs(renderLayerClass,
                        List.of("getDebugQuads", "method_49042", "C"));
                Object consumer = getBuffer.invoke(providers, fillLayer);
                VertexMethods vertices = VertexMethods.forConsumer(consumer, entry);
                for (WorldGeometryBatch.Quad quad : quads) {
                    vertices.quad(entry, consumer, relative(quad.a(), camera), relative(quad.b(), camera),
                            relative(quad.c(), camera), relative(quad.d(), camera),
                            quad.colorA(), quad.colorB(), quad.colorC(), quad.colorD());
                }
                invokeOneArg(providers, List.of("draw", "method_22994", "a"), fillLayer);
            }

            if (!lines.isEmpty()) {
                Object lineLayer = invokeStaticNoArgs(renderLayerClass, List.of("getLines", "method_23594", "y"));
                Object consumer = getBuffer.invoke(providers, lineLayer);
                VertexMethods vertices = VertexMethods.forConsumer(consumer, entry);
                for (WorldGeometryBatch.Line line : lines) {
                    vertices.line(entry, consumer, relative(line.from(), camera), relative(line.to(), camera),
                            line.fromColor(), line.toColor());
                }
                invokeOneArg(providers, List.of("draw", "method_22994", "a"), lineLayer);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            lastError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                diagnosticSink.accept("3D world renderer failed: " + lastError);
            }
            return false;
        }
    }

    /** Draws quads with depth testing disabled, so they stay visible through walls (ESP-style). Falls
     * back to the normal depth-tested debug-quads layer if the no-depth render layer can't be built
     * (e.g. an unexpected mapping), so this never renders worse than {@link #draw}. */
    static boolean drawThroughWalls(Object matrices, Object providers, CameraPose camera,
                                    List<WorldGeometryBatch.Quad> quads) {
        if (matrices == null || providers == null || camera == null || !camera.available() || quads.isEmpty()) {
            return quads.isEmpty();
        }
        try {
            Object entry = invokeNoArgs(matrices, List.of("peek", "method_23760", "c"));
            ClassLoader loader = matrices.getClass().getClassLoader();
            Class<?> renderLayerClass = loadAny(loader, RENDER_LAYER_CLASSES);
            Object noDepthLayer = noDepthQuadLayer(loader);
            Object layer = noDepthLayer != null ? noDepthLayer
                    : invokeStaticNoArgs(renderLayerClass, List.of("getDebugQuads", "method_49042", "C"));
            Method getBuffer = method(providers.getClass(), List.of("getBuffer", "method_24145"), 1,
                    candidate -> candidate.getParameterTypes()[0].isInstance(layer));
            Object consumer = getBuffer.invoke(providers, layer);
            VertexMethods vertices = VertexMethods.forConsumer(consumer, entry);
            for (WorldGeometryBatch.Quad quad : quads) {
                vertices.quad(entry, consumer, relative(quad.a(), camera), relative(quad.b(), camera),
                        relative(quad.c(), camera), relative(quad.d(), camera),
                        quad.colorA(), quad.colorB(), quad.colorC(), quad.colorD());
            }
            invokeOneArg(providers, List.of("draw", "method_22994", "a"), layer);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            lastError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                diagnosticSink.accept("3D world renderer failed: " + lastError);
            }
            return false;
        }
    }

    private static Object noDepthQuadLayer(ClassLoader loader) {
        if (noDepthQuadLayer != null || noDepthQuadLayerAttempted) {
            return noDepthQuadLayer;
        }
        synchronized (ReflectionWorldGeometry.class) {
            if (noDepthQuadLayer != null || noDepthQuadLayerAttempted) {
                return noDepthQuadLayer;
            }
            noDepthQuadLayerAttempted = true;
            try {
                noDepthQuadLayer = buildNoDepthQuadLayer(loader);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                diagnosticSink.accept("ESP through-walls render layer unavailable, falling back to normal depth "
                        + "test: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            return noDepthQuadLayer;
        }
    }

    /** Builds a custom RenderLayer for solid-color quads with depth testing disabled (GL_ALWAYS), so
     * geometry submitted through it draws on top of anything already in the frame, including terrain
     * that would normally occlude it. */
    private static Object buildNoDepthQuadLayer(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> renderLayerClass = loadAny(loader, RENDER_LAYER_CLASSES);
        Class<?> renderPhaseClass = loadAny(loader, RENDER_PHASE_CLASSES);
        Class<?> vertexFormatsClass = loadAny(loader, VERTEX_FORMATS_CLASSES);
        Class<?> vertexFormatClass = loadAny(loader, VERTEX_FORMAT_CLASSES);
        Class<?> drawModeClass = loadAny(loader, DRAW_MODE_CLASSES);
        Class<?> multiPhaseParametersClass = loadAny(loader, MULTI_PHASE_PARAMETERS_CLASSES);
        Class<?> builderClass = loadAny(loader, MULTI_PHASE_PARAMETERS_BUILDER_CLASSES);

        Object positionColorFormat = staticField(vertexFormatsClass, List.of("POSITION_COLOR", "field_1576"));
        Object quadsMode = staticField(drawModeClass, List.of("QUADS", "field_27382"));
        Object program = staticField(renderPhaseClass, List.of("POSITION_COLOR_PROGRAM", "field_29442"));
        Object transparency = staticField(renderPhaseClass, List.of("TRANSLUCENT_TRANSPARENCY", "field_21370"));
        Object depthTest = staticField(renderPhaseClass, List.of("ALWAYS_DEPTH_TEST", "field_21346"));
        Object cull = staticField(renderPhaseClass, List.of("DISABLE_CULLING", "field_21345"));
        Object writeMask = staticField(renderPhaseClass, List.of("COLOR_MASK", "field_21350"));

        Object builder = invokeStaticNoArgs(multiPhaseParametersClass, List.of("builder", "method_23598"));
        builder = invokeSetter(builder, builderClass, List.of("program", "method_34578"), program);
        builder = invokeSetter(builder, builderClass, List.of("transparency", "method_23615"), transparency);
        builder = invokeSetter(builder, builderClass, List.of("depthTest", "method_23604"), depthTest);
        builder = invokeSetter(builder, builderClass, List.of("cull", "method_23603"), cull);
        builder = invokeSetter(builder, builderClass, List.of("writeMaskState", "method_23616"), writeMask);

        Method buildMethod = method(builderClass, List.of("build", "method_23617"), 1,
                candidate -> candidate.getParameterTypes()[0] == boolean.class);
        Object parameters = buildMethod.invoke(builder, false);

        Method ofMethod = method(renderLayerClass, List.of("of", "method_24049"), 7, candidate -> {
            Class<?>[] p = candidate.getParameterTypes();
            return p[0] == String.class && vertexFormatClass.isAssignableFrom(p[1])
                    && drawModeClass.isAssignableFrom(p[2]) && p[3] == int.class
                    && p[4] == boolean.class && p[5] == boolean.class
                    && multiPhaseParametersClass.isAssignableFrom(p[6]);
        });
        return ofMethod.invoke(null, "aurora:esp_through_walls", positionColorFormat, quadsMode, 256, false, true,
                parameters);
    }

    private static Object staticField(Class<?> type, List<String> names) throws ReflectiveOperationException {
        for (String name : names) {
            try {
                return type.getField(name).get(null);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + names);
    }

    private static Object invokeSetter(Object target, Class<?> type, List<String> names, Object value)
            throws ReflectiveOperationException {
        for (Method candidate : type.getMethods()) {
            if (names.contains(candidate.getName()) && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(value)) {
                return candidate.invoke(target, value);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + names);
    }

    private static Vec3 relative(Vec3 point, CameraPose camera) {
        return point.subtract(camera.eye());
    }

    private static Class<?> loadAny(ClassLoader loader, List<String> names) throws ClassNotFoundException {
        for (String name : names) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException("Minecraft RenderLayer was not found");
    }

    private static Object invokeStaticNoArgs(Class<?> type, List<String> names) throws ReflectiveOperationException {
        Method method = method(type, names, 0, candidate -> Modifier.isStatic(candidate.getModifiers()));
        return method.invoke(null);
    }

    private static Object invokeNoArgs(Object target, List<String> names) throws ReflectiveOperationException {
        Method method = method(target.getClass(), names, 0,
                candidate -> !Modifier.isStatic(candidate.getModifiers()) && candidate.getReturnType() != boolean.class);
        return method.invoke(target);
    }

    private static void invokeOneArg(Object target, List<String> names, Object argument)
            throws ReflectiveOperationException {
        Method method = method(target.getClass(), names, 1, candidate ->
                !Modifier.isStatic(candidate.getModifiers())
                        && candidate.getParameterTypes()[0].isInstance(argument));
        method.invoke(target, argument);
    }

    private static Method method(Class<?> type, List<String> names, int parameterCount,
                                 java.util.function.Predicate<Method> extra) throws NoSuchMethodException {
        for (Method candidate : type.getMethods()) {
            if (names.contains(candidate.getName()) && candidate.getParameterCount() == parameterCount && extra.test(candidate)) {
                candidate.setAccessible(true);
                return candidate;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + names);
    }

    private record VertexMethods(Method vertex, Method color, Method normal) {
        static VertexMethods forConsumer(Object consumer, Object entry) throws NoSuchMethodException {
            Class<?> type = consumer.getClass();
            Method vertex = method(type, List.of("vertex", "method_56824", "a"), 4, candidate -> {
                Class<?>[] p = candidate.getParameterTypes();
                return p[0].isInstance(entry) && p[1] == float.class && p[2] == float.class && p[3] == float.class;
            });
            Method color = method(type, List.of("color", "method_1336", "a"), 4, candidate -> {
                Class<?>[] p = candidate.getParameterTypes();
                return p[0] == int.class && p[1] == int.class && p[2] == int.class && p[3] == int.class;
            });
            Method normal = method(type, List.of("normal", "method_60831", "b"), 4, candidate -> {
                Class<?>[] p = candidate.getParameterTypes();
                return p[0].isInstance(entry) && p[1] == float.class && p[2] == float.class && p[3] == float.class;
            });
            return new VertexMethods(vertex, color, normal);
        }

        void quad(Object entry, Object consumer, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                  int colorA, int colorB, int colorC, int colorD)
                throws ReflectiveOperationException {
            vertex(entry, consumer, a, colorA, null);
            vertex(entry, consumer, b, colorB, null);
            vertex(entry, consumer, c, colorC, null);
            vertex(entry, consumer, d, colorD, null);
        }

        void line(Object entry, Object consumer, Vec3 from, Vec3 to, int fromColor, int toColor)
                throws ReflectiveOperationException {
            Vec3 direction = to.subtract(from).normalize();
            vertex(entry, consumer, from, fromColor, direction);
            vertex(entry, consumer, to, toColor, direction);
        }

        private void vertex(Object entry, Object consumer, Vec3 point, int argb, Vec3 direction)
                throws ReflectiveOperationException {
            vertex.invoke(consumer, entry, (float) point.x(), (float) point.y(), (float) point.z());
            color.invoke(consumer, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
            if (direction != null) {
                normal.invoke(consumer, entry, (float) direction.x(), (float) direction.y(), (float) direction.z());
            }
        }
    }
}
