package dev.aurora.render;

import dev.aurora.minecraft.HudSize;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Small, mapping-independent OpenGL effect pipeline loaded from Minecraft's LWJGL class loader.
 * The framebuffer is copied once for each glass draw, blurred at quarter resolution, then sampled
 * through a rounded rectangle mask. All touched GL state is restored before returning to vanilla.
 */
public final class OpenGlHudEffects {
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_VIEWPORT = 0x0BA2;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_ACTIVE_TEXTURE = 0x84E0;
    private static final int GL_TEXTURE_BINDING_2D = 0x8069;
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_VERTEX_SHADER = 0x8B31;
    private static final int GL_FRAGMENT_SHADER = 0x8B30;
    private static final int GL_COMPILE_STATUS = 0x8B81;
    private static final int GL_LINK_STATUS = 0x8B82;
    private static final int DOWNSAMPLE = 4;

    private static final String FULLSCREEN_VERTEX = """
            #version 150
            out vec2 uv;
            void main() {
                vec2 p = vec2((gl_VertexID == 1) ? 3.0 : -1.0,
                              (gl_VertexID == 2) ? 3.0 : -1.0);
                uv = p * 0.5 + 0.5;
                gl_Position = vec4(p, 0.0, 1.0);
            }
            """;
    private static final String BLUR_FRAGMENT = """
            #version 150
            uniform sampler2D sourceTexture;
            uniform vec2 direction;
            in vec2 uv;
            out vec4 fragColor;
            void main() {
                vec4 color = texture(sourceTexture, uv) * 0.227027;
                color += texture(sourceTexture, uv + direction * 1.384615) * 0.316216;
                color += texture(sourceTexture, uv - direction * 1.384615) * 0.316216;
                color += texture(sourceTexture, uv + direction * 3.230769) * 0.070270;
                color += texture(sourceTexture, uv - direction * 3.230769) * 0.070270;
                fragColor = color;
            }
            """;
    private static final String PANEL_VERTEX = """
            #version 150
            uniform vec4 rect;
            uniform vec2 panelOrigin;
            uniform vec2 viewportSize;
            out vec2 uv;
            out vec2 localPosition;
            void main() {
                const vec2 corners[6] = vec2[6](
                    vec2(0,0), vec2(1,0), vec2(1,1),
                    vec2(0,0), vec2(1,1), vec2(0,1));
                vec2 corner = corners[gl_VertexID];
                vec2 pixel = mix(rect.xy, rect.zw, corner);
                uv = pixel / viewportSize;
                localPosition = pixel - panelOrigin;
                vec2 ndc = pixel / viewportSize * 2.0 - 1.0;
                gl_Position = vec4(ndc, 0.0, 1.0);
            }
            """;
    private static final String PANEL_FRAGMENT = """
            #version 150
            uniform sampler2D blurredTexture;
            uniform vec2 panelSize;
            uniform float radius;
            uniform vec4 tint;
            uniform vec4 border;
            in vec2 uv;
            in vec2 localPosition;
            out vec4 fragColor;
            float roundedDistance(vec2 p, vec2 size, float r) {
                vec2 q = abs(p - size * 0.5) - (size * 0.5 - vec2(r));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }
            void main() {
                float distance = roundedDistance(localPosition, panelSize, radius);
                float coverage = 1.0 - smoothstep(-1.0, 1.0, distance);
                if (distance > 0.0) {
                    float shadow = (1.0 - smoothstep(0.0, 12.0, distance)) * 0.40;
                    if (shadow <= 0.001) discard;
                    fragColor = vec4(0.0, 0.0, 0.0, shadow);
                    return;
                }
                vec4 glass = texture(blurredTexture, uv);
                float luminance = dot(glass.rgb, vec3(0.2126, 0.7152, 0.0722));
                glass.rgb = mix(vec3(luminance), glass.rgb, 1.22) * 0.95;
                glass.rgb = mix(glass.rgb, tint.rgb, tint.a);
                float edge = smoothstep(-2.0, -0.25, distance);
                glass.rgb = mix(glass.rgb, border.rgb, edge * border.a);
                float topHighlight = smoothstep(panelSize.y - 2.0, panelSize.y - 0.25,
                                                localPosition.y) * (1.0 - edge);
                glass.rgb = mix(glass.rgb, vec3(1.0), topHighlight * 0.12);
                glass.a = coverage;
                fragColor = glass;
            }
            """;

