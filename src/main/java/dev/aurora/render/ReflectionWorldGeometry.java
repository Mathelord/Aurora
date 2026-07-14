package dev.aurora.render;

import dev.aurora.aim.Vec3;
import dev.aurora.minecraft.CameraPose;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Reflection bootstrap for Minecraft's world-space vertex and render-layer APIs.
 *
 * <p>Reflection is deliberately restricted to the one-time access-plan construction. Frame and
 * vertex hot paths use cached, exactly typed {@link MethodHandle MethodHandles}, avoiding the
 * argument-array allocation and primitive boxing performed by {@link Method#invoke}.</p>
 */
final class ReflectionWorldGeometry {
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static volatile Consumer<String> diagnosticSink = message -> System.err.println("[Aurora] " + message);
    private static volatile String lastError = "";

    private static final List<String> RENDER_LAYER_CLASSES = List.of(
            "net.minecraft.client.renderer.RenderType",
            "net.minecraft.client.renderer.rendertype.RenderType",
            "net.minecraft.client.render.RenderLayer", "net.minecraft.class_1921", "gmj", "ijs"
    );
    private static final List<String> RENDER_LAYERS_CLASSES = List.of(
            "net.minecraft.client.render.RenderLayers", "net.minecraft.class_12249", "ijt"
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

    private static final MethodType OBJECT_TO_OBJECT = MethodType.methodType(Object.class, Object.class);
    private static final MethodType OBJECT_OBJECT_TO_OBJECT =
            MethodType.methodType(Object.class, Object.class, Object.class);
    private static final MethodType OBJECT_OBJECT_TO_VOID =
            MethodType.methodType(void.class, Object.class, Object.class);
    private static final MethodType VERTEX_WITH_ENTRY = MethodType.methodType(
            void.class, Object.class, Object.class, float.class, float.class, float.class);
    private static final MethodType RAW_VERTEX = MethodType.methodType(
            void.class, Object.class, float.class, float.class, float.class);
    private static final MethodType PACKED_COLOR = MethodType.methodType(void.class, Object.class, int.class);
    private static final MethodType RGBA_COLOR = MethodType.methodType(
            void.class, Object.class, int.class, int.class, int.class, int.class);
    private static final MethodType LINE_WIDTH = MethodType.methodType(
            void.class, Object.class, float.class);

    private static final ClassValue<Resolution<MatrixMethods>> MATRIX_METHODS = new ClassValue<>() {
        @Override
        protected Resolution<MatrixMethods> computeValue(Class<?> type) {
            return Resolution.resolve(() -> MatrixMethods.resolve(type));
        }
    };
    private static final ClassValue<ProviderMethodsHolder> PROVIDER_METHODS = new ClassValue<>() {
        @Override
        protected ProviderMethodsHolder computeValue(Class<?> type) {
            return new ProviderMethodsHolder(type);
        }
    };
    private static final ClassValue<VertexMethodsHolder> VERTEX_METHODS = new ClassValue<>() {
        @Override
        protected VertexMethodsHolder computeValue(Class<?> type) {
            return new VertexMethodsHolder(type);
        }
    };
    private static final ClassValue<Resolution<Class<?>>> RENDER_LAYER_TYPES = new ClassValue<>() {
        @Override
        protected Resolution<Class<?>> computeValue(Class<?> matrixType) {
            return Resolution.resolve(() -> loadAny(matrixType.getClassLoader(), RENDER_LAYER_CLASSES));
        }
    };
    private static final ClassValue<LayerSet> LAYERS = new ClassValue<>() {
        @Override
        protected LayerSet computeValue(Class<?> renderLayerType) {
            return new LayerSet(renderLayerType);
        }
    };

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
        return draw(matrices, providers, camera, quads, lines, List.of());
    }

    /**
     * Submits every primitive owned by one {@link WorldGeometryBatch}. Each used layer is flushed
     * exactly once. When the custom no-depth layer is unavailable, through-wall quads share the
     * normal quad consumer and flush instead of starting a second identical debug-quad batch.
     */
    static boolean draw(Object matrices, Object providers, CameraPose camera,
                        List<WorldGeometryBatch.Quad> quads, List<WorldGeometryBatch.Line> lines,
                        List<WorldGeometryBatch.Quad> throughWallQuads) {
        if (matrices == null || providers == null || camera == null || !camera.available()
                || camera.eye() == null) {
            return false;
        }
        if (quads.isEmpty() && lines.isEmpty() && throughWallQuads.isEmpty()) {
            return true;
        }

        try {
            Object entry = MATRIX_METHODS.get(matrices.getClass()).value().peek(matrices);
            Class<?> renderLayerType = RENDER_LAYER_TYPES.get(matrices.getClass()).value();
            LayerSet layers = LAYERS.get(renderLayerType);
            ProviderMethods providerMethods = PROVIDER_METHODS.get(providers.getClass()).get(renderLayerType);
            Vec3 eye = camera.eye();

            Object normalQuadLayer = null;
            Object throughWallLayer = null;
            if (!quads.isEmpty()) {
                normalQuadLayer = layers.quads();
            }
            if (!throughWallQuads.isEmpty()) {
                throughWallLayer = layers.noDepthQuads();
                if (throughWallLayer == null) {
                    throughWallLayer = normalQuadLayer != null ? normalQuadLayer : layers.quads();
                }
            }

            boolean combineQuads = normalQuadLayer != null && normalQuadLayer == throughWallLayer;
            if (normalQuadLayer != null) {
                Object consumer = providerMethods.getBuffer(providers, normalQuadLayer);
                VertexMethods vertices = VERTEX_METHODS.get(consumer.getClass()).get(entry);
                vertices.quads(entry, consumer, quads, eye);
                if (combineQuads) {
                    vertices.quads(entry, consumer, throughWallQuads, eye);
                }
                providerMethods.draw(providers, normalQuadLayer);
            } else if (throughWallLayer != null && layers.isNormalQuadLayer(throughWallLayer)) {
                // No regular fills were submitted and no-depth construction failed. Draw the
                // fallback fill before outlines, matching the usual fill-then-line ordering.
                Object consumer = providerMethods.getBuffer(providers, throughWallLayer);
                VERTEX_METHODS.get(consumer.getClass()).get(entry)
                        .quads(entry, consumer, throughWallQuads, eye);
                providerMethods.draw(providers, throughWallLayer);
                combineQuads = true;
            }

            if (!lines.isEmpty()) {
                Object lineLayer = layers.lines();
                Object consumer = providerMethods.getBuffer(providers, lineLayer);
                VERTEX_METHODS.get(consumer.getClass()).get(entry).lines(entry, consumer, lines, eye);
                providerMethods.draw(providers, lineLayer);
            }

            if (throughWallLayer != null && !combineQuads) {
                Object consumer = providerMethods.getBuffer(providers, throughWallLayer);
                VERTEX_METHODS.get(consumer.getClass()).get(entry)
                        .quads(entry, consumer, throughWallQuads, eye);
                providerMethods.draw(providers, throughWallLayer);
            }
            return true;
        } catch (Throwable failure) {
            if (failure instanceof Error error) {
                throw error;
            }
            reportFailure(failure);
            return false;
        }
    }

    /**
     * Compatibility entry point for callers that only have through-wall geometry.
     */
    static boolean drawThroughWalls(Object matrices, Object providers, CameraPose camera,
                                    List<WorldGeometryBatch.Quad> quads) {
        if (quads.isEmpty()) {
            return true;
        }
        return draw(matrices, providers, camera, List.of(), List.of(), quads);
    }

    private static void reportFailure(Throwable failure) {
        String message = failure.getMessage();
        lastError = failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            diagnosticSink.accept("3D world renderer failed: " + lastError);
        }
    }

    /** Builds the 1.21.4-style custom no-depth RenderLayer. 1.21.11 moved custom layers to render
     * pipelines; that version deliberately falls back to its stock debug-quads layer here. */
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
                // Try the next namespace.
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

    private static Class<?> loadAny(ClassLoader loader, List<String> names) throws ClassNotFoundException {
        for (String name : names) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
                // Try the next namespace/version.
            }
        }
        throw new ClassNotFoundException("Minecraft class was not found; tried " + names);
    }

    private static Object invokeStaticNoArgs(Class<?> type, List<String> names) throws ReflectiveOperationException {
        Method target = method(type, names, 0, candidate -> Modifier.isStatic(candidate.getModifiers()));
        return target.invoke(null);
    }

    /** Resolves 1.21.11's RenderLayers owner first, then the owner used through 1.21.10. */
    private static Object renderLayer(ClassLoader loader, Class<?> legacyOwner, List<String> modernNames,
                                      List<String> legacyNames) throws ReflectiveOperationException {
        try {
            return invokeStaticNoArgs(loadAny(loader, RENDER_LAYERS_CLASSES), modernNames);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return invokeStaticNoArgs(legacyOwner, legacyNames);
        }
    }

    private static Method method(Class<?> type, List<String> names, int parameterCount,
                                 Predicate<Method> extra) throws NoSuchMethodException {
        for (Method candidate : type.getMethods()) {
            if (names.contains(candidate.getName()) && candidate.getParameterCount() == parameterCount
                    && extra.test(candidate)) {
                candidate.trySetAccessible();
                return candidate;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + names);
    }

    private static MethodHandle handle(Method method, MethodType targetType) throws IllegalAccessException {
        MethodHandle handle;
        try {
            handle = MethodHandles.publicLookup().unreflect(method);
        } catch (IllegalAccessException inaccessiblePublicMethod) {
            if (!method.trySetAccessible()) {
                throw inaccessiblePublicMethod;
            }
            handle = MethodHandles.lookup().unreflect(method);
        }
        return handle.asType(targetType);
    }

    private record MatrixMethods(MethodHandle peek) {
        static MatrixMethods resolve(Class<?> matrixType) throws ReflectiveOperationException {
            Method method = method(matrixType, List.of("peek", "method_23760", "c"), 0,
                    candidate -> !Modifier.isStatic(candidate.getModifiers())
                            && candidate.getReturnType() != boolean.class);
            return new MatrixMethods(handle(method, OBJECT_TO_OBJECT));
        }

        Object peek(Object matrices) throws Throwable {
            return (Object) peek.invokeExact(matrices);
        }
    }

    private record ProviderMethods(MethodHandle getBuffer, MethodHandle draw) {
        static ProviderMethods resolve(Class<?> providerType, Class<?> renderLayerType)
                throws ReflectiveOperationException {
            Method getBuffer = method(providerType,
                    List.of("getBuffer", "method_24145", "method_73477"), 1, candidate ->
                            !Modifier.isStatic(candidate.getModifiers())
                                    && candidate.getReturnType() != void.class
                                    && candidate.getParameterTypes()[0].isAssignableFrom(renderLayerType));
            Method draw = method(providerType, List.of("draw", "method_22994", "a"), 1, candidate ->
                    !Modifier.isStatic(candidate.getModifiers())
                            && candidate.getReturnType() == void.class
                            && candidate.getParameterTypes()[0].isAssignableFrom(renderLayerType));
            return new ProviderMethods(
                    handle(getBuffer, OBJECT_OBJECT_TO_OBJECT),
                    handle(draw, OBJECT_OBJECT_TO_VOID));
        }

        Object getBuffer(Object providers, Object layer) throws Throwable {
            return (Object) getBuffer.invokeExact(providers, layer);
        }

        void draw(Object providers, Object layer) throws Throwable {
            draw.invokeExact(providers, layer);
        }
    }

    private static final class ProviderMethodsHolder {
        private final Class<?> providerType;
        private volatile Class<?> renderLayerType;
        private volatile Resolution<ProviderMethods> resolution;

        private ProviderMethodsHolder(Class<?> providerType) {
            this.providerType = providerType;
        }

        ProviderMethods get(Class<?> requestedRenderLayerType) throws ReflectiveOperationException {
            Resolution<ProviderMethods> current = resolution;
            if (current == null || renderLayerType != requestedRenderLayerType) {
                synchronized (this) {
                    current = resolution;
                    if (current == null || renderLayerType != requestedRenderLayerType) {
                        current = Resolution.resolve(
                                () -> ProviderMethods.resolve(providerType, requestedRenderLayerType));
                        renderLayerType = requestedRenderLayerType;
                        resolution = current;
                    }
                }
            }
            return current.value();
        }
    }

    private record VertexMethods(MethodHandle vertex, boolean vertexUsesEntry,
                                 MethodHandle color, boolean colorUsesPackedArgb,
                                 MethodHandle normal, boolean normalUsesEntry,
                                 MethodHandle lineWidth) {
        static VertexMethods resolve(Class<?> consumerType, Object entry) throws ReflectiveOperationException {
            Method vertex;
            boolean vertexUsesEntry;
            try {
                vertex = method(consumerType, List.of("vertex", "addVertex", "method_56824", "a"), 4,
                        candidate -> entryAndFloats(candidate.getParameterTypes(), entry));
                vertexUsesEntry = true;
            } catch (NoSuchMethodException ignored) {
                vertex = method(consumerType, List.of("vertex", "addVertex", "method_22912", "a"), 3,
                        candidate -> allFloats(candidate.getParameterTypes()));
                vertexUsesEntry = false;
            }

            Method color;
            boolean colorUsesPackedArgb;
            try {
                // method_39415 is VertexConsumer.color(int) in both supported intermediary maps.
                color = method(consumerType,
                        List.of("color", "setColor", "method_39415", "method_1336", "a"), 1,
                        candidate -> candidate.getParameterTypes()[0] == int.class);
                colorUsesPackedArgb = true;
            } catch (NoSuchMethodException ignored) {
                color = method(consumerType, List.of("color", "setColor", "method_1336", "a"), 4,
                        candidate -> allInts(candidate.getParameterTypes()));
                colorUsesPackedArgb = false;
            }

            Method normal;
            boolean normalUsesEntry;
            try {
                normal = method(consumerType, List.of("normal", "setNormal", "method_60831", "b"), 4,
                        candidate -> entryAndFloats(candidate.getParameterTypes(), entry));
                normalUsesEntry = true;
            } catch (NoSuchMethodException ignored) {
                normal = method(consumerType, List.of("normal", "setNormal", "method_22914", "b"), 3,
                        candidate -> allFloats(candidate.getParameterTypes()));
                normalUsesEntry = false;
            }

            MethodHandle lineWidth = null;
            try {
                // LINES gained a mandatory LineWidth vertex element in 1.21.11. Older versions do
                // not expose this method, so its absence must remain a supported configuration.
                Method method = method(consumerType,
                        List.of("lineWidth", "method_75298", "a"), 1,
                        candidate -> candidate.getParameterTypes()[0] == float.class);
                lineWidth = handle(method, LINE_WIDTH);
            } catch (NoSuchMethodException ignored) {
                // Minecraft 1.21.4 line formats only require position, color, and normal.
            }

            return new VertexMethods(
                    handle(vertex, vertexUsesEntry ? VERTEX_WITH_ENTRY : RAW_VERTEX), vertexUsesEntry,
                    handle(color, colorUsesPackedArgb ? PACKED_COLOR : RGBA_COLOR), colorUsesPackedArgb,
                    handle(normal, normalUsesEntry ? VERTEX_WITH_ENTRY : RAW_VERTEX), normalUsesEntry,
                    lineWidth);
        }

        private static boolean entryAndFloats(Class<?>[] parameters, Object entry) {
            return parameters.length == 4 && parameters[0].isInstance(entry)
                    && parameters[1] == float.class && parameters[2] == float.class
                    && parameters[3] == float.class;
        }

        private static boolean allFloats(Class<?>[] parameters) {
            return parameters.length == 3 && parameters[0] == float.class
                    && parameters[1] == float.class && parameters[2] == float.class;
        }

        private static boolean allInts(Class<?>[] parameters) {
            return parameters.length == 4 && parameters[0] == int.class && parameters[1] == int.class
                    && parameters[2] == int.class && parameters[3] == int.class;
        }

        void quads(Object entry, Object consumer, List<WorldGeometryBatch.Quad> quads, Vec3 eye)
                throws Throwable {
            double eyeX = eye.x();
            double eyeY = eye.y();
            double eyeZ = eye.z();
            for (int index = 0, size = quads.size(); index < size; index++) {
                WorldGeometryBatch.Quad quad = quads.get(index);
                vertex(entry, consumer, quad.a(), quad.colorA(), eyeX, eyeY, eyeZ, false, 0, 0, 0);
                vertex(entry, consumer, quad.b(), quad.colorB(), eyeX, eyeY, eyeZ, false, 0, 0, 0);
                vertex(entry, consumer, quad.c(), quad.colorC(), eyeX, eyeY, eyeZ, false, 0, 0, 0);
                vertex(entry, consumer, quad.d(), quad.colorD(), eyeX, eyeY, eyeZ, false, 0, 0, 0);
            }
        }

        void lines(Object entry, Object consumer, List<WorldGeometryBatch.Line> lines, Vec3 eye)
                throws Throwable {
            double eyeX = eye.x();
            double eyeY = eye.y();
            double eyeZ = eye.z();
            for (int index = 0, size = lines.size(); index < size; index++) {
                WorldGeometryBatch.Line line = lines.get(index);
                Vec3 from = line.from();
                Vec3 to = line.to();
                double normalX = to.x() - from.x();
                double normalY = to.y() - from.y();
                double normalZ = to.z() - from.z();
                double lengthSquared = normalX * normalX + normalY * normalY + normalZ * normalZ;
                if (lengthSquared > 1.0e-18D) {
                    double inverseLength = 1.0D / Math.sqrt(lengthSquared);
                    normalX *= inverseLength;
                    normalY *= inverseLength;
                    normalZ *= inverseLength;
                } else {
                    normalX = 0.0D;
                    normalY = 0.0D;
                    normalZ = 0.0D;
                }
                vertex(entry, consumer, from, line.fromColor(), eyeX, eyeY, eyeZ,
                        true, normalX, normalY, normalZ);
                vertex(entry, consumer, to, line.toColor(), eyeX, eyeY, eyeZ,
                        true, normalX, normalY, normalZ);
            }
        }

        private void vertex(Object entry, Object consumer, Vec3 point, int argb,
                            double eyeX, double eyeY, double eyeZ, boolean writeNormal,
                            double normalX, double normalY, double normalZ) throws Throwable {
            float x = (float) (point.x() - eyeX);
            float y = (float) (point.y() - eyeY);
            float z = (float) (point.z() - eyeZ);
            if (vertexUsesEntry) {
                vertex.invokeExact(consumer, entry, x, y, z);
            } else {
                vertex.invokeExact(consumer, x, y, z);
            }
            if (colorUsesPackedArgb) {
                color.invokeExact(consumer, argb);
            } else {
                color.invokeExact(consumer, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF,
                        argb & 0xFF, (argb >>> 24) & 0xFF);
            }
            if (writeNormal) {
                float nx = (float) normalX;
                float ny = (float) normalY;
                float nz = (float) normalZ;
                if (normalUsesEntry) {
                    normal.invokeExact(consumer, entry, nx, ny, nz);
                } else {
                    normal.invokeExact(consumer, nx, ny, nz);
                }
                if (lineWidth != null) {
                    lineWidth.invokeExact(consumer, 1.0F);
                }
            }
        }
    }

    private static final class VertexMethodsHolder {
        private final Class<?> consumerType;
        private volatile Class<?> entryType;
        private volatile Resolution<VertexMethods> resolution;

        private VertexMethodsHolder(Class<?> consumerType) {
            this.consumerType = consumerType;
        }

        VertexMethods get(Object entry) throws ReflectiveOperationException {
            Class<?> requestedEntryType = entry.getClass();
            Resolution<VertexMethods> current = resolution;
            if (current == null || entryType != requestedEntryType) {
                synchronized (this) {
                    current = resolution;
                    if (current == null || entryType != requestedEntryType) {
                        current = Resolution.resolve(() -> VertexMethods.resolve(consumerType, entry));
                        entryType = requestedEntryType;
                        resolution = current;
                    }
                }
            }
            return current.value();
        }
    }

    private static final class LayerSet {
        private final Class<?> renderLayerType;
        private final ClassLoader loader;
        private volatile Resolution<Object> quads;
        private volatile Resolution<Object> lines;
        private volatile Resolution<Object> noDepthQuads;

        private LayerSet(Class<?> renderLayerType) {
            this.renderLayerType = renderLayerType;
            this.loader = renderLayerType.getClassLoader();
        }

        Object quads() throws ReflectiveOperationException {
            Resolution<Object> current = quads;
            if (current == null) {
                synchronized (this) {
                    current = quads;
                    if (current == null) {
                        current = Resolution.resolve(() -> renderLayer(loader, renderLayerType,
                                List.of("debugQuads", "method_76023", "w"),
                                List.of("getDebugQuads", "method_49042", "C")));
                        quads = current;
                    }
                }
            }
            return current.value();
        }

        Object lines() throws ReflectiveOperationException {
            Resolution<Object> current = lines;
            if (current == null) {
                synchronized (this) {
                    current = lines;
                    if (current == null) {
                        current = Resolution.resolve(() -> renderLayer(loader, renderLayerType,
                                List.of("lines", "method_76015", "r"),
                                List.of("getLines", "method_23594", "y")));
                        lines = current;
                    }
                }
            }
            return current.value();
        }

        Object noDepthQuads() {
            Resolution<Object> current = noDepthQuads;
            if (current == null) {
                synchronized (this) {
                    current = noDepthQuads;
                    if (current == null) {
                        current = Resolution.resolve(() -> buildNoDepthQuadLayer(loader));
                        noDepthQuads = current;
                        if (current.failure() != null) {
                            Throwable failure = current.failure();
                            diagnosticSink.accept("ESP through-walls render layer unavailable, falling back to normal "
                                    + "depth test: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                        }
                    }
                }
            }
            return current.failure() == null ? current.uncheckedValue() : null;
        }

        boolean isNormalQuadLayer(Object layer) throws ReflectiveOperationException {
            return layer == quads();
        }
    }

    @FunctionalInterface
    private interface Resolver<T> {
        T resolve() throws ReflectiveOperationException;
    }

    private record Resolution<T>(T uncheckedValue, ReflectiveOperationException failure) {
        static <T> Resolution<T> resolve(Resolver<T> resolver) {
            try {
                return new Resolution<>(resolver.resolve(), null);
            } catch (ReflectiveOperationException failure) {
                return new Resolution<>(null, failure);
            }
        }

        T value() throws ReflectiveOperationException {
            if (failure != null) {
                throw failure;
            }
            return uncheckedValue;
        }
    }
}
