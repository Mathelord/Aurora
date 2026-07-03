package dev.aurora.minecraft;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.Vec3;
import dev.aurora.aim.DecoupledAimState;
import dev.aurora.aim.DecoupledMovementSteering;
import dev.aurora.aim.AimMath;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ReflectionMinecraftBridge implements MinecraftBridge {
    private static final double BODY_AIM_HEIGHT_RATIO = 0.55D;
    // Renderers only need a plausible projection, not the player's exact configured slider value.
    private static final double DEFAULT_FOV_DEGREES = 70.0D;
    private static final List<String> CLIENT_CLASSES = List.of(
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.MinecraftClient",
            "net.minecraft.class_310",
            "flk"
    );
    private static final List<String> ATTRIBUTE_CLASSES = List.of(
            "net.minecraft.world.entity.ai.attributes.Attributes",
            "net.minecraft.entity.attribute.EntityAttributes",
            "net.minecraft.class_5134",
            "bwq"
    );
    private static final List<String> ITEMS_CLASSES = List.of(
            "net.minecraft.world.item.Items",
            "net.minecraft.item.Items",
            "net.minecraft.class_1802",
            "cwu"
    );
    private static final List<String> BLOCKS_CLASSES = List.of(
            "net.minecraft.world.level.block.Blocks",
            "net.minecraft.block.Blocks",
            "net.minecraft.class_2246"
    );
    private static final List<String> BLOCK_POS_CLASSES = List.of(
            "net.minecraft.core.BlockPos",
            "net.minecraft.util.math.BlockPos",
            "net.minecraft.class_2338"
    );
    private static final List<String> VEC3_CLASSES = List.of(
            "net.minecraft.world.phys.Vec3",
            "net.minecraft.util.math.Vec3d",
            "net.minecraft.class_243"
    );
    private static final List<String> DIRECTION_CLASSES = List.of(
            "net.minecraft.core.Direction",
            "net.minecraft.util.math.Direction",
            "net.minecraft.class_2350"
    );
    private static final List<String> BLOCK_HIT_RESULT_CLASSES = List.of(
            "net.minecraft.world.phys.BlockHitResult",
            "net.minecraft.util.hit.BlockHitResult",
            "net.minecraft.class_3965"
    );
    private static final List<String> BOX_CLASSES = List.of(
            "net.minecraft.world.phys.AABB",
            "net.minecraft.util.math.Box",
            "net.minecraft.class_238"
    );
    private static final List<String> BLOCK_ITEM_CLASSES = List.of(
            "net.minecraft.world.item.BlockItem",
            "net.minecraft.item.BlockItem",
            "net.minecraft.class_1747"
    );
    private static final List<String> END_CRYSTAL_CLASSES = List.of(
            "net.minecraft.world.entity.boss.enderdragon.EndCrystal",
            "net.minecraft.entity.decoration.EndCrystalEntity",
            "net.minecraft.class_1511"
    );
    private static final List<String> BLOCK_RANGE_FIELDS = List.of(
            "BLOCK_INTERACTION_RANGE",
            "field_47758",
            "g",
            "f_337215_"
    );
    private static final List<String> ENTITY_RANGE_FIELDS = List.of(
            "ENTITY_INTERACTION_RANGE",
            "field_47759",
            "j",
            "f_337223_"
    );
    // LivingEntity#jumpingCooldown in named, Mojmap, 1.21.4 intermediary, and 1.21.4 official.
    private static final List<String> JUMP_COOLDOWN_FIELD_NAMES = List.of(
            "jumpingCooldown", "noJumpDelay", "field_6228", "ce"
    );
    private static final List<String> JUMP_KEY_FIELD_NAMES = List.of(
            "jumpKey", "keyJump", "field_1903", "z", "f_92089_"
    );
    private static final List<String> FORWARD_KEY_FIELD_NAMES = List.of(
            "keyUp", "forwardKey", "keyForward", "field_1894", "w", "f_92070_"
    );
    // Entity#xo/yo/zo (last-tick position, used to interpolate render-space poses), in Mojmap and
    // 1.21.4 intermediary.
    private static final List<String> PREV_X_FIELD_NAMES = List.of("xo", "field_6014");
    private static final List<String> PREV_Y_FIELD_NAMES = List.of("yo", "field_6036");
    private static final List<String> PREV_Z_FIELD_NAMES = List.of("zo", "field_5969");
    // DeltaTracker#getGameTimeDeltaPartialTick(boolean), in Mojmap and 1.21.4 intermediary.
    private static final List<String> GAME_TIME_DELTA_METHOD_NAMES = List.of(
            "getGameTimeDeltaPartialTick", "method_60637"
    );
    private static final List<String> USE_KEY_FIELD_NAMES = List.of(
            "keyUse", "useKey", "field_1904", "f_92086_"
    );
    private static final List<String> INVENTORY_SELECTED_SLOT_FIELD_NAMES = List.of(
            "selectedSlot", "selected", "field_7545", "j", "l", "f_35974_"
    );
    private static final List<String> KEY_PRESSED_METHOD_NAMES = List.of(
            "setDown", "setPressed", "method_23481", "m_90837_", "a"
    );
    private final Set<String> reportedCapabilities = ConcurrentHashMap.newKeySet();
    private final Consumer<String> diagnosticSink;
    private final Map<Object, Double> originalReachValues = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<String, Object> aimTargetEntities = Collections.synchronizedMap(new java.util.HashMap<>());
    private final Map<Class<?>, Method> fillMethodCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Object clickGuiScreen;
    private volatile Object previousScreen;

    public ReflectionMinecraftBridge() {
        this(message -> System.err.println("[Aurora] " + message));
    }

    public ReflectionMinecraftBridge(Consumer<String> diagnosticSink) {
        this.diagnosticSink = diagnosticSink == null ? ignored -> { } : diagnosticSink;
    }

    private void reportOnce(String capability, Exception exception) {
        if (reportedCapabilities.add(capability)) {
            diagnosticSink.accept("Minecraft bridge capability '" + capability + "' failed: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    @Override
    public boolean isSinglePlayer() {
        Optional<Object> client = minecraftClient();
        if (client.isEmpty()) {
            return false;
        }
        Object instance = client.get();
        for (String methodName : List.of(
                "isInSingleplayer",
                "isIntegratedServerRunning",
                "hasSingleplayerServer",
                "isLocalServer",
                "isSingleplayer",
                "isConnectedToLocalServer",
                "method_1542",
                "method_1496",
                "method_47392",
                "m_91090_",
                "m_91091_",
                "m_257720_",
                "T",
                "U",
                "W")) {
            Optional<Object> value = invokeNoArgs(instance, methodName);
            if (value.filter(Boolean.class::isInstance).map(Boolean.class::cast).orElse(false)) {
                return true;
            }
        }
        for (String methodName : List.of("getServer", "getSingleplayerServer", "method_1576", "m_91092_", "V")) {
            Optional<Object> value = invokeNoArgs(instance, methodName);
            if (value.isPresent()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean applyReach(double range) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            if (player == null) {
                return false;
            }
            boolean applied = setInteractionAttributes(player, range);
            Object serverPlayer = findIntegratedServerPlayer(client, player).orElse(null);
            if (serverPlayer != null) {
                applied |= setInteractionAttributes(serverPlayer, range);
            }
            return applied;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("reach", exception);
            return false;
        }
    }

    @Override
    public boolean resetReach() {
        boolean restored = false;
        synchronized (originalReachValues) {
            for (Map.Entry<Object, Double> entry : originalReachValues.entrySet()) {
                restored |= invokeVoidCompatible(entry.getKey(), List.of("setBaseValue", "method_6192", "m_22100_", "a"), entry.getValue());
            }
            originalReachValues.clear();
        }
        return restored;
    }

    @Override
    public AimContext aimContext(double range, boolean ignoreWalls) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            Object world = readField(client, "world", "level", "field_1687", "r", "f_91073_").orElse(null);
            if (player == null || world == null) {
                return AimContext.unavailable();
            }

            double playerX = x(player).orElseThrow();
            double playerY = y(player).orElseThrow();
            double playerZ = z(player).orElseThrow();
            double eyeY = playerY + eyeHeight(player);
            double yaw = yaw(player).orElse(0.0D);
            double pitch = pitch(player).orElse(0.0D);
            double maxDistanceSquared = range * range;

            List<AimTarget> targets = new ArrayList<>();
            Map<String, Object> resolvedEntities = new java.util.HashMap<>();
            for (Object candidate : players(world)) {
                if (candidate == player || !isUsableTarget(candidate)) {
                    continue;
                }
                double targetX = x(candidate).orElse(Double.NaN);
                double targetY = y(candidate).orElse(Double.NaN);
                double targetZ = z(candidate).orElse(Double.NaN);
                if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) {
                    continue;
                }
                if (!ignoreWalls && !hasLineOfSight(player, candidate)) {
                    continue;
                }
                Vec3 targetPoint = bodyAimPoint(targetX, targetY, targetZ, candidate);
                double distanceSquared = targetPoint.squaredDistanceTo(new Vec3(playerX, playerY, playerZ));
                if (distanceSquared > maxDistanceSquared) {
                    continue;
                }
                String id = targetId(candidate);
                resolvedEntities.put(id, candidate);
                targets.add(new AimTarget(
                        id,
                        targetName(candidate),
                        targetPoint,
                        distanceSquared,
                        yawTo(playerX, playerZ, targetPoint.x(), targetPoint.z()),
                        pitchTo(playerX, eyeY, playerZ, targetPoint.x(), targetPoint.y(), targetPoint.z()),
                        health(candidate)
                ));
            }

            synchronized (aimTargetEntities) {
                aimTargetEntities.clear();
                aimTargetEntities.putAll(resolvedEntities);
            }

            return new AimContext(
                    true,
                    screenOpen(client),
                    yaw,
                    pitch,
                    mouseSensitivity(client),
                    targets
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("aim-context", exception);
            return AimContext.unavailable();
        }
    }

    @Override
    public boolean applyAimRotation(double yaw, double pitch) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            if (player == null) {
                return false;
            }
            boolean yawApplied = invokeVoidNumberCompatible(player, List.of("setYaw", "setYRot", "method_36456", "m_146922_"), yaw)
                    || setNumberField(player, List.of("yaw", "yRot", "field_6031", "f_19857_"), yaw);
            boolean pitchApplied = invokeVoidNumberCompatible(player, List.of("setPitch", "setXRot", "method_36457", "m_146926_"), pitch)
                    || setNumberField(player, List.of("pitch", "xRot", "field_5965", "f_19858_"), pitch);
            return yawApplied || pitchApplied;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("aim-rotation", exception);
            return false;
        }
    }

    @Override
    public double mouseSensitivity() {
        try {
            Object client = minecraftClient().orElseThrow();
            return mouseSensitivity(client);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("mouse-sensitivity", exception);
            return 0.5D;
        }
    }

    @Override
    public boolean renderStatusText(Object renderContext, String text) {
        if (renderContext == null || text == null || text.isBlank()) {
            return false;
        }
        try {
            Object client = minecraftClient().orElseThrow();
            Object textRenderer = readField(client, "font", "textRenderer", "field_1772", "h", "f_91062_").orElse(null);
            if (textRenderer == null) {
                return false;
            }
            for (Method method : renderContext.getClass().getMethods()) {
                if (method.getParameterCount() == 6 && method.getName().toLowerCase(Locale.ROOT).contains("draw")) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (parameters[1] == String.class && parameters[2] == int.class && parameters[3] == int.class) {
                        method.invoke(renderContext, textRenderer, text, 6, 6, 0x55FF55, true);
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("hud-text", exception);
            return false;
        }
        return false;
    }

    @Override
    public ClickGuiInput clickGuiInput() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object window = invokeNoArgs(client, "getWindow", "method_22683", "m_91268_").orElseThrow();
            long handle = getNumberCompatible(window, List.of("getWindow", "getHandle", "method_4490", "m_85439_"))
                    .orElseThrow().longValue();
            int screenWidth = getNumberCompatible(window, List.of("getGuiScaledWidth", "getScaledWidth", "method_4486", "m_85445_"))
                    .orElseThrow().intValue();
            int screenHeight = getNumberCompatible(window, List.of("getGuiScaledHeight", "getScaledHeight", "method_4502", "m_85446_"))
                    .orElseThrow().intValue();
            int windowWidth = getNumberCompatible(window, List.of("getWidth", "method_4480", "m_85441_"))
                    .orElse((double) screenWidth).intValue();
            int windowHeight = getNumberCompatible(window, List.of("getHeight", "method_4507", "m_85442_"))
                    .orElse((double) screenHeight).intValue();

            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW", false, client.getClass().getClassLoader());
            double[] cursorX = new double[1];
            double[] cursorY = new double[1];
            glfw.getMethod("glfwGetCursorPos", long.class, double[].class, double[].class)
                    .invoke(null, handle, cursorX, cursorY);
            Method getKey = glfw.getMethod("glfwGetKey", long.class, int.class);
            Method getMouseButton = glfw.getMethod("glfwGetMouseButton", long.class, int.class);
            return new ClickGuiInput(
                    true,
                    screenWidth,
                    screenHeight,
                    cursorX[0] * screenWidth / Math.max(1, windowWidth),
                    cursorY[0] * screenHeight / Math.max(1, windowHeight),
                    ((Number) getMouseButton.invoke(null, handle, 0)).intValue() == 1,
                    ((Number) getKey.invoke(null, handle, 344)).intValue() == 1,
                    ((Number) getKey.invoke(null, handle, 256)).intValue() == 1
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("gui-input", exception);
            return ClickGuiInput.unavailable();
        }
    }

    @Override
    public void setClickGuiInputCaptured(boolean captured) {
        try {
            Object client = minecraftClient().orElseThrow();
            if (captured) {
                openClickGuiScreen(client);
            } else {
                closeClickGuiScreen(client);
            }
            return;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("gui-screen", exception);
        }

        setCursorCapturedFallback(captured);
    }

    private void setCursorCapturedFallback(boolean captured) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object mouse = readField(client, "mouseHandler", "mouse", "field_1729", "f_91067_").orElse(null);
            if (mouse != null) {
                List<String> methods = captured
                        ? List.of("releaseMouse", "unlockCursor", "method_1610", "m_91602_")
                        : List.of("grabMouse", "lockCursor", "method_1612", "m_91601_");
                if (invokeNoArgsIfPresent(mouse, methods)) {
                    return;
                }
            }

            Object window = invokeNoArgs(client, "getWindow", "method_22683", "m_91268_").orElseThrow();
            long handle = getNumberCompatible(window, List.of("getWindow", "getHandle", "method_4490", "m_85439_"))
                    .orElseThrow().longValue();
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW", false, client.getClass().getClassLoader());
            glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class)
                    .invoke(null, handle, 0x00033001, captured ? 0x00034001 : 0x00034003);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("gui-mouse-capture", exception);
        }
    }

    private void openClickGuiScreen(Object client) throws ReflectiveOperationException {
        if (clickGuiScreen != null) {
            return;
        }
        ClassLoader loader = client.getClass().getClassLoader();
        Class<?> screenClass = Class.forName("net.minecraft.class_437", false, loader);
        Class<?> textClass = Class.forName("net.minecraft.class_2561", false, loader);
        Class<?> auroraScreenClass = auroraScreenClass(screenClass, textClass);
        Constructor<?> constructor = auroraScreenClass.getDeclaredConstructor(textClass);
        constructor.setAccessible(true);
        Object screen = constructor.newInstance(literalText(textClass, "Aurora"));
        previousScreen = readField(client, "currentScreen", "screen", "field_1755", "y", "f_91080_").orElse(null);
        if (!invokeNullableCompatible(client, List.of("setScreen", "method_1507", "m_91152_"), screen, screenClass)) {
            previousScreen = null;
            throw new NoSuchMethodException("Minecraft setScreen method was not found");
        }
        clickGuiScreen = screen;
    }

    private void closeClickGuiScreen(Object client) throws ReflectiveOperationException {
        Object screen = clickGuiScreen;
        if (screen == null) {
            return;
        }
        Object currentScreen = readField(client, "currentScreen", "screen", "field_1755", "y", "f_91080_").orElse(null);
        if (currentScreen == screen) {
            Class<?> screenClass = screen.getClass().getSuperclass();
            if (!invokeNullableCompatible(client, List.of("setScreen", "method_1507", "m_91152_"), previousScreen, screenClass)) {
                throw new NoSuchMethodException("Minecraft setScreen method was not found");
            }
        }
        clickGuiScreen = null;
        previousScreen = null;
    }

    private static Class<?> auroraScreenClass(Class<?> screenClass, Class<?> textClass) throws ReflectiveOperationException {
        String generatedName = screenClass.getPackageName() + ".AuroraClickGuiScreenNoBlur";
        try {
            return Class.forName(generatedName, false, screenClass.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(screenClass, MethodHandles.lookup());
            return new ByteBuddy()
                    .subclass(screenClass)
                    .name(generatedName)
                    .method(ElementMatchers.namedOneOf(
                            "applyBlur", "blur", "method_57734", "method_48267", "m_280851_", "m_280593_"))
                    .intercept(StubMethod.INSTANCE)
                    .make()
                    .load(screenClass.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(lookup))
                    .getLoaded();
        }
    }

    private static Object literalText(Class<?> textClass, String value) throws ReflectiveOperationException {
        for (Method method : textClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != String.class || !textClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (!List.of("literal", "of", "method_43470", "method_30163").contains(method.getName())) {
                continue;
            }
            method.setAccessible(true);
            return method.invoke(null, value);
        }
        throw new NoSuchMethodException("Minecraft Text literal factory was not found");
    }

    private static boolean invokeNullableCompatible(Object target, List<String> methodNames, Object argument,
                                                    Class<?> argumentType) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isAssignableFrom(argumentType)) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, argument);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void suppressClickGuiGameplayInput() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object options = readField(client, "options", "gameOptions", "field_1690", "m", "f_91066_").orElse(null);
            if (options == null) {
                return;
            }
            for (String fieldName : List.of(
                    "keyAttack", "attackKey", "field_1886", "f_92087_",
                    "keyUse", "useKey", "field_1904", "f_92086_",
                    "keyPickItem", "pickItemKey", "field_1910", "f_92088_")) {
                Object key = readField(options, fieldName).orElse(null);
                if (key != null) {
                    invokeBooleanSetter(key, List.of("setDown", "setPressed", "method_23481", "m_90837_"), false);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @Override
    public boolean isKeyDown(int keyCode) {
        if (keyCode < 0) {
            return false;
        }
        try {
            Object client = minecraftClient().orElseThrow();
            Object window = invokeNoArgs(client, "getWindow", "method_22683", "m_91268_").orElseThrow();
            long handle = getNumberCompatible(window, List.of("getWindow", "getHandle", "method_4490", "m_85439_"))
                    .orElseThrow().longValue();
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW", false, client.getClass().getClassLoader());
            return ((Number) glfw.getMethod("glfwGetKey", long.class, int.class)
                    .invoke(null, handle, keyCode)).intValue() == 1;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("key-state", exception);
            return false;
        }
    }

    @Override
    public boolean hasOpenScreen() {
        try {
            return minecraftClient()
                    .map(client -> {
                        try {
                            return screenOpen(client);
                        } catch (IllegalAccessException ignored) {
                            return false;
                        }
                    })
                    .orElse(false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public boolean isLocalPlayer(Object entity) {
        if (entity == null) {
            return false;
        }
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            return entity == player;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("local-player", exception);
            return false;
        }
    }

    @Override
    public boolean applyCameraRotation(Object camera, float yaw, float pitch) {
        if (camera == null) {
            return false;
        }
        for (Method method : camera.getClass().getMethods()) {
            if (!List.of("setRotation", "method_19325", "m_90572_", "a").contains(method.getName())
                    || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if ((parameters[0] != float.class && parameters[0] != Float.class)
                    || (parameters[1] != float.class && parameters[1] != Float.class)) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(camera, yaw, pitch);
                return true;
            } catch (ReflectiveOperationException exception) {
                reportOnce("camera-rotation", exception);
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean applyEntityRotation(Object entity, float yaw, float pitch) {
        if (entity == null) {
            return false;
        }
        try {
            boolean yawApplied = invokeVoidNumberCompatible(entity,
                    List.of("setYaw", "setYRot", "method_36456", "m_146922_"), yaw)
                    || setNumberField(entity, List.of("yaw", "yRot", "field_6031", "f_19857_"), yaw);
            boolean pitchApplied = invokeVoidNumberCompatible(entity,
                    List.of("setPitch", "setXRot", "method_36457", "m_146926_"), pitch)
                    || setNumberField(entity, List.of("pitch", "xRot", "field_5965", "f_19858_"), pitch);
            return yawApplied || pitchApplied;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("entity-rotation", exception);
            return false;
        }
    }

    @Override
    public boolean attackTarget(String targetId) {
        if (targetId == null) {
            return false;
        }
        try {
            Object target = aimTargetEntities.get(targetId);
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            Object interactionManager = readField(client, "interactionManager", "gameMode", "field_1761", "s", "f_91072_").orElse(null);
            if (target == null || player == null || interactionManager == null) {
                return false;
            }
            boolean attacked = attackEntity(interactionManager, player, target);
            if (attacked) {
                swingMainHand(player);
            }
            return attacked;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("attack-target", exception);
            return false;
        }
    }

    static boolean attackEntity(Object interactionManager, Object player, Object target) {
        if (interactionManager == null || player == null || target == null) {
            return false;
        }
        return invokeTwoCompatible(interactionManager,
                List.of("attackEntity", "attack", "method_2918", "m_105223_", "a"), player, target);
    }

    @Override
    public boolean setCrosshairTarget(String targetId) {
        if (targetId == null) {
            return false;
        }
        try {
            Object entity = aimTargetEntities.get(targetId);
            if (entity == null) {
                return false;
            }
            Object client = minecraftClient().orElseThrow();
            Object hit = newEntityHitResult(entity);
            if (hit == null) {
                return false;
            }
            // doAttack/doItemUse read this field; pointing it at the silently-aimed entity makes the
            // real click act on that entity regardless of where the player is visually looking.
            return writeField(client, hit, "crosshairTarget", "hitResult", "field_1765", "f_91077_");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("crosshair-target", exception);
            return false;
        }
    }

    @Override
    public boolean doAttack() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object result = invokeDeclaredResult(client, List.of("doAttack", "startAttack", "method_1536"));
            if (result == NOT_INVOKED) {
                return false;
            }
            // doAttack returns a hit boolean on modern versions and void on older ones; treat a
            // successful void call as a hit.
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (RuntimeException exception) {
            reportOnce("attack", exception);
            return false;
        }
    }

    @Override
    public boolean doItemUse() {
        try {
            Object client = minecraftClient().orElseThrow();
            return invokeDeclaredResult(client, List.of("doItemUse", "startUseItem", "method_1583")) != NOT_INVOKED;
        } catch (RuntimeException exception) {
            reportOnce("item-use", exception);
            return false;
        }
    }

    private static Object newEntityHitResult(Object entity) {
        ClassLoader loader = entity.getClass().getClassLoader();
        for (String className : List.of(
                "net.minecraft.util.hit.EntityHitResult",
                "net.minecraft.world.phys.EntityHitResult",
                "net.minecraft.class_3966")) {
            try {
                Class<?> hitClass = Class.forName(className, false, loader);
                for (Constructor<?> constructor : hitClass.getDeclaredConstructors()) {
                    if (constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].isInstance(entity)) {
                        constructor.setAccessible(true);
                        return constructor.newInstance(entity);
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    @Override
    public Optional<BlockHitTarget> crosshairBlock() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object hitResult = readField(client, "crosshairTarget", "hitResult", "field_1765", "f_91077_").orElse(null);
            if (hitResult == null || !isBlockHitResult(hitResult)) {
                return Optional.empty();
            }
            Object blockPos = invokeNoArgs(hitResult, "getBlockPos", "method_17777", "m_82425_").orElse(null);
            Object side = invokeNoArgs(hitResult, "getSide", "method_17780", "m_82434_").orElse(null);
            Vec3 hitPoint = vec3(invokeNoArgs(hitResult, "getPos", "method_17784", "m_82450_").orElse(null)).orElse(null);
            if (blockPos == null || side == null || hitPoint == null) {
                return Optional.empty();
            }
            BlockFace face = blockFace(side).orElse(null);
            BlockPosition pos = blockPosition(blockPos).orElse(null);
            if (face == null || pos == null) {
                return Optional.empty();
            }
            return Optional.of(new BlockHitTarget(pos.x(), pos.y(), pos.z(), face, hitPoint));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("crosshair-block", exception);
            return Optional.empty();
        }
    }

    @Override
    public BlockType blockType(int x, int y, int z) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object world = readField(client, "world", "level", "field_1687", "r", "f_91073_").orElse(null);
            if (world == null) {
                return BlockType.OTHER;
            }
            Object state = invokeCompatible(world, List.of("getBlockState", "method_8320", "m_8055_"), newBlockPos(x, y, z)).orElse(null);
            if (state == null) {
                return BlockType.OTHER;
            }
            if (invokeBooleanNoArgs(state, List.of("isAir", "method_26215", "m_60795_")).orElse(false)) {
                return BlockType.AIR;
            }
            Object block = invokeNoArgs(state, "getBlock", "method_26204", "m_60734_").orElse(null);
            if (block == null) {
                return BlockType.OTHER;
            }
            if (block == resolveBlock("OBSIDIAN").orElse(null)) {
                return BlockType.OBSIDIAN;
            }
            if (block == resolveBlock("BEDROCK").orElse(null)) {
                return BlockType.BEDROCK;
            }
            return BlockType.OTHER;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("block-type", exception);
            return BlockType.OTHER;
        }
    }

    @Override
    public boolean hasEntityCollision(Vec3 min, Vec3 max) {
        if (min == null || max == null) {
            return false;
        }
        try {
            Object client = minecraftClient().orElseThrow();
            Object world = readField(client, "world", "level", "field_1687", "r", "f_91073_").orElse(null);
            if (world == null) {
                return false;
            }
            for (Object entity : entitiesInBox(world, newBox(min, max), null)) {
                if (!isRemoved(entity)) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("entity-collision", exception);
            return false;
        }
    }

    @Override
    public Optional<AimTarget> nearestEndCrystal(Vec3 min, Vec3 max, Vec3 referencePoint) {
        if (min == null || max == null || referencePoint == null) {
            return Optional.empty();
        }
        try {
            Object client = minecraftClient().orElseThrow();
            Object world = readField(client, "world", "level", "field_1687", "r", "f_91073_").orElse(null);
            if (world == null) {
                return Optional.empty();
            }
            Object box = newBox(min, max);
            Object crystalClass = resolveClass(END_CRYSTAL_CLASSES).orElse(null);
            if (!(crystalClass instanceof Class<?> crystalType)) {
                return Optional.empty();
            }
            Object closest = null;
            double bestDistance = Double.MAX_VALUE;
            for (Object entity : entitiesInBox(world, box, crystalType)) {
                if (entity == null || isRemoved(entity)) {
                    continue;
                }
                double entityX = x(entity).orElse(Double.NaN);
                double entityY = y(entity).orElse(Double.NaN);
                double entityZ = z(entity).orElse(Double.NaN);
                if (!Double.isFinite(entityX) || !Double.isFinite(entityY) || !Double.isFinite(entityZ)) {
                    continue;
                }
                double distance = squaredDistance(referencePoint.x(), referencePoint.y(), referencePoint.z(), entityX, entityY, entityZ);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    closest = entity;
                }
            }
            if (closest == null) {
                return Optional.empty();
            }
            String id = targetId(closest);
            synchronized (aimTargetEntities) {
                aimTargetEntities.put(id, closest);
            }
            double width = width(closest);
            double height = height(closest);
            Vec3 targetPoint = new Vec3(x(closest).orElse(0.0D), y(closest).orElse(0.0D) + height * 0.5D, z(closest).orElse(0.0D));
            return Optional.of(new AimTarget(
                    id,
                    targetName(closest),
                    targetPoint,
                    bestDistance,
                    0.0D,
                    0.0D,
                    Math.max(1.0D, width * height)
            ));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("nearest-end-crystal", exception);
            return Optional.empty();
        }
    }

    @Override
    public int findHotbarItem(ItemType item) {
        if (item == null) {
            return -1;
        }
        try {
            Object player = currentPlayer().orElse(null);
            Object inventory = player == null ? null : playerInventory(player);
            Object targetItem = resolveItem(item).orElse(null);
            if (inventory == null || targetItem == null) {
                return -1;
            }
            for (int slot = 0; slot < 9; slot++) {
                Object stack = invokeNumericCompatible(inventory, List.of("getStack", "method_5438", "m_8020_"), slot).orElse(null);
                Object stackItem = stack == null ? null : invokeNoArgs(stack, "getItem", "method_7909", "m_41720_").orElse(null);
                if (stackItem == targetItem) {
                    return slot;
                }
            }
            return -1;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("find-hotbar-item", exception);
            return -1;
        }
    }

    @Override
    public boolean isHoldingItem(ItemType item) {
        if (item == null) {
            return false;
        }
        try {
            Object player = currentPlayer().orElse(null);
            Object inventory = player == null ? null : playerInventory(player);
            Object targetItem = resolveItem(item).orElse(null);
            int slot = selectedHotbarSlot();
            if (inventory == null || targetItem == null || slot < 0 || slot > 8) {
                return false;
            }
            Object stack = invokeNumericCompatible(inventory, List.of("getStack", "method_5438", "m_8020_"), slot).orElse(null);
            Object stackItem = stack == null ? null : invokeNoArgs(stack, "getItem", "method_7909", "m_41720_").orElse(null);
            return stackItem == targetItem;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("holding-item", exception);
            return false;
        }
    }

    @Override
    public int selectedHotbarSlot() {
        try {
            Object player = currentPlayer().orElse(null);
            Object inventory = player == null ? null : playerInventory(player);
            if (inventory == null) {
                return -1;
            }
            return readField(inventory, INVENTORY_SELECTED_SLOT_FIELD_NAMES.toArray(String[]::new))
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::intValue)
                    .orElse(-1);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("selected-hotbar-slot", exception);
            return -1;
        }
    }

    @Override
    public boolean selectHotbarSlot(int slot) {
        if (slot < 0 || slot > 8) {
            return false;
        }
        try {
            Object player = currentPlayer().orElse(null);
            Object inventory = player == null ? null : playerInventory(player);
            if (inventory == null) {
                return false;
            }
            if (selectedHotbarSlot() == slot) {
                return true;
            }
            boolean changed = selectHotbarSlotField(inventory, slot);
            sendSelectedSlotPacket(player, slot);
            return changed || selectedHotbarSlot() == slot;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("select-hotbar-slot", exception);
            return false;
        }
    }

    @Override
    public int hotbarEnchantmentLevel(int slot, EnchantmentType type) {
        if (type == null || slot < 0 || slot > 8) {
            return 0;
        }
        try {
            Object player = currentPlayer().orElse(null);
            Object inventory = player == null ? null : playerInventory(player);
            if (inventory == null) {
                return 0;
            }
            Object stack = invokeNumericCompatible(inventory, List.of("getStack", "method_5438", "m_8020_"), slot).orElse(null);
            if (stack == null) {
                return 0;
            }
            Object component = invokeNoArgs(stack, "getEnchantments").orElse(null);
            if (component == null) {
                return 0;
            }
            Object entries = invokeNoArgs(component, "getEnchantmentEntries").orElse(null);
            if (!(entries instanceof Iterable<?> iterable)) {
                return 0;
            }
            for (Object entry : iterable) {
                int level = enchantmentEntryLevel(entry, type.path());
                if (level > 0) {
                    return level;
                }
            }
            return 0;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("hotbar-enchantment-level", exception);
            return 0;
        }
    }

    /** Reads one fastutil {@code Object2IntMap.Entry<RegistryEntry<Enchantment>>} from an {@code
     * ItemEnchantmentsComponent}: {@code getKey()}/{@code getIntValue()} are fastutil's own stable
     * API, never remapped; only the two Minecraft-specific hops (registry entry -> key -> id path)
     * need mapping-tolerant lookups. */
    private static int enchantmentEntryLevel(Object entry, String enchantmentPath) throws ReflectiveOperationException {
        Object registryEntry = invokeNoArgs(entry, "getKey").orElse(null);
        Object levelValue = invokeNoArgs(entry, "getIntValue").orElse(null);
        if (registryEntry == null || !(levelValue instanceof Number levelNumber) || levelNumber.intValue() <= 0) {
            return 0;
        }
        Object keyOptional = invokeNoArgs(registryEntry, "getKey", "unwrapKey").orElse(null);
        Object registryKey = keyOptional instanceof Optional<?> optional ? optional.orElse(null) : null;
        if (registryKey == null) {
            return 0;
        }
        Object identifier = invokeNoArgs(registryKey, "getValue", "location").orElse(null);
        Object path = identifier == null ? null : invokeNoArgs(identifier, "getPath").orElse(null);
        return enchantmentPath.equals(path) ? levelNumber.intValue() : 0;
    }

    @Override
    public boolean isTargetBlockingWithShield(String targetId) {
        if (targetId == null) {
            return false;
        }
        try {
            Object entity = aimTargetEntities.get(targetId);
            if (entity == null || !isUsableTarget(entity)) {
                return false;
            }
            boolean blocking = invokeBooleanNoArgs(entity,
                    List.of("isBlocking", "method_6039", "fG")).orElse(false);
            if (!blocking) {
                return false;
            }
            Object activeItem = invokeNoArgs(entity,
                    "getActiveItem", "getUseItem", "method_6030", "fB").orElse(null);
            if (activeItem == null) {
                return false;
            }
            Object shieldItem = resolveItem(ItemType.SHIELD).orElse(null);
            Object stackItem = invokeNoArgs(activeItem, "getItem", "method_7909", "m_41720_").orElse(null);
            return shieldItem != null && shieldItem == stackItem;
        } catch (RuntimeException exception) {
            reportOnce("target-blocking-shield", exception);
            return false;
        }
    }

    @Override
    public boolean isTargetHoldingShield(String targetId) {
        if (targetId == null) {
            return false;
        }
        try {
            Object entity = aimTargetEntities.get(targetId);
            if (entity == null || !isUsableTarget(entity)) {
                return false;
            }
            Object shield = resolveItem(ItemType.SHIELD).orElse(null);
            if (shield == null) {
                return false;
            }
            Object mainHand = invokeNoArgs(entity,
                    "getMainHandItem", "getMainHandStack", "method_6047", "eZ").orElse(null);
            Object offHand = invokeNoArgs(entity,
                    "getOffhandItem", "getOffHandStack", "method_6079", "fa").orElse(null);
            return stackContainsItem(mainHand, shield) || stackContainsItem(offHand, shield);
        } catch (RuntimeException exception) {
            reportOnce("target-holding-shield", exception);
            return false;
        }
    }

    private static boolean stackContainsItem(Object stack, Object expectedItem) {
        if (stack == null || expectedItem == null) {
            return false;
        }
        Object item = invokeNoArgs(stack, "getItem", "method_7909", "m_41720_", "h").orElse(null);
        return item == expectedItem;
    }

    @Override
    public boolean useItemOnBlock(BlockHitTarget hit) {
        if (hit == null) {
            return false;
        }
        try {
            Object client = minecraftClient().orElseThrow();
            Object previous = readField(client, "crosshairTarget", "hitResult", "field_1765", "f_91077_").orElse(null);
            Object blockHit = newBlockHitResult(hit);
            if (blockHit == null) {
                return false;
            }
            writeField(client, blockHit, "crosshairTarget", "hitResult", "field_1765", "f_91077_");
            try {
                return doItemUse();
            } finally {
                if (previous != null) {
                    writeField(client, previous, "crosshairTarget", "hitResult", "field_1765", "f_91077_");
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("use-item-on-block", exception);
            return false;
        }
    }

    @Override
    public double attackCooldown() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            return player == null ? 0.0D : invokeNumberCompatible(player,
                    List.of("getAttackCooldownProgress", "getAttackStrengthScale", "method_7261", "m_36403_"), 0.0F).orElse(0.0D);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("attack-cooldown", exception);
            return 0.0D;
        }
    }

    @Override
    public boolean isMouseButtonDown(int button) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object window = invokeNoArgs(client, "getWindow", "method_22683", "m_91268_").orElseThrow();
            long handle = getNumberCompatible(window, List.of("getWindow", "getHandle", "method_4490", "m_85439_"))
                    .orElseThrow().longValue();
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW", false, client.getClass().getClassLoader());
            return ((Number) glfw.getMethod("glfwGetMouseButton", long.class, int.class)
                    .invoke(null, handle, button)).intValue() == 1;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("mouse-button", exception);
            return false;
        }
    }

    @Override
    public boolean isUsingItem() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            return player != null && invokeBooleanNoArgs(player,
                    List.of("isUsingItem", "isUsingItem", "method_6115", "m_6117_")).orElse(false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("using-item", exception);
            return false;
        }
    }

    @Override
    public boolean steerMovementInput(Object input, float visualYaw, float silentYaw) {
        if (input == null) {
            return false;
        }
        try {
            Object playerInput = readField(input, "playerInput", "field_54155", "a").orElse(null);
            if (playerInput == null) {
                return false;
            }
            DecoupledMovementSteering.Keys raw = new DecoupledMovementSteering.Keys(
                    booleanComponent(playerInput, "forward", "comp_3159", "a"),
                    booleanComponent(playerInput, "backward", "comp_3160", "b"),
                    booleanComponent(playerInput, "left", "comp_3161", "c"),
                    booleanComponent(playerInput, "right", "comp_3162", "d"),
                    booleanComponent(playerInput, "jump", "comp_3163", "e"),
                    booleanComponent(playerInput, "sneak", "comp_3164", "f"),
                    booleanComponent(playerInput, "sprint", "comp_3165", "g")
            );
            DecoupledMovementSteering.Keys steered = DecoupledMovementSteering.steer(
                    raw, AimMath.wrapDegrees(visualYaw - silentYaw));
            if (steered == null || steered.equals(raw)) {
                return false;
            }
            Constructor<?> constructor = playerInput.getClass().getDeclaredConstructor(
                    boolean.class, boolean.class, boolean.class, boolean.class,
                    boolean.class, boolean.class, boolean.class);
            constructor.setAccessible(true);
            Object replacement = constructor.newInstance(
                    steered.forward(), steered.backward(), steered.left(), steered.right(),
                    steered.jump(), steered.sneak(), steered.sprint());
            if (!writeField(input, replacement, "playerInput", "field_54155", "a")) {
                return false;
            }
            writeNumberField(input, (float) axis(steered.left(), steered.right()),
                    "movementSideways", "field_3907", "b");
            writeNumberField(input, (float) axis(steered.forward(), steered.backward()),
                    "movementForward", "field_3905", "c");
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("movement-steering", exception);
            return false;
        }
    }

    @Override
    public boolean swingMainHand() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            if (player == null) {
                return false;
            }
            swingMainHand(player);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("swing", exception);
            return false;
        }
    }

    @Override
    public boolean stopUsingItem() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            Object interactionManager = readField(client, "interactionManager", "gameMode", "field_1761", "s", "f_91072_").orElse(null);
            return player != null && interactionManager != null && invokeOneCompatible(interactionManager,
                    List.of("stopUsingItem", "releaseUsingItem", "method_2897", "m_105251_"), player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("stop-using-item", exception);
            return false;
        }
    }

    @Override
    public boolean fill(Object renderContext, int left, int top, int right, int bottom, int color) {
        if (renderContext == null) {
            return false;
        }
        // Renderers (e.g. TrailModule's scanline quad fill) can call this hundreds of times per frame,
        // so the resolved Method is cached per render-context class instead of re-scanning every call.
        Method method = resolveFillMethod(renderContext.getClass());
        if (method == null) {
            return false;
        }
        try {
            method.invoke(renderContext, left, top, right, bottom, color);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Method resolveFillMethod(Class<?> contextClass) {
        Method cached = fillMethodCache.get(contextClass);
        if (cached != null) {
            return cached;
        }
        for (Method method : contextClass.getMethods()) {
            if (!List.of("fill", "method_25294", "a").contains(method.getName()) || method.getParameterCount() != 5) {
                continue;
            }
            if (!Arrays.stream(method.getParameterTypes()).allMatch(type -> type == int.class)) {
                continue;
            }
            method.setAccessible(true);
            fillMethodCache.put(contextClass, method);
            return method;
        }
        return null;
    }

    @Override
    public boolean drawText(Object renderContext, String text, int x, int y, int color) {
        if (renderContext == null || text == null) {
            return false;
        }
        try {
            Object client = minecraftClient().orElseThrow();
            Object textRenderer = readField(client, "font", "textRenderer", "field_1772", "h", "f_91062_").orElseThrow();
            for (Method method : renderContext.getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 5 && parameters[0].isInstance(textRenderer)
                        && parameters[1] == String.class && parameters[2] == int.class
                        && parameters[3] == int.class && parameters[4] == int.class) {
                    method.invoke(renderContext, textRenderer, text, x, y, color);
                    return true;
                }
                if (parameters.length == 6 && parameters[0].isInstance(textRenderer)
                        && parameters[1] == String.class && parameters[2] == int.class
                        && parameters[3] == int.class && parameters[4] == int.class
                        && parameters[5] == boolean.class) {
                    method.invoke(renderContext, textRenderer, text, x, y, color, true);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return false;
    }

    @Override
    public int textWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            Object client = minecraftClient().orElseThrow();
            Object textRenderer = readField(client, "font", "textRenderer", "field_1772", "h", "f_91062_").orElseThrow();
            Optional<Object> width = invokeCompatible(textRenderer, List.of("getWidth", "width", "method_1727", "m_92895_"), text);
            return width.filter(Number.class::isInstance).map(value -> ((Number) value).intValue()).orElse(0);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }
    }

    @Override
    public Optional<AimTarget> crosshairEntity(double range) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            if (player == null) {
                return Optional.empty();
            }
            Object hitResult = readField(client, "crosshairTarget", "hitResult", "field_1765", "f_91077_").orElse(null);
            if (hitResult == null || !isEntityHitResult(hitResult)) {
                return Optional.empty();
            }
            Object entity = invokeNoArgs(hitResult, "getEntity", "method_17782", "m_82443_").orElse(null);
            if (entity == null || entity == player || !isUsableTarget(entity)) {
                return Optional.empty();
            }
            double playerX = x(player).orElseThrow();
            double playerY = y(player).orElseThrow();
            double playerZ = z(player).orElseThrow();
            double targetX = x(entity).orElse(Double.NaN);
            double targetY = y(entity).orElse(Double.NaN);
            double targetZ = z(entity).orElse(Double.NaN);
            if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) {
                return Optional.empty();
            }
            Vec3 targetPoint = bodyAimPoint(targetX, targetY, targetZ, entity);
            double distanceSquared = targetPoint.squaredDistanceTo(new Vec3(playerX, playerY, playerZ));
            if (range > 0.0D && distanceSquared > range * range) {
                return Optional.empty();
            }
            double eyeY = playerY + eyeHeight(player);
            String id = targetId(entity);
            synchronized (aimTargetEntities) {
                aimTargetEntities.put(id, entity);
            }
            return Optional.of(new AimTarget(
                    id,
                    targetName(entity),
                    targetPoint,
                    distanceSquared,
                    yawTo(playerX, playerZ, targetPoint.x(), targetPoint.z()),
                    pitchTo(playerX, eyeY, playerZ, targetPoint.x(), targetPoint.y(), targetPoint.z()),
                    health(entity)
            ));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("crosshair-entity", exception);
            return Optional.empty();
        }
    }

    @Override
    public Optional<TargetPose> targetPose(String targetId) {
        if (targetId == null) {
            return Optional.empty();
        }
        Object entity = aimTargetEntities.get(targetId);
        if (entity == null || !isUsableTarget(entity)) {
            aimTargetEntities.remove(targetId);
            return Optional.empty();
        }
        double feetX = x(entity).orElse(Double.NaN);
        double feetY = y(entity).orElse(Double.NaN);
        double feetZ = z(entity).orElse(Double.NaN);
        if (!Double.isFinite(feetX) || !Double.isFinite(feetY) || !Double.isFinite(feetZ)) {
            return Optional.empty();
        }
        // Entities only move once per game tick, but this pose is used to draw boxes every render
        // frame; without interpolating toward the last-tick position the same way the vanilla model
        // renderer does, the box visibly stutters/lags behind the model between ticks.
        double prevFeetX = prevX(entity).orElse(Double.NaN);
        double prevFeetY = prevY(entity).orElse(Double.NaN);
        double prevFeetZ = prevZ(entity).orElse(Double.NaN);
        if (Double.isFinite(prevFeetX) && Double.isFinite(prevFeetY) && Double.isFinite(prevFeetZ)) {
            float partialTick = partialTick();
            feetX = lerp(partialTick, prevFeetX, feetX);
            feetY = lerp(partialTick, prevFeetY, feetY);
            feetZ = lerp(partialTick, prevFeetZ, feetZ);
        }
        return Optional.of(new TargetPose(feetX, feetY, feetZ, width(entity), height(entity)));
    }

    @Override
    public OptionalInt targetEntityId(String targetId) {
        if (targetId == null) {
            return OptionalInt.empty();
        }
        Object entity = aimTargetEntities.get(targetId);
        if (entity == null) {
            return OptionalInt.empty();
        }
        Optional<Double> id = getNumberCompatible(entity, List.of("getId", "method_5628", "m_19879_"));
        return id.isPresent() ? OptionalInt.of(id.get().intValue()) : OptionalInt.empty();
    }

    @Override
    public boolean isOnGround() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            return player != null && invokeBooleanNoArgs(player, List.of("isOnGround", "onGround", "method_24828")).orElse(false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("on-ground", exception);
            return false;
        }
    }

    @Override
    public boolean isSprinting() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            return player != null && invokeBooleanNoArgs(player, List.of("isSprinting", "method_5624")).orElse(false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("sprinting", exception);
            return false;
        }
    }

    @Override
    public boolean setSprinting(boolean sprinting) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            return player != null && invokeBooleanSetter(player,
                    List.of("setSprinting", "method_5728", "m_6858_"), sprinting);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("set-sprinting", exception);
            return false;
        }
    }

    @Override
    public CombatState combatState() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            if (player == null) return CombatState.unavailable();
            Vec3 velocity = velocity(player);
            return new CombatState(
                    true,
                    invokeBooleanNoArgs(player, List.of("isOnGround", "onGround", "method_24828")).orElse(false),
                    invokeBooleanNoArgs(player, List.of("isSprinting", "method_5624")).orElse(false),
                    readNumberField(player, List.of("fallDistance", "field_6017", "aa")).orElse(0.0D),
                    velocity.y(),
                    invokeBooleanNoArgs(player, List.of("isClimbing", "isClimbable", "method_6101")).orElse(false),
                    invokeBooleanNoArgs(player, List.of("isTouchingWater", "isInWater", "method_5799")).orElse(false),
                    invokeBooleanNoArgs(player, List.of("isInLava", "method_5771")).orElse(false),
                    false, false, false,
                    invokeBooleanNoArgs(player, List.of("hasVehicle", "hasControllingPassenger", "method_3144")).orElse(false));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("combat-state", exception);
            return CombatState.unavailable();
        }
    }

    @Override
    public Vec3 playerVelocity() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            return player == null ? Vec3.ZERO : velocity(player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return Vec3.ZERO;
        }
    }

    @Override
    public boolean setForwardKeyHeld(boolean held) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object options = readField(client, "options", "gameOptions", "field_1690", "m", "f_91066_").orElse(null);
            Object key = options == null ? null : readField(options, FORWARD_KEY_FIELD_NAMES.toArray(String[]::new)).orElse(null);
            return key != null && invokeBooleanSetter(key, KEY_PRESSED_METHOD_NAMES, held);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("forward-key", exception);
            return false;
        }
    }

    @Override
    public boolean isForwardKeyPhysicallyDown() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object options = readField(client, "options", "gameOptions", "field_1690", "m", "f_91066_").orElse(null);
            Object key = options == null ? null : readField(options, FORWARD_KEY_FIELD_NAMES.toArray(String[]::new)).orElse(null);
            return key != null && invokeBooleanNoArgs(key,
                    List.of("isDown", "isPressed", "method_1434", "m_90857_")).orElse(false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean setJumpKeyHeld(boolean held) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object options = readField(client, "options", "gameOptions", "field_1690", "m", "f_91066_").orElse(null);
            return options != null && setJumpKeyHeld(options, held);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("jump-key", exception);
            return false;
        }
    }

    static boolean setJumpKeyHeld(Object options, boolean held) throws IllegalAccessException {
        Object key = readField(options, JUMP_KEY_FIELD_NAMES.toArray(String[]::new)).orElse(null);
        return key != null && invokeBooleanSetter(key, KEY_PRESSED_METHOD_NAMES, held);
    }

    @Override
    public boolean setUseKeyHeld(boolean held) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object options = readField(client, "options", "gameOptions", "field_1690", "m", "f_91066_").orElse(null);
            return options != null && setUseKeyHeld(options, held);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("use-key", exception);
            return false;
        }
    }

    static boolean setUseKeyHeld(Object options, boolean held) throws IllegalAccessException {
        Object key = readField(options, USE_KEY_FIELD_NAMES.toArray(String[]::new)).orElse(null);
        return key != null && invokeBooleanSetter(key, KEY_PRESSED_METHOD_NAMES, held);
    }

    @Override
    public int localEntityId() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            if (player == null) {
                return -1;
            }
            return getNumberCompatible(player, List.of("getId", "method_5628", "m_19879_")).orElse(-1.0D).intValue();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("entity-id", exception);
            return -1;
        }
    }

    @Override
    public Vec3 playerPosition() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            if (player == null) {
                return Vec3.ZERO;
            }
            return new Vec3(x(player).orElse(0.0D), y(player).orElse(0.0D), z(player).orElse(0.0D));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("player-position", exception);
            return Vec3.ZERO;
        }
    }

    @Override
    public boolean clearJumpCooldown() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            return player != null && clearJumpCooldownField(player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("jump-cooldown", exception);
            return false;
        }
    }

    static boolean clearJumpCooldownField(Object player) throws IllegalAccessException {
        return setNumberField(player, JUMP_COOLDOWN_FIELD_NAMES, 0.0D);
    }

    @Override
    public Optional<Double> gamma() {
        try {
            Object option = gammaOption().orElse(null);
            if (option == null) {
                return Optional.empty();
            }
            // Read the same backing field setGamma writes, so the value round-trips symmetrically
            // (a Fullbright override can be captured and later restored exactly).
            Object value = readField(option, "value", "field_37868").orElse(null);
            if (value instanceof Number number) {
                return Optional.of(number.doubleValue());
            }
            value = invokeNoArgs(option, "getValue", "get", "method_41753").orElse(null);
            return value instanceof Number number ? Optional.of(number.doubleValue()) : Optional.empty();
        } catch (IllegalAccessException | RuntimeException exception) {
            reportOnce("gamma", exception);
            return Optional.empty();
        }
    }

    @Override
    public boolean setGamma(double value) {
        try {
            Object option = gammaOption().orElse(null);
            // Write OptionInstance's backing value field directly: its setter validates against the
            // 0..1 gamma range, which would clamp away a Fullbright override. The field is typed
            // Object, so writeField (not the numeric-only writer) is what matches it.
            return option != null && writeField(option, Double.valueOf(value), "value", "field_37868");
        } catch (IllegalAccessException | RuntimeException exception) {
            reportOnce("set-gamma", exception);
            return false;
        }
    }

    private static Optional<Object> gammaOption() {
        Object client = minecraftClient().orElse(null);
        if (client == null) {
            return Optional.empty();
        }
        try {
            Object options = readField(client, "options", "gameOptions", "field_1690", "m", "f_91066_").orElse(null);
            if (options == null) {
                return Optional.empty();
            }
            Object option = invokeNoArgs(options, "getGamma", "gamma", "method_42473").orElse(null);
            if (option != null) {
                return Optional.of(option);
            }
            return readField(options, "gamma", "field_1840");
        } catch (IllegalAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public int itemUseCooldown() {
        Object client = minecraftClient().orElse(null);
        if (client == null) {
            return -1;
        }
        try {
            return readField(client, "itemUseCooldown", "rightClickDelay", "field_1752", "f_91078_")
                    .filter(Number.class::isInstance)
                    .map(value -> ((Number) value).intValue())
                    .orElse(-1);
        } catch (IllegalAccessException exception) {
            reportOnce("item-use-cooldown", exception);
            return -1;
        }
    }

    @Override
    public boolean setItemUseCooldown(int ticks) {
        Object client = minecraftClient().orElse(null);
        if (client == null) {
            return false;
        }
        try {
            return writeNumberField(client, ticks, "itemUseCooldown", "rightClickDelay", "field_1752", "f_91078_");
        } catch (IllegalAccessException exception) {
            reportOnce("set-item-use-cooldown", exception);
            return false;
        }
    }

    @Override
    public boolean isHoldingBlockItem() {
        try {
            Object item = heldItem().orElse(null);
            if (item == null) {
                return false;
            }
            Object blockItemClass = resolveClass(BLOCK_ITEM_CLASSES).orElse(null);
            return blockItemClass instanceof Class<?> type && type.isInstance(item);
        } catch (RuntimeException exception) {
            reportOnce("holding-block-item", exception);
            return false;
        }
    }

    @Override
    public boolean isSneaking() {
        try {
            Object player = currentPlayer().orElse(null);
            return player != null && invokeBooleanNoArgs(player,
                    List.of("isSneaking", "isShiftKeyDown", "method_5715", "m_6144_")).orElse(false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("sneaking", exception);
            return false;
        }
    }

    @Override
    public Optional<String> heldItemId() {
        try {
            Object item = heldItem().orElse(null);
            if (item == null) {
                return Optional.empty();
            }
            Object key = invokeNoArgs(item, "getTranslationKey", "getDescriptionId", "method_7876", "m_5671_").orElse(null);
            return key instanceof String string ? Optional.of(string) : Optional.empty();
        } catch (RuntimeException exception) {
            reportOnce("held-item-id", exception);
            return Optional.empty();
        }
    }

    @Override
    public boolean isBlockSolidAt(int x, int y, int z) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object world = readField(client, "world", "level", "field_1687", "r", "f_91073_").orElse(null);
            if (world == null) {
                return false;
            }
            Object pos = newBlockPos(x, y, z);
            Object state = invokeCompatible(world, List.of("getBlockState", "method_8320", "m_8055_"), pos).orElse(null);
            if (state == null) {
                return false;
            }
            if (invokeBooleanNoArgs(state, List.of("isAir", "method_26215", "m_60795_")).orElse(false)) {
                return false;
            }
            Object shape = invokeTwoResult(state, List.of("getCollisionShape", "method_26220", "m_60651_"), world, pos);
            if (shape == null) {
                // Couldn't resolve the collision shape; treat any non-air block as solid.
                return true;
            }
            return !invokeBooleanNoArgs(shape, List.of("isEmpty", "method_1110", "m_83281_")).orElse(false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("block-solid", exception);
            return false;
        }
    }

    @Override
    public List<EntityBox> nearbyEntityBoxes(Vec3 center, double range) {
        if (center == null || range <= 0.0D) {
            return List.of();
        }
        try {
            Object client = minecraftClient().orElseThrow();
            Object world = readField(client, "world", "level", "field_1687", "r", "f_91073_").orElse(null);
            if (world == null) {
                return List.of();
            }
            Vec3 min = center.add(-range, -range, -range);
            Vec3 max = center.add(range, range, range);
            Object box = newBox(min, max);
            List<EntityBox> boxes = new java.util.ArrayList<>();
            for (Object entity : entitiesInBox(world, box, null)) {
                if (entity == null || !isUsableTarget(entity) || isLocalPlayer(entity)) {
                    continue;
                }
                double entityX = x(entity).orElse(Double.NaN);
                double entityY = y(entity).orElse(Double.NaN);
                double entityZ = z(entity).orElse(Double.NaN);
                if (!Double.isFinite(entityX) || !Double.isFinite(entityY) || !Double.isFinite(entityZ)) {
                    continue;
                }
                double halfWidth = width(entity) / 2.0D;
                double height = height(entity);
                boxes.add(new EntityBox(
                        targetId(entity),
                        new Vec3(entityX - halfWidth, entityY, entityZ - halfWidth),
                        new Vec3(entityX + halfWidth, entityY + height, entityZ + halfWidth)));
            }
            return boxes;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("nearby-entity-boxes", exception);
            return List.of();
        }
    }

    @Override
    public int bestMiningToolSlot(int x, int y, int z) {
        try {
            Object client = minecraftClient().orElseThrow();
            Object world = readField(client, "world", "level", "field_1687", "r", "f_91073_").orElse(null);
            Object player = currentPlayer().orElse(null);
            Object inventory = player == null ? null : playerInventory(player);
            if (world == null || inventory == null) {
                return -1;
            }
            Object state = invokeCompatible(world, List.of("getBlockState", "method_8320", "m_8055_"), newBlockPos(x, y, z)).orElse(null);
            if (state == null) {
                return -1;
            }
            int selected = selectedHotbarSlot();
            int bestSlot = -1;
            // A bare hand's mining-speed multiplier is 1.0; only swap for a tool that beats it.
            double bestScore = 1.0D;
            for (int slot = 0; slot < 9; slot++) {
                Object stack = invokeNumericCompatible(inventory, List.of("getStack", "method_5438", "m_8020_"), slot).orElse(null);
                if (stack == null || invokeBooleanNoArgs(stack, List.of("isEmpty", "method_7960", "m_41619_")).orElse(true)) {
                    continue;
                }
                double speed = invokeCompatible(stack, List.of("getMiningSpeedMultiplier", "getDestroySpeed", "method_7924"), state)
                        .filter(Number.class::isInstance)
                        .map(value -> ((Number) value).doubleValue())
                        .orElse(1.0D);
                boolean suitable = invokeCompatible(stack, List.of("isSuitableFor", "isCorrectToolForDrops", "method_7951"), state)
                        .filter(Boolean.class::isInstance)
                        .map(Boolean.class::cast)
                        .orElse(false);
                double score = speed + (suitable ? 8.0D : 0.0D) + (slot == selected ? 0.05D : 0.0D);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
            return bestSlot;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("best-mining-tool", exception);
            return -1;
        }
    }

    /** The {@code Item} held in the main hand, resolved from the selected hotbar stack. */
    private static Optional<Object> heldItem() {
        try {
            Object player = currentPlayer().orElse(null);
            if (player == null) {
                return Optional.empty();
            }
            Object stack = invokeNoArgs(player, "getMainHandStack", "getMainHandItem", "method_6047", "m_21205_").orElse(null);
            if (stack == null) {
                return Optional.empty();
            }
            if (invokeBooleanNoArgs(stack, List.of("isEmpty", "method_7960", "m_41619_")).orElse(false)) {
                return Optional.empty();
            }
            return invokeNoArgs(stack, "getItem", "method_7909", "m_41720_");
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
        }
    }

    /** Invokes a two-argument instance method by any of its candidate names, returning its result. */
    private static Object invokeTwoResult(Object target, List<String> methodNames, Object first, Object second) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (!parameters[0].isInstance(first) || !parameters[1].isInstance(second)) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target, first, second);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public CameraPose cameraPose() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object player = readField(client, "player", "field_1724", "t", "f_91074_").orElse(null);
            if (player == null) {
                return CameraPose.unavailable();
            }
            int[] size = screenSize(client);
            if (size == null) {
                return CameraPose.unavailable();
            }
            // Prefer the game's real render Camera: its position and rotation already account for the
            // third-person offset and the F5 front-view yaw flip, so world-space overlays line up in
            // every camera mode. Falls back to the player's eye + look angles if it can't be read.
            CameraPose real = gameCameraPose(client, size);
            if (real != null) {
                return real;
            }
            Vec3 eye = new Vec3(x(player).orElseThrow(), y(player).orElseThrow() + eyeHeight(player), z(player).orElseThrow());
            float yaw;
            float pitch;
            if (DecoupledAimState.get().isActive()) {
                AimAngles visual = DecoupledAimState.get().visualAngles();
                yaw = visual.yaw();
                pitch = visual.pitch();
            } else {
                yaw = yaw(player).orElse(0.0D).floatValue();
                pitch = pitch(player).orElse(0.0D).floatValue();
            }
            return new CameraPose(true, eye, yaw, pitch, DEFAULT_FOV_DEGREES, size[0], size[1]);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportOnce("camera-pose", exception);
            return CameraPose.unavailable();
        }
    }

    /** Builds a pose from {@code gameRenderer.getCamera()}, or {@code null} if the camera or any of its
     * position/rotation accessors can't be resolved (in which case the caller falls back to the
     * player). */
    private static CameraPose gameCameraPose(Object client, int[] size) throws ReflectiveOperationException {
        Object gameRenderer = readField(client, "gameRenderer", "field_1773").orElse(null);
        if (gameRenderer == null) {
            return null;
        }
        Object camera = invokeNoArgs(gameRenderer, "getCamera", "method_19418").orElse(null);
        if (camera == null) {
            return null;
        }
        Object position = invokeNoArgs(camera, "getPos", "getPosition", "method_19326").orElse(null);
        Optional<Double> yaw = getNumberCompatible(camera, List.of("getYaw", "getYRot", "method_19330"));
        Optional<Double> pitch = getNumberCompatible(camera, List.of("getPitch", "getXRot", "method_19329"));
        if (position == null || yaw.isEmpty() || pitch.isEmpty()) {
            return null;
        }
        Optional<Double> px = vecComponent(position, "getX", "method_10216", "x", "field_1352");
        Optional<Double> py = vecComponent(position, "getY", "method_10214", "y", "field_1351");
        Optional<Double> pz = vecComponent(position, "getZ", "method_10215", "z", "field_1350");
        if (px.isEmpty() || py.isEmpty() || pz.isEmpty()) {
            return null;
        }
        Vec3 eye = new Vec3(px.get(), py.get(), pz.get());
        return new CameraPose(true, eye, yaw.get().floatValue(), pitch.get().floatValue(),
                DEFAULT_FOV_DEGREES, size[0], size[1]);
    }

    /** A component of a Vec3d, read via getter methods first and then public fields. */
    private static Optional<Double> vecComponent(Object vec, String getter, String intermediaryGetter,
                                                 String field, String intermediaryField) {
        Optional<Double> viaMethod = getNumberCompatible(vec, List.of(getter, intermediaryGetter));
        if (viaMethod.isPresent()) {
            return viaMethod;
        }
        try {
            return readField(vec, field, intermediaryField)
                    .filter(Number.class::isInstance)
                    .map(value -> ((Number) value).doubleValue());
        } catch (IllegalAccessException ignored) {
            return Optional.empty();
        }
    }

    /** Width/height of the current GL viewport in scaled GUI pixels, or {@code null} if unavailable.
     * Not pixel-precise FOV-wise — see {@link #DEFAULT_FOV_DEGREES} — but accurate enough to anchor a
     * debug-style overlay. */
    private static int[] screenSize(Object client) throws ReflectiveOperationException {
        Object window = invokeNoArgs(client, "getWindow", "method_22683", "m_91268_").orElseThrow();
        int screenWidth = getNumberCompatible(window, List.of("getGuiScaledWidth", "getScaledWidth", "method_4486", "m_85445_"))
                .orElseThrow().intValue();
        int screenHeight = getNumberCompatible(window, List.of("getGuiScaledHeight", "getScaledHeight", "method_4502", "m_85446_"))
                .orElseThrow().intValue();
        return new int[]{screenWidth, screenHeight};
    }

    private static boolean isEntityHitResult(Object hitResult) {
        return Set.of(
                "net.minecraft.util.hit.EntityHitResult",
                "net.minecraft.world.phys.EntityHitResult",
                "net.minecraft.class_3966"
        ).contains(hitResult.getClass().getName());
    }

    @Override
    public String environment() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (classPresent("net.fabricmc.loader.api.FabricLoader", loader)) {
            return "Fabric";
        }
        if (classPresent("org.quiltmc.loader.api.QuiltLoader", loader)) {
            return "Quilt";
        }
        if (classPresent("net.minecraftforge.fml.loading.FMLLoader", loader)) {
            return "Forge";
        }
        return "Vanilla/unknown";
    }

    private boolean setInteractionAttributes(Object player, double range) throws ReflectiveOperationException {
        boolean block = setInteractionAttribute(player, BLOCK_RANGE_FIELDS, range);
        boolean entity = setInteractionAttribute(player, ENTITY_RANGE_FIELDS, range);
        return block || entity;
    }

    private boolean setInteractionAttribute(Object player, List<String> fieldNames, double range) throws ReflectiveOperationException {
        Object attribute = findInteractionAttribute(fieldNames).orElse(null);
        if (attribute == null) {
            return false;
        }
        Object instance = invokeCompatible(player, List.of("getAttribute", "getAttributeInstance", "method_5996", "m_21051_", "g"), attribute).orElse(null);
        if (instance == null) {
            return false;
        }
        rememberOriginalReachValue(instance);
        return invokeVoidCompatible(instance, List.of("setBaseValue", "method_6192", "m_22100_", "a"), range);
    }

    private void rememberOriginalReachValue(Object attributeInstance) {
        synchronized (originalReachValues) {
            if (originalReachValues.containsKey(attributeInstance)) {
                return;
            }
            getDoubleCompatible(attributeInstance, List.of("getBaseValue", "method_6201", "m_22227_", "b"))
                    .ifPresent(value -> originalReachValues.put(attributeInstance, value));
        }
    }

    private static Optional<Object> currentPlayer() throws ReflectiveOperationException {
        Object client = minecraftClient().orElse(null);
        if (client == null) {
            return Optional.empty();
        }
        return readField(client, "player", "field_1724", "t", "f_91074_");
    }

    private static Object playerInventory(Object player) throws ReflectiveOperationException {
        Object inventory = invokeNoArgs(player, "getInventory", "method_31548", "m_150109_").orElse(null);
        if (inventory != null) {
            return inventory;
        }
        return readField(player, "inventory", "field_7514", "f_35971_").orElse(null);
    }

    private static Optional<Object> resolveItem(ItemType item) {
        for (String fieldName : item.fieldNames()) {
            Optional<Object> resolved = resolveStaticField(ITEMS_CLASSES, fieldName);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> resolveBlock(String fieldName) {
        return resolveStaticField(BLOCKS_CLASSES, fieldName);
    }

    private static Optional<Object> resolveClass(List<String> classNames) {
        for (String className : classNames) {
            try {
                return Optional.of(Class.forName(className, false, Thread.currentThread().getContextClassLoader()));
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> resolveStaticField(List<String> classNames, String fieldName) {
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.ofNullable(field.get(null));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

    private static Object newBlockPos(int x, int y, int z) throws ReflectiveOperationException {
        Class<?> blockPosClass = (Class<?>) resolveClass(BLOCK_POS_CLASSES).orElseThrow();
        for (Constructor<?> constructor : blockPosClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 3) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters[0] == int.class && parameters[1] == int.class && parameters[2] == int.class) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(x, y, z);
                }
            }
        }
        throw new NoSuchMethodException("No BlockPos(int,int,int) constructor found");
    }

    private static Object newVec3(Vec3 vector) throws ReflectiveOperationException {
        Class<?> vec3Class = (Class<?>) resolveClass(VEC3_CLASSES).orElseThrow();
        for (Constructor<?> constructor : vec3Class.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 3) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters[0] == double.class && parameters[1] == double.class && parameters[2] == double.class) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(vector.x(), vector.y(), vector.z());
                }
            }
        }
        throw new NoSuchMethodException("No Vec3(double,double,double) constructor found");
    }

    private static Object newBox(Vec3 min, Vec3 max) throws ReflectiveOperationException {
        Class<?> boxClass = (Class<?>) resolveClass(BOX_CLASSES).orElseThrow();
        for (Constructor<?> constructor : boxClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 6) {
                Class<?>[] parameters = constructor.getParameterTypes();
                boolean matches = true;
                for (Class<?> parameter : parameters) {
                    if (parameter != double.class) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
                }
            }
        }
        throw new NoSuchMethodException("No AABB/Box(double...) constructor found");
    }

    private static Object resolveDirection(BlockFace face) throws ReflectiveOperationException {
        Class<?> directionClass = (Class<?>) resolveClass(DIRECTION_CLASSES).orElseThrow();
        if (directionClass.isEnum()) {
            for (Object constant : directionClass.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(face.name())) {
                    return constant;
                }
            }
        }
        for (Field field : directionClass.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == directionClass && field.getName().equals(face.name())) {
                return field.get(null);
            }
        }
        throw new NoSuchFieldException("No direction constant for " + face);
    }

    private static Object newBlockHitResult(BlockHitTarget hit) throws ReflectiveOperationException {
        Class<?> hitClass = (Class<?>) resolveClass(BLOCK_HIT_RESULT_CLASSES).orElseThrow();
        Object hitPos = newVec3(hit.hitPoint());
        Object direction = resolveDirection(hit.face());
        Object blockPos = newBlockPos(hit.blockX(), hit.blockY(), hit.blockZ());
        for (Constructor<?> constructor : hitClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 4) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters[0].isInstance(hitPos) && parameters[1].isInstance(direction)
                        && parameters[2].isInstance(blockPos) && parameters[3] == boolean.class) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(hitPos, direction, blockPos, false);
                }
            }
        }
        throw new NoSuchMethodException("No BlockHitResult(Vec3,Direction,BlockPos,boolean) constructor found");
    }

    private static boolean isBlockHitResult(Object hitResult) {
        return BLOCK_HIT_RESULT_CLASSES.contains(hitResult.getClass().getName());
    }

    private static Optional<BlockFace> blockFace(Object direction) {
        if (direction == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(BlockFace.valueOf(((Enum<?>) direction).name()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<BlockPosition> blockPosition(Object blockPos) {
        if (blockPos == null) {
            return Optional.empty();
        }
        Optional<Double> x = getNumberCompatible(blockPos, List.of("getX", "method_10263", "m_123341_"));
        Optional<Double> y = getNumberCompatible(blockPos, List.of("getY", "method_10264", "m_123342_"));
        Optional<Double> z = getNumberCompatible(blockPos, List.of("getZ", "method_10260", "m_123343_"));
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPosition(x.get().intValue(), y.get().intValue(), z.get().intValue()));
    }

    private static Optional<Vec3> vec3(Object vector) {
        if (vector == null) {
            return Optional.empty();
        }
        Optional<Double> x = getNumberCompatible(vector, List.of("getX", "x", "method_10216", "m_7096_"));
        Optional<Double> y = getNumberCompatible(vector, List.of("getY", "y", "method_10214", "m_7098_"));
        Optional<Double> z = getNumberCompatible(vector, List.of("getZ", "z", "method_10215", "m_7094_"));
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Vec3(x.get(), y.get(), z.get()));
    }

    private static Collection<?> entitiesInBox(Object world, Object box, Class<?> entityType) throws ReflectiveOperationException {
        if (entityType != null) {
            Collection<?> byClass = entitiesByClass(world, entityType, box);
            if (!byClass.isEmpty()) {
                return byClass;
            }
        }
        return otherEntities(world, box);
    }

    private static Collection<?> entitiesByClass(Object world, Class<?> entityType, Object box) throws ReflectiveOperationException {
        for (Method method : world.getClass().getMethods()) {
            if (!List.of("getEntitiesByClass", "getEntitiesOfClass", "method_18470", "m_45976_").contains(method.getName())
                    || method.getParameterCount() != 3) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters[0] != Class.class || !parameters[1].isInstance(box)) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(world, entityType, box, (java.util.function.Predicate<Object>) entity -> true);
                if (result instanceof Collection<?> collection) {
                    return collection;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return List.of();
    }

    private static Collection<?> otherEntities(Object world, Object box) throws ReflectiveOperationException {
        for (Method method : world.getClass().getMethods()) {
            if (!List.of("getOtherEntities", "getEntities", "method_8335", "m_45933_").contains(method.getName())) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result;
                if (method.getParameterCount() == 2 && method.getParameterTypes()[1].isInstance(box)) {
                    result = method.invoke(world, null, box);
                } else if (method.getParameterCount() == 3 && method.getParameterTypes()[1].isInstance(box)) {
                    result = method.invoke(world, null, box, (java.util.function.Predicate<Object>) entity -> true);
                } else {
                    continue;
                }
                if (result instanceof Collection<?> collection) {
                    return collection;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return List.of();
    }

    private static void sendSelectedSlotPacket(Object player, int slot) {
        try {
            Object connection = readField(player,
                    "networkHandler", "connection", "field_3944", "f_36097_", "j").orElse(null);
            if (connection == null) {
                return;
            }
            Object packet = null;
            ClassLoader loader = player.getClass().getClassLoader();
            for (String className : List.of(
                    "net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket",
                    "net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket",
                    "net.minecraft.class_2868",
                    "ahx")) {
                try {
                    Class<?> packetClass = Class.forName(className, false, loader);
                    for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
                        if (constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0] == int.class) {
                            constructor.setAccessible(true);
                            packet = constructor.newInstance(slot);
                            break;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                }
                if (packet != null) {
                    break;
                }
            }
            if (packet != null) {
                invokeOneCompatible(connection,
                        List.of("sendPacket", "send", "method_10743", "m_9829_", "b"), packet);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private record BlockPosition(int x, int y, int z) {
    }

    private static Collection<?> players(Object world) throws IllegalAccessException {
        for (String methodName : List.of("getPlayers", "players", "method_18456", "m_6907_", "v")) {
            Optional<Object> value = invokeNoArgs(world, methodName);
            if (value.filter(Collection.class::isInstance).isPresent()) {
                return (Collection<?>) value.get();
            }
        }
        return readField(world, "players", "field_18226", "P", "f_46442_")
                .filter(Collection.class::isInstance)
                .map(Collection.class::cast)
                .orElse(List.of());
    }

    private static boolean screenOpen(Object client) throws IllegalAccessException {
        return readField(client, "currentScreen", "screen", "field_1755", "y", "f_91080_").isPresent();
    }

    private static double mouseSensitivity(Object client) throws IllegalAccessException {
        Object options = readField(client, "options", "gameOptions", "field_1690", "m", "f_91066_").orElse(null);
        if (options == null) {
            return 0.5D;
        }
        Object option = invokeNoArgs(options, "getMouseSensitivity", "method_42495", "m_231928_").orElse(null);
        if (option != null) {
            Optional<Double> value = getNumberCompatible(option, List.of("getValue", "method_41753", "m_231551_"));
            if (value.isPresent()) {
                return value.get();
            }
        }
        return getNumberCompatible(options, List.of("getMouseSensitivity", "method_42495", "m_231928_")).orElse(0.5D);
    }

    private static boolean isUsableTarget(Object target) {
        return !isRemoved(target) && isAlive(target);
    }

    private static boolean isRemoved(Object entity) {
        return invokeBooleanNoArgs(entity, List.of("isRemoved", "removed", "method_31481", "m_146910_")).orElse(false);
    }

    private static boolean isAlive(Object entity) {
        return invokeBooleanNoArgs(entity, List.of("isAlive", "method_5805", "m_6084_")).orElse(true);
    }

    private static boolean hasLineOfSight(Object player, Object target) {
        return invokeBooleanCompatible(player, List.of("canSee", "hasLineOfSight", "method_6057", "m_142582_"), target).orElse(false);
    }

    private static Optional<Double> x(Object entity) {
        return getNumberCompatible(entity, List.of("getX", "method_23317", "m_20185_"));
    }

    private static Optional<Double> y(Object entity) {
        return getNumberCompatible(entity, List.of("getY", "method_23318", "m_20186_"));
    }

    private static Optional<Double> z(Object entity) {
        return getNumberCompatible(entity, List.of("getZ", "method_23321", "m_20189_"));
    }

    private static Vec3 velocity(Object entity) {
        Object value = invokeNoArgs(entity, "getVelocity", "getDeltaMovement", "method_18798", "m_20184_").orElse(null);
        if (value == null) return Vec3.ZERO;
        // Extract components with the Vec3 accessors (x/getX/method_10216 ...), not the Entity
        // position getters used by x()/y()/z() — those never match a Vec3, so velocity read 0.0.
        return vec3(value).orElse(Vec3.ZERO);
    }

    private static Optional<Double> prevX(Object entity) {
        return readNumberField(entity, PREV_X_FIELD_NAMES);
    }

    private static Optional<Double> prevY(Object entity) {
        return readNumberField(entity, PREV_Y_FIELD_NAMES);
    }

    private static Optional<Double> prevZ(Object entity) {
        return readNumberField(entity, PREV_Z_FIELD_NAMES);
    }

    private static Optional<Double> readNumberField(Object target, List<String> fieldNames) {
        try {
            return readField(target, fieldNames.toArray(String[]::new))
                    .filter(Number.class::isInstance)
                    .map(value -> ((Number) value).doubleValue());
        } catch (IllegalAccessException exception) {
            return Optional.empty();
        }
    }

    /** Fraction of the current tick that has elapsed for rendering purposes (0 = last tick, 1 = this
     * tick), used to interpolate render-space poses the same way vanilla's entity renderer does.
     * Defaults to 1.0 (i.e. the raw current-tick position) if it can't be resolved. */
    private static float partialTick() {
        try {
            Object client = minecraftClient().orElseThrow();
            Object deltaTracker = invokeNoArgs(client, "getDeltaTracker", "method_61966")
                    .or(() -> {
                        try {
                            return readField(client, "deltaTracker", "field_52750");
                        } catch (IllegalAccessException exception) {
                            return Optional.empty();
                        }
                    })
                    .orElse(null);
            if (deltaTracker == null) {
                return 1.0F;
            }
            for (Method method : deltaTracker.getClass().getMethods()) {
                if (!GAME_TIME_DELTA_METHOD_NAMES.contains(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameter = method.getParameterTypes()[0];
                if (parameter != boolean.class && parameter != Boolean.class) {
                    continue;
                }
                method.setAccessible(true);
                Object result = method.invoke(deltaTracker, true);
                if (result instanceof Number number) {
                    return number.floatValue();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return 1.0F;
    }

    private static double lerp(float t, double start, double end) {
        return start + (end - start) * t;
    }

    private static Optional<Double> yaw(Object entity) {
        return getNumberCompatible(entity, List.of("getYaw", "getYRot", "method_36454", "m_146908_"));
    }

    private static Optional<Double> pitch(Object entity) {
        return getNumberCompatible(entity, List.of("getPitch", "getXRot", "method_36455", "m_146909_"));
    }

    private static double height(Object entity) {
        return getNumberCompatible(entity, List.of("getHeight", "method_17682", "m_20206_")).orElse(1.8D);
    }

    private static double width(Object entity) {
        return getNumberCompatible(entity, List.of("getWidth", "method_17681", "m_20205_")).orElse(0.6D);
    }

    private static double eyeHeight(Object entity) {
        return getNumberCompatible(entity, List.of("getEyeHeight", "method_5751", "m_20237_")).orElse(height(entity) * 0.85D);
    }

    private static double health(Object entity) {
        return getNumberCompatible(entity, List.of("getHealth", "method_6032", "m_21223_")).orElse(20.0D);
    }

    private static Vec3 bodyAimPoint(double x, double y, double z, Object target) {
        return new Vec3(x, y + height(target) * BODY_AIM_HEIGHT_RATIO, z);
    }

    private static String targetId(Object entity) {
        return readUuid(entity).map(UUID::toString).orElseGet(() -> targetName(entity) + "#" + System.identityHashCode(entity));
    }

    private static String targetName(Object entity) {
        Object text = invokeNoArgs(entity, "getName", "method_5477", "m_7755_").orElse(null);
        if (text != null) {
            Optional<Object> string = invokeNoArgs(text, "getString", "method_10851", "getContents");
            if (string.filter(String.class::isInstance).isPresent()) {
                return (String) string.get();
            }
        }
        Object profile = invokeNoArgs(entity, "getGameProfile", "method_7334", "m_36316_").orElse(null);
        if (profile != null) {
            Optional<Object> name = invokeNoArgs(profile, "getName");
            if (name.filter(String.class::isInstance).isPresent()) {
                return (String) name.get();
            }
        }
        return entity.getClass().getSimpleName();
    }

    private static double yawTo(double fromX, double fromZ, double toX, double toZ) {
        double deltaX = toX - fromX;
        double deltaZ = toZ - fromZ;
        return Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D;
    }

    private static double pitchTo(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double deltaX = toX - fromX;
        double deltaY = toY - fromY;
        double deltaZ = toZ - fromZ;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        return -Math.toDegrees(Math.atan2(deltaY, horizontal));
    }

    private static double squaredDistance(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double deltaX = toX - fromX;
        double deltaY = toY - fromY;
        double deltaZ = toZ - fromZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private static Optional<Object> findInteractionAttribute(List<String> fieldNames) throws IllegalAccessException {
        for (String className : ATTRIBUTE_CLASSES) {
            Class<?> attributesClass;
            try {
                attributesClass = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException ignored) {
                continue;
            }
            for (Field field : attributesClass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (fieldNames.contains(field.getName())) {
                    field.setAccessible(true);
                    return Optional.ofNullable(field.get(null));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> minecraftClient() {
        for (String className : CLIENT_CLASSES) {
            try {
                Class<?> clientClass = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                for (String methodName : List.of("getInstance", "method_1551", "m_91087_", "Q")) {
                    for (Method method : clientClass.getDeclaredMethods()) {
                        if (method.getParameterCount() == 0 && method.getName().equals(methodName)) {
                            method.setAccessible(true);
                            return Optional.ofNullable(method.invoke(null));
                        }
                    }
                }
                for (Field field : clientClass.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && field.getType() == clientClass) {
                        field.setAccessible(true);
                        return Optional.ofNullable(field.get(null));
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> findIntegratedServerPlayer(Object client, Object clientPlayer) throws ReflectiveOperationException {
        Object server = null;
        for (String methodName : List.of("getServer", "getSingleplayerServer", "method_1576", "m_91092_", "V")) {
            Optional<Object> value = invokeNoArgs(client, methodName);
            if (value.isPresent()) {
                server = value.get();
                break;
            }
        }
        if (server == null) {
            server = readField(client, "singleplayerServer", "server", "field_1766", "aS", "f_91007_").orElse(null);
        }
        if (server == null) {
            return Optional.empty();
        }
        Object playerList = null;
        for (String methodName : List.of("getPlayerManager", "getPlayerList", "method_3760", "m_6846_", "ag")) {
            Optional<Object> value = invokeNoArgs(server, methodName);
            if (value.isPresent()) {
                playerList = value.get();
                break;
            }
        }
        if (playerList == null) {
            return Optional.empty();
        }
        UUID uuid = readUuid(clientPlayer).orElse(null);
        if (uuid != null) {
            Object directPlayer = invokeCompatible(playerList, List.of("getPlayer", "method_14602", "m_11259_"), uuid).orElse(null);
            if (directPlayer != null) {
                return Optional.of(directPlayer);
            }
        }
        return findMatchingPlayerInCollections(playerList, uuid);
    }

    private static Optional<Object> invokeNoArgs(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(target));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

    private static boolean invokeNoArgsIfPresent(Object target, List<String> methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                method.invoke(target);
                return true;
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    private static Optional<Boolean> invokeBooleanNoArgs(Object target, List<String> methodNames) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 0) {
                continue;
            }
            if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                return Optional.of((Boolean) method.invoke(target));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Boolean> invokeBooleanCompatible(Object target, List<String> methodNames, Object argument) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (!parameter.isAssignableFrom(argument.getClass())) {
                continue;
            }
            try {
                method.setAccessible(true);
                return Optional.of((Boolean) method.invoke(target, argument));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean invokeBooleanSetter(Object target, List<String> methodNames, boolean argument) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != boolean.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, argument);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    private static Optional<Object> invokeCompatible(Object target, List<String> methodNames, Object argument) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (argument instanceof Double && parameter != double.class && parameter != Double.class) {
                continue;
            }
            if (!(argument instanceof Double) && !parameter.isAssignableFrom(argument.getClass())) {
                continue;
            }
            try {
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(target, argument));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> invokeNumericCompatible(Object target, List<String> methodNames, int argument) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1
                    || !isNumericType(method.getParameterTypes()[0])) {
                continue;
            }
            try {
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(target, castNumber(argument, method.getParameterTypes()[0])));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean invokeTwoCompatible(Object target, List<String> methodNames, Object first, Object second) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (!parameters[0].isInstance(first) || !parameters[1].isInstance(second)) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, first, second);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean invokeOneCompatible(Object target, List<String> methodNames, Object argument) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isInstance(argument)) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, argument);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    /** Sentinel returned by {@link #invokeDeclaredResult} when no matching method could be invoked. */
    private static final Object NOT_INVOKED = new Object();

    /**
     * Invokes a (possibly private) method by matching name and argument count, walking up the class
     * hierarchy. Used for input-pipeline methods such as {@code MinecraftClient.doAttack}, which are
     * not public and so are invisible to the {@code getMethods()}-based helpers above. Returns the
     * method's result (possibly {@code null} for void methods), or {@link #NOT_INVOKED} if nothing
     * matched or the call failed.
     */
    private static Object invokeDeclaredResult(Object target, List<String> methodNames, Object... args) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!methodNames.contains(method.getName()) || method.getParameterCount() != args.length) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                }
            }
        }
        return NOT_INVOKED;
    }

    private static Optional<Double> invokeNumberCompatible(Object target, List<String> methodNames, float argument) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1
                    || !isNumericType(method.getParameterTypes()[0]) || !isNumericType(method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(target, castNumber(argument, method.getParameterTypes()[0]));
                return Optional.of(((Number) result).doubleValue());
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static void swingMainHand(Object player) {
        ClassLoader loader = player.getClass().getClassLoader();
        for (String className : List.of(
                "net.minecraft.world.InteractionHand",
                "net.minecraft.util.Hand",
                "net.minecraft.class_1268")) {
            try {
                Class<?> handClass = Class.forName(className, false, loader);
                Object mainHand = null;
                if (handClass.isEnum()) {
                    for (Object constant : handClass.getEnumConstants()) {
                        if (((Enum<?>) constant).name().equals("MAIN_HAND")) {
                            mainHand = constant;
                            break;
                        }
                    }
                }
                if (mainHand == null) {
                    for (Field field : handClass.getFields()) {
                        if (Modifier.isStatic(field.getModifiers()) && field.getType() == handClass) {
                            mainHand = field.get(null);
                            break;
                        }
                    }
                }
                if (mainHand != null) {
                    invokeCompatible(player, List.of("swingHand", "swing", "method_6104", "m_6674_"), mainHand);
                    return;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static boolean invokeVoidCompatible(Object target, List<String> methodNames, double argument) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (parameter != double.class && parameter != Double.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, argument);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean invokeVoidNumberCompatible(Object target, List<String> methodNames, double argument) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (!isNumericType(parameter)) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, castNumber(argument, parameter));
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    private static Optional<Double> getDoubleCompatible(Object target, List<String> methodNames) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 0) {
                continue;
            }
            if (method.getReturnType() != double.class && method.getReturnType() != Double.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                return Optional.of(((Number) method.invoke(target)).doubleValue());
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Double> getNumberCompatible(Object target, List<String> methodNames) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodNames.contains(method.getName()) || method.getParameterCount() != 0) {
                continue;
            }
            if (!isNumericType(method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                return Optional.of(((Number) method.invoke(target)).doubleValue());
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean setNumberField(Object target, List<String> fieldNames, double value) throws IllegalAccessException {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!fieldNames.contains(field.getName()) || !isNumericType(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(target, castNumber(value, field.getType()));
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    static boolean selectHotbarSlotField(Object inventory, int slot) throws IllegalAccessException {
        return inventory != null && slot >= 0 && slot <= 8
                && setNumberField(inventory, INVENTORY_SELECTED_SLOT_FIELD_NAMES, slot);
    }

    private static boolean booleanComponent(Object target, String... methodNames) {
        return invokeBooleanNoArgs(target, Arrays.asList(methodNames)).orElse(false);
    }

    private static boolean writeField(Object target, Object value, String... fieldNames) throws IllegalAccessException {
        List<String> candidates = Arrays.asList(fieldNames);
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!candidates.contains(field.getName()) || value != null && !field.getType().isInstance(value)) {
                    continue;
                }
                field.setAccessible(true);
                field.set(target, value);
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean writeNumberField(Object target, double value, String... fieldNames) throws IllegalAccessException {
        return setNumberField(target, Arrays.asList(fieldNames), value);
    }

    private static double axis(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0D;
        }
        return positive ? 1.0D : -1.0D;
    }

    private static boolean isNumericType(Class<?> type) {
        return type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class;
    }

    private static Object castNumber(double value, Class<?> type) {
        if (type == byte.class || type == Byte.class) {
            return (byte) value;
        }
        if (type == short.class || type == Short.class) {
            return (short) value;
        }
        if (type == int.class || type == Integer.class) {
            return (int) value;
        }
        if (type == long.class || type == Long.class) {
            return (long) value;
        }
        if (type == float.class || type == Float.class) {
            return (float) value;
        }
        return value;
    }

    private static Optional<Object> readField(Object target, String... names) throws IllegalAccessException {
        List<String> candidates = Arrays.asList(names);
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (candidates.contains(field.getName())) {
                    field.setAccessible(true);
                    return Optional.ofNullable(field.get(target));
                }
            }
            type = type.getSuperclass();
        }
        return Optional.empty();
    }

    private static Optional<UUID> readUuid(Object entity) {
        for (String methodName : List.of("getUUID", "getUuid", "method_5667", "m_20148_")) {
            Optional<Object> value = invokeNoArgs(entity, methodName);
            if (value.filter(UUID.class::isInstance).isPresent()) {
                return Optional.of((UUID) value.get());
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> findMatchingPlayerInCollections(Object playerList, UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        for (Method method : playerList.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            try {
                Object value = method.invoke(playerList);
                if (value instanceof Collection<?> collection) {
                    for (Object player : collection) {
                        if (readUuid(player).filter(uuid::equals).isPresent()) {
                            return Optional.of(player);
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

    private static boolean classPresent(String name, ClassLoader loader) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