    private final Consumer<String> diagnosticSink;
    private final Map<String, Method> methods = new HashMap<>();
    private boolean unavailable;
    private boolean initialized;
    private int captureTexture;
    private int horizontalTexture;
    private int verticalTexture;
    private int horizontalFramebuffer;
    private int verticalFramebuffer;
    private int blurProgram;
    private int panelProgram;
    private int vertexArray;
    private int width;
    private int height;
    private boolean sceneCaptured;
    private float blurredRadius = Float.NaN;
    private int frameIndex;

    public OpenGlHudEffects(Consumer<String> diagnosticSink) {
        this.diagnosticSink = diagnosticSink == null ? ignored -> { } : diagnosticSink;
    }

    public void beginFrame() {
        // A full-screen separable blur is far more expensive than drawing the small HUD panels.
        // Reuse it for two frames; at 144 Hz it is still refreshed 48 times per second and remains
        // visually continuous while cutting the Text GUI's blur workload by roughly two thirds.
        if (frameIndex++ % 3 == 0) {
            sceneCaptured = false;
            blurredRadius = Float.NaN;
        }
    }

    public boolean drawFrostedPanel(HudSize hud, int left, int top, int right, int bottom,
                                    int radius, float blurRadius, int tintColor, int borderColor) {
        if (unavailable || !hud.available() || right <= left || bottom <= top) return false;
        try {
            int[] viewport = new int[4];
            call("org.lwjgl.opengl.GL11", "glGetIntegerv", new Class<?>[]{int.class, int[].class}, GL_VIEWPORT, viewport);
            int framebufferWidth = viewport[2];
            int framebufferHeight = viewport[3];
            if (framebufferWidth <= 0 || framebufferHeight <= 0) return false;
            initialize();
            allocate(framebufferWidth, framebufferHeight);
            render(viewport, hud, left, top, right, bottom, radius, blurRadius, tintColor, borderColor);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            unavailable = true;
            diagnosticSink.accept("Frosted HUD effects unavailable: " + rootMessage(exception));
            return false;
        }
    }

    private void initialize() throws ReflectiveOperationException {
        if (initialized) return;
        blurProgram = createProgram(FULLSCREEN_VERTEX, BLUR_FRAGMENT);
        panelProgram = createProgram(PANEL_VERTEX, PANEL_FRAGMENT);
        vertexArray = integerCall("org.lwjgl.opengl.GL30", "glGenVertexArrays", new Class<?>[0]);
        captureTexture = createTexture();
        horizontalTexture = createTexture();
        verticalTexture = createTexture();
        horizontalFramebuffer = createFramebuffer(horizontalTexture);
        verticalFramebuffer = createFramebuffer(verticalTexture);
        initialized = true;
    }

    private void allocate(int newWidth, int newHeight) throws ReflectiveOperationException {
        if (width == newWidth && height == newHeight) return;
        width = newWidth;
        height = newHeight;
        allocateTexture(captureTexture, width, height);
        allocateTexture(horizontalTexture, Math.max(1, width / DOWNSAMPLE), Math.max(1, height / DOWNSAMPLE));
        allocateTexture(verticalTexture, Math.max(1, width / DOWNSAMPLE), Math.max(1, height / DOWNSAMPLE));
    }

    private void render(int[] viewport, HudSize hud, int left, int top, int right, int bottom,
                        int radius, float blurRadius, int tintColor, int borderColor)
            throws ReflectiveOperationException {
        int oldFramebuffer = getInteger(GL_FRAMEBUFFER_BINDING);
        int oldProgram = getInteger(GL_CURRENT_PROGRAM);
        int oldVertexArray = getInteger(GL_VERTEX_ARRAY_BINDING);
        int oldActiveTexture = getInteger(GL_ACTIVE_TEXTURE);
        activeTexture(GL_TEXTURE0);
        int oldTexture0 = getInteger(GL_TEXTURE_BINDING_2D);
        boolean blendWasEnabled = booleanCall("org.lwjgl.opengl.GL11", "glIsEnabled", new Class<?>[]{int.class}, GL_BLEND);
        boolean depthWasEnabled = booleanCall("org.lwjgl.opengl.GL11", "glIsEnabled",
                new Class<?>[]{int.class}, GL_DEPTH_TEST);
        boolean scissorWasEnabled = booleanCall("org.lwjgl.opengl.GL11", "glIsEnabled",
                new Class<?>[]{int.class}, GL_SCISSOR_TEST);
        try {
            // 1.21.11's render pipelines may leave depth testing enabled when the HUD callback is
            // reached. The fullscreen blur triangle and glass panel have no meaningful world depth
            // and are otherwise completely rejected by the existing depth buffer.
            call("org.lwjgl.opengl.GL11", "glDisable", new Class<?>[]{int.class}, GL_DEPTH_TEST);
            // A DrawContext scissor uses main-framebuffer coordinates and can clip the
            // quarter-resolution off-screen targets down to nothing.
            call("org.lwjgl.opengl.GL11", "glDisable", new Class<?>[]{int.class}, GL_SCISSOR_TEST);
            int smallWidth = Math.max(1, width / DOWNSAMPLE);
            int smallHeight = Math.max(1, height / DOWNSAMPLE);
            if (!sceneCaptured) {
                bindTexture(captureTexture);
                call("org.lwjgl.opengl.GL11", "glCopyTexSubImage2D",
                        new Class<?>[]{int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class},
                        GL_TEXTURE_2D, 0, 0, 0, viewport[0], viewport[1], width, height);
                sceneCaptured = true;
            }
            if (Float.compare(blurredRadius, blurRadius) != 0) {
                setViewport(0, 0, smallWidth, smallHeight);
                int passes = Math.max(2, Math.min(6, (int) Math.ceil(blurRadius / 6.0F)));
                int source = captureTexture;
                float passRadius = blurRadius / (float) Math.sqrt(passes);
                for (int pass = 0; pass < passes; pass++) {
                    bindFramebuffer(horizontalFramebuffer);
                    drawBlur(source, passRadius / width, 0.0F);
                    bindFramebuffer(verticalFramebuffer);
                    drawBlur(horizontalTexture, 0.0F, passRadius / height);
                    source = verticalTexture;
                }
                blurredRadius = blurRadius;
            }

            bindFramebuffer(oldFramebuffer);
            setViewport(viewport[0], viewport[1], width, height);
            useProgram(panelProgram);
            bindVertexArray(vertexArray);
            bindTexture(verticalTexture);
            uniform1i(panelProgram, "blurredTexture", 0);
            float scaleX = width / (float) hud.width();
            float scaleY = height / (float) hud.height();
            float glLeft = left * scaleX;
            float glRight = right * scaleX;
            float glBottom = height - bottom * scaleY;
            float glTop = height - top * scaleY;
            float shadowSpread = 12.0F * Math.min(scaleX, scaleY);
            uniform4f(panelProgram, "rect", glLeft - shadowSpread, glBottom - shadowSpread,
                    glRight + shadowSpread, glTop + shadowSpread);
            uniform2f(panelProgram, "panelOrigin", glLeft, glBottom);
            uniform2f(panelProgram, "viewportSize", width, height);
            uniform2f(panelProgram, "panelSize", glRight - glLeft, glTop - glBottom);
            uniform1f(panelProgram, "radius", Math.max(0, radius) * Math.min(scaleX, scaleY));
            uniformColor(panelProgram, "tint", tintColor);
            uniformColor(panelProgram, "border", borderColor);
            enableBlend();
            call("org.lwjgl.opengl.GL11", "glDrawArrays",
                    new Class<?>[]{int.class, int.class, int.class}, GL_TRIANGLES, 0, 6);
        } finally {
            bindFramebuffer(oldFramebuffer);
            useProgram(oldProgram);
            bindVertexArray(oldVertexArray);
            activeTexture(GL_TEXTURE0);
            bindTexture(oldTexture0);
            activeTexture(oldActiveTexture);
            setViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (!blendWasEnabled) call("org.lwjgl.opengl.GL11", "glDisable", new Class<?>[]{int.class}, GL_BLEND);
            if (depthWasEnabled) {
                call("org.lwjgl.opengl.GL11", "glEnable", new Class<?>[]{int.class}, GL_DEPTH_TEST);
            } else {
                call("org.lwjgl.opengl.GL11", "glDisable", new Class<?>[]{int.class}, GL_DEPTH_TEST);
            }
            if (scissorWasEnabled) {
                call("org.lwjgl.opengl.GL11", "glEnable", new Class<?>[]{int.class}, GL_SCISSOR_TEST);
            } else {
                call("org.lwjgl.opengl.GL11", "glDisable", new Class<?>[]{int.class}, GL_SCISSOR_TEST);
            }
        }
    }

    private void drawBlur(int texture, float directionX, float directionY) throws ReflectiveOperationException {
        useProgram(blurProgram);
        bindVertexArray(vertexArray);
        bindTexture(texture);
        uniform1i(blurProgram, "sourceTexture", 0);
        // direction is already in full-frame UV units. Scaling it again by the downsample factor
        // turns the low end of the control into a coarse resample instead of a Gaussian blur.
        uniform2f(blurProgram, "direction", directionX, directionY);
        call("org.lwjgl.opengl.GL11", "glDisable", new Class<?>[]{int.class}, GL_BLEND);
        call("org.lwjgl.opengl.GL11", "glDrawArrays",
                new Class<?>[]{int.class, int.class, int.class}, GL_TRIANGLES, 0, 3);
    }

    private int createTexture() throws ReflectiveOperationException {
        int texture = integerCall("org.lwjgl.opengl.GL11", "glGenTextures", new Class<?>[0]);
        bindTexture(texture);
        textureParameter(GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        textureParameter(GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        textureParameter(GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        textureParameter(GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        allocateTexture(texture, 1, 1);
        return texture;
    }

    private void allocateTexture(int texture, int textureWidth, int textureHeight) throws ReflectiveOperationException {
        bindTexture(texture);
        call("org.lwjgl.opengl.GL11", "glTexImage2D",
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, java.nio.ByteBuffer.class},
                GL_TEXTURE_2D, 0, GL_RGBA8, textureWidth, textureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
    }

    private int createFramebuffer(int texture) throws ReflectiveOperationException {
        int previous = getInteger(GL_FRAMEBUFFER_BINDING);
        int framebuffer = integerCall("org.lwjgl.opengl.GL30", "glGenFramebuffers", new Class<?>[0]);
        bindFramebuffer(framebuffer);
        call("org.lwjgl.opengl.GL30", "glFramebufferTexture2D",
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class},
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        int status = integerCall("org.lwjgl.opengl.GL30", "glCheckFramebufferStatus",
                new Class<?>[]{int.class}, GL_FRAMEBUFFER);
        bindFramebuffer(previous);
        if (status != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Framebuffer status " + status);
        return framebuffer;
    }

    private int createProgram(String vertexSource, String fragmentSource) throws ReflectiveOperationException {
        int vertex = createShader(GL_VERTEX_SHADER, vertexSource);
        int fragment = createShader(GL_FRAGMENT_SHADER, fragmentSource);
        int program = integerCall("org.lwjgl.opengl.GL20", "glCreateProgram", new Class<?>[0]);
        call("org.lwjgl.opengl.GL20", "glAttachShader", new Class<?>[]{int.class, int.class}, program, vertex);
        call("org.lwjgl.opengl.GL20", "glAttachShader", new Class<?>[]{int.class, int.class}, program, fragment);
        call("org.lwjgl.opengl.GL20", "glLinkProgram", new Class<?>[]{int.class}, program);
        int linked = integerCall("org.lwjgl.opengl.GL20", "glGetProgrami", new Class<?>[]{int.class, int.class}, program, GL_LINK_STATUS);
        call("org.lwjgl.opengl.GL20", "glDeleteShader", new Class<?>[]{int.class}, vertex);
        call("org.lwjgl.opengl.GL20", "glDeleteShader", new Class<?>[]{int.class}, fragment);
        if (linked == 0) {
            String log = stringCall("org.lwjgl.opengl.GL20", "glGetProgramInfoLog", new Class<?>[]{int.class}, program);
            throw new IllegalStateException("Shader link failed: " + log);
        }
        return program;
    }

    private int createShader(int type, String source) throws ReflectiveOperationException {
        int shader = integerCall("org.lwjgl.opengl.GL20", "glCreateShader", new Class<?>[]{int.class}, type);
        call("org.lwjgl.opengl.GL20", "glShaderSource", new Class<?>[]{int.class, CharSequence.class}, shader, source);
        call("org.lwjgl.opengl.GL20", "glCompileShader", new Class<?>[]{int.class}, shader);
        int compiled = integerCall("org.lwjgl.opengl.GL20", "glGetShaderi", new Class<?>[]{int.class, int.class}, shader, GL_COMPILE_STATUS);
        if (compiled == 0) {
            String log = stringCall("org.lwjgl.opengl.GL20", "glGetShaderInfoLog", new Class<?>[]{int.class}, shader);
            throw new IllegalStateException("Shader compile failed: " + log);
        }
        return shader;
    }

    private void textureParameter(int name, int value) throws ReflectiveOperationException {
        call("org.lwjgl.opengl.GL11", "glTexParameteri", new Class<?>[]{int.class, int.class, int.class}, GL_TEXTURE_2D, name, value);
    }

    private void bindTexture(int texture) throws ReflectiveOperationException {
        call("org.lwjgl.opengl.GL11", "glBindTexture", new Class<?>[]{int.class, int.class}, GL_TEXTURE_2D, texture);
    }

    private void bindFramebuffer(int framebuffer) throws ReflectiveOperationException {
        call("org.lwjgl.opengl.GL30", "glBindFramebuffer", new Class<?>[]{int.class, int.class}, GL_FRAMEBUFFER, framebuffer);
    }

    private void bindVertexArray(int vao) throws ReflectiveOperationException {
        call("org.lwjgl.opengl.GL30", "glBindVertexArray", new Class<?>[]{int.class}, vao);
    }

    private void useProgram(int program) throws ReflectiveOperationException {
        call("org.lwjgl.opengl.GL20", "glUseProgram", new Class<?>[]{int.class}, program);
    }

    private void activeTexture(int textureUnit) throws ReflectiveOperationException {
        call("org.lwjgl.opengl.GL13", "glActiveTexture", new Class<?>[]{int.class}, textureUnit);
    }

    private void setViewport(int x, int y, int viewportWidth, int viewportHeight) throws ReflectiveOperationException {
        call("org.lwjgl.opengl.GL11", "glViewport", new Class<?>[]{int.class, int.class, int.class, int.class},
                x, y, viewportWidth, viewportHeight);
    }

    private void enableBlend() throws ReflectiveOperationException {
        call("org.lwjgl.opengl.GL11", "glEnable", new Class<?>[]{int.class}, GL_BLEND);
        call("org.lwjgl.opengl.GL11", "glBlendFunc", new Class<?>[]{int.class, int.class}, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private int getInteger(int name) throws ReflectiveOperationException {
        return integerCall("org.lwjgl.opengl.GL11", "glGetInteger", new Class<?>[]{int.class}, name);
    }

    private void uniform1i(int program, String name, int value) throws ReflectiveOperationException {
        int location = uniformLocation(program, name);
        call("org.lwjgl.opengl.GL20", "glUniform1i", new Class<?>[]{int.class, int.class}, location, value);
    }

    private void uniform1f(int program, String name, float value) throws ReflectiveOperationException {
        int location = uniformLocation(program, name);
        call("org.lwjgl.opengl.GL20", "glUniform1f", new Class<?>[]{int.class, float.class}, location, value);
    }

    private void uniform2f(int program, String name, float x, float y) throws ReflectiveOperationException {
        int location = uniformLocation(program, name);
        call("org.lwjgl.opengl.GL20", "glUniform2f", new Class<?>[]{int.class, float.class, float.class}, location, x, y);
    }

    private void uniform4f(int program, String name, float x, float y, float z, float w) throws ReflectiveOperationException {
        int location = uniformLocation(program, name);
        call("org.lwjgl.opengl.GL20", "glUniform4f", new Class<?>[]{int.class, float.class, float.class, float.class, float.class},
                location, x, y, z, w);
    }

    private void uniformColor(int program, String name, int argb) throws ReflectiveOperationException {
        uniform4f(program, name, ((argb >>> 16) & 0xFF) / 255.0F, ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, ((argb >>> 24) & 0xFF) / 255.0F);
    }

    private int uniformLocation(int program, String name) throws ReflectiveOperationException {
        return integerCall("org.lwjgl.opengl.GL20", "glGetUniformLocation",
                new Class<?>[]{int.class, CharSequence.class}, program, name);
    }

    private Object call(String className, String name, Class<?>[] types, Object... arguments)
            throws ReflectiveOperationException {
        String key = className + '#' + name + java.util.Arrays.toString(types);
        Method method = methods.get(key);
        if (method == null) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> type = Class.forName(className, true, loader);
            method = type.getMethod(name, types);
            methods.put(key, method);
        }
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw exception;
        }
    }

    private int integerCall(String owner, String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        return ((Number) call(owner, name, types, args)).intValue();
    }

    private boolean booleanCall(String owner, String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        return (Boolean) call(owner, name, types, args);
    }

    private String stringCall(String owner, String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        return String.valueOf(call(owner, name, types, args));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
