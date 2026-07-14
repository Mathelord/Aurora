package dev.aurora.agent;

import dev.aurora.api.ClientModule;
import dev.aurora.aim.AimAngles;
import dev.aurora.aim.DecoupledAimState;
import dev.aurora.aim.FreeCameraState;
import dev.aurora.aim.SilentAimSystem;
import dev.aurora.api.ModuleManager;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.injector.IpcMessage;
import dev.aurora.injector.ModuleUpdate;
import dev.aurora.input.RealClickSimulator;
import dev.aurora.input.ActivationClickSuppressor;
import dev.aurora.input.GameplayInputGate;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.ReflectionMinecraftBridge;
import dev.aurora.modules.AimAssistModule;
import dev.aurora.modules.AutoToolModule;
import dev.aurora.modules.AutoAnchorModule;
import dev.aurora.modules.BackTrackModule;
import dev.aurora.modules.BlinkModule;
import dev.aurora.modules.EspModule;
import dev.aurora.modules.FastPlaceModule;
import dev.aurora.modules.FreeLookModule;
import dev.aurora.modules.FreecamModule;
import dev.aurora.modules.FullbrightModule;
import dev.aurora.modules.HitSwapModule;
import dev.aurora.modules.JumpResetModule;
import dev.aurora.modules.InstantStopModule;
import dev.aurora.modules.KnockbackDelayModule;
import dev.aurora.modules.NoJumpDelayModule;
import dev.aurora.modules.AntiDebuffModule;
import dev.aurora.modules.PanicModule;
import dev.aurora.modules.NetworkDelayModule;
import dev.aurora.modules.NametagsModule;
import dev.aurora.modules.ReachModule;
import dev.aurora.modules.TrajectoriesModule;
import dev.aurora.modules.SilentAuraModule;
import dev.aurora.modules.TargetRingModule;
import dev.aurora.modules.TextGuiModule;
import dev.aurora.modules.TracersModule;
import dev.aurora.modules.TrailModule;
import dev.aurora.modules.TriggerBotModule;
import dev.aurora.network.KnockbackPackets;
import dev.aurora.network.PacketRelay;
import dev.aurora.social.FriendManager;
import dev.aurora.render.WorldGeometryBatch;
import dev.aurora.render.FrostedToastRenderer;
import dev.aurora.render.SilentAimCrosshairIndicator;
import dev.aurora.util.Json;

import java.lang.instrument.Instrumentation;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RuntimeController {
    private static volatile RuntimeController instance;
    private static final long HOOK_WATCHDOG_INITIAL_DELAY_SECONDS = 5L;
    private static final long HOOK_WATCHDOG_INTERVAL_SECONDS = 5L;

    private final ModuleManager modules;
    private final MinecraftBridge minecraft;
    private final SilentAuraModule silentAura;
    private final TriggerBotModule triggerBot;
    private final FreeLookModule freeLook;
    private final FreecamModule freecam;
    private final IpcAgentClient ipc;
    private final HookInstaller hooks;
    private final PacketRelay packetRelay = PacketRelay.get();
    private final AtomicBoolean uninstalled = new AtomicBoolean();
    private final AtomicBoolean moduleStateChangedOffTick = new AtomicBoolean();
    /** Work received from the control panel that must interact with Minecraft on its client thread. */
    private final ConcurrentLinkedQueue<Runnable> pendingClientThreadTasks = new ConcurrentLinkedQueue<>();
    private final Map<String, Boolean> moduleKeyStates = new HashMap<>();
    private final ScheduledExecutorService hookWatchdog = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "aurora-hook-watchdog");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong ticks = new AtomicLong();
    private final AtomicLong renders = new AtomicLong();
    private final AtomicLong packets = new AtomicLong();
    private final AtomicLong inboundPackets = new AtomicLong();
    private final AtomicLong outboundPackets = new AtomicLong();
    private final AtomicLong worldRenders = new AtomicLong();
    private final AtomicLong worldRenderFlushFailures = new AtomicLong();
    private volatile boolean hookHealthyLogged;
    private volatile boolean deadTickHookLogged;
    private volatile boolean deadRenderHookLogged;
    private volatile boolean deadWorldRenderHookLogged;
    private final ClientConfig config = ClientConfig.standard();
    private final HitSwapModule hitSwap;
    private final AntiDebuffModule antiDebuff;
    private volatile String lastSavedFingerprint = "";
    private volatile boolean silentAimCrosshairIndicator = true;
    private final FrostedToastRenderer toastRenderer;
    private final Map<String, Boolean> notifiedModuleStates = new HashMap<>();
    private int notificationDurationMillis = 1_500;
    private boolean notificationsEnabled = true;

    private RuntimeController(IpcAgentClient ipc, String minecraftVersion) {
        this.ipc = ipc;
        this.minecraft = new ReflectionMinecraftBridge(message -> ipc.log("ERROR", message));
        this.toastRenderer = new FrostedToastRenderer(minecraft);
        this.hooks = new HookInstaller(HookProfile.forVersion(minecraftVersion),
                message -> ipc.log("ERROR", message));
        this.modules = new ModuleManager(message -> ipc.log("ERROR", message));
        this.packetRelay.setDiagnosticSink(message -> ipc.log("ERROR", "Packet relay: " + message));
        WorldGeometryBatch.setDiagnosticSink(message -> ipc.log("ERROR", message));
        RealClickSimulator.init(minecraft);
        ActivationClickSuppressor.init(minecraft);
        KnockbackPackets knockbackPackets = KnockbackPackets.minecraft1214();
        modules.register(new ReachModule(minecraft));
        modules.register(new BackTrackModule(minecraft, packetRelay));
        modules.register(new AutoAnchorModule(minecraft));
        modules.register(new AimAssistModule(minecraft, message -> ipc.log("INFO", message)));
        silentAura = new SilentAuraModule(minecraft);
        modules.register(silentAura);
        modules.register(new TargetRingModule(minecraft, silentAura::currentAimTargetId));
        triggerBot = new TriggerBotModule(minecraft);
        modules.register(triggerBot);
        modules.register(new KnockbackDelayModule(minecraft, knockbackPackets, packetRelay));
        modules.register(new JumpResetModule(minecraft, knockbackPackets));
        modules.register(new InstantStopModule(minecraft));
        modules.register(new NoJumpDelayModule(minecraft));
        hitSwap = new HitSwapModule(minecraft);
        modules.register(hitSwap);
        modules.register(new TrailModule(minecraft));
        // Trajectories is registered before ESP so its hit-entity id is updated earlier in the
        // world-render pass than ESP reads it, letting ESP defer that entity's box the same frame.
        TrajectoriesModule trajectories = new TrajectoriesModule(minecraft);
        modules.register(trajectories);
        modules.register(new EspModule(minecraft, trajectories::currentHitEntityId));
        modules.register(new TracersModule(minecraft));
        modules.register(new NametagsModule(minecraft));
        modules.register(new FullbrightModule(minecraft));
        antiDebuff = new AntiDebuffModule();
        modules.register(antiDebuff);
        freeLook = new FreeLookModule(minecraft);
        modules.register(freeLook);
        freecam = new FreecamModule(minecraft);
        modules.register(freecam);
        modules.register(new AutoToolModule(minecraft));
        modules.register(new FastPlaceModule(minecraft));
        modules.register(new NetworkDelayModule(packetRelay));
        modules.register(new BlinkModule(minecraft, packetRelay, () -> moduleStateChangedOffTick.set(true)));
        modules.register(new TextGuiModule(minecraft, modules::modules));
        modules.register(new PanicModule(modules));
        config.applyTo(modules, message -> ipc.log("WARN", message));
        for (ClientModule module : modules.modules()) {
            notifiedModuleStates.put(module.id(), module.enabled());
        }
        lastSavedFingerprint = moduleStateFingerprint();
    }

    public static RuntimeController install(Instrumentation instrumentation, IpcAgentClient ipc) {
        return install(instrumentation, ipc, "");
    }

    public static RuntimeController install(Instrumentation instrumentation, IpcAgentClient ipc,
                                            String minecraftVersion) {
        RuntimeController controller = new RuntimeController(ipc, minecraftVersion);
        instance = controller;
        HookDispatch.bind(controller);
        controller.publishStatus("Installing hooks");
        controller.hooks.install(instrumentation);
        controller.publishModules();
        controller.publishStatus("Agent active");
        controller.startHookWatchdog();
        return controller;
    }

    public static RuntimeController instance() {
        return instance;
    }

    void publishInitialState() {
        publishModules();
        publishStatus("Agent active");
    }

    public void handleIpc(IpcMessage message) {
        if (message.type() == IpcMessage.Type.DETACH) {
            uninstall();
            return;
        }
        if (message.type() == IpcMessage.Type.MODULE_UPDATE) {
            ModuleUpdate update = ModuleUpdate.fromJson(message.payload());
            if (update.id() != null) {
                // IPC callbacks run on the connection thread. Module lifecycle methods can touch
                // Minecraft options (Fullbright, for example), so apply them during the next
                // client tick instead of mutating game state from that background thread.
                pendingClientThreadTasks.add(() -> applyModuleUpdate(update));
            }
        }
        if (message.type() == IpcMessage.Type.GLOBAL_SETTINGS) {
            silentAimCrosshairIndicator = Json.booleanField(
                    message.payload(), "silentAimCrosshairIndicator", silentAimCrosshairIndicator);
            notificationsEnabled = Json.booleanField(
                    message.payload(), "notificationsEnabled", notificationsEnabled);
            if (!notificationsEnabled) toastRenderer.dismissImmediately();
            int radius = numberSetting(message.payload(), "notificationRadius", 18, 8, 28);
            int blur = numberSetting(message.payload(), "notificationBlur", 4, 4, 40);
            int opacity = numberSetting(message.payload(), "notificationOpacity", 35, 0, 100);
            notificationDurationMillis = numberSetting(message.payload(), "notificationDurationMillis",
                    notificationDurationMillis, 500, 5_000);
            toastRenderer.configure(radius, blur, opacity);
        }
        if (message.type() == IpcMessage.Type.FRIENDS) {
            applyFriends(message.payload());
        }
    }

    private void applyFriends(String payload) {
        List<String> names = new ArrayList<>();
        if (Json.parse(payload) instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof String name) {
                    names.add(name);
                }
            }
        }
        FriendManager.get().setAll(names);
    }

    private void uninstall() {
        if (!uninstalled.compareAndSet(false, true)) {
            return;
        }
        ipc.log("INFO", "Uninjecting Aurora agent");
        for (ClientModule module : modules.modules()) {
            module.setEnabled(false);
        }
        packetRelay.reset();
        ActivationClickSuppressor.clear();
        GameplayInputGate.clear();
        hookWatchdog.shutdownNow();
        hooks.uninstall();
        HookDispatch.unbind();
        instance = null;
        ipc.send(IpcMessage.Type.STATUS, Json.object(Map.of(
                "attached", false,
                "message", "Aurora uninjected",
                "updatedAt", Instant.now().toString()
        )));
        AuroraAgent.onUninstalled(this);
        try {
            ipc.close();
        } catch (Exception exception) {
            System.err.println("[Aurora] Could not close IPC after uninject: " + exception.getMessage());
        }
    }

    public void onTick() {
        long count = ticks.incrementAndGet();
        boolean stateChangedOffTick = moduleStateChangedOffTick.getAndSet(false);
        packetRelay.onTick();
        if (GameplayInputGate.isActive()) {
            minecraft.suppressClickGuiGameplayInput();
        }
        ActivationClickSuppressor.armIfAttackKeyHeld(
                silentAura.suppressPhysicalHeldAttack()
                        || triggerBot.suppressPhysicalHeldAttack());
        String before = moduleStateFingerprint();
        drainClientThreadTasks();
        processModuleKeybinds();
        modules.onTick(TickEvent.now());
        if (stateChangedOffTick || !before.equals(moduleStateFingerprint())) {
            publishModules();
            saveConfigIfChanged();
        }
        if (count % 20 == 0) {
            publishSample();
        }
    }

    private void applyModuleUpdate(ModuleUpdate update) {
        boolean updated = modules.update(update.id(), update.enabled(), update.keybind(), update.settings());
        publishModules();
        publishStatus((updated ? "Updated module " : "Rejected update for module ") + update.id());
        if (updated) {
            saveConfigIfChanged();
        }
    }

    private void drainClientThreadTasks() {
        Runnable task;
        while ((task = pendingClientThreadTasks.poll()) != null) {
            task.run();
        }
    }

    public boolean onAttackAttempt() {
        if (GameplayInputGate.isActive()) {
            return true;
        }
        // A detached camera must never turn the player's server-side crosshair into an invisible
        // interaction cursor. The server still evaluates attacks from the stationary body, which
        // otherwise allows Freecam to hit entities that are behind or outside the rendered view.
        if (freecam.enabled() && freecam.state().isActive()) {
            return true;
        }
        if (ActivationClickSuppressor.shouldSuppressAttack()) {
            return true;
        }
        hitSwap.onAttackAttempt();
        return false;
    }

    public boolean antiDebuffEnabled() {
        return antiDebuff.enabled();
    }

    public boolean onBlockAttackAttempt() {
        return GameplayInputGate.isActive()
                || (freecam.enabled() && freecam.state().isActive())
                || ActivationClickSuppressor.shouldSuppressAttack();
    }

    private void saveConfigIfChanged() {
        String fingerprint = moduleStateFingerprint();
        if (fingerprint.equals(lastSavedFingerprint)) {
            return;
        }
        lastSavedFingerprint = fingerprint;
        config.save(modules, message -> ipc.log("WARN", message));
    }

    private void processModuleKeybinds() {
        boolean inputBlocked = minecraft.hasOpenScreen() || GameplayInputGate.isActive();
        for (ClientModule module : modules.modules()) {
            if (isPanicActive() && !"panic".equals(module.id())) {
                moduleKeyStates.put(module.id(), false);
                continue;
            }
            int keybind = module.keybind();
            boolean down = keybind >= 0 && minecraft.isKeyDown(keybind);
            boolean wasDown = moduleKeyStates.getOrDefault(module.id(), false);
            moduleKeyStates.put(module.id(), down);
            if (inputBlocked || keybind < 0) {
                continue;
            }
            if (module.holdToActivate()) {
                // Momentary: track the key state exactly so the module is on only while held.
                if (down != module.enabled()) {
                    module.setEnabled(down);
                }
            } else if (down && !wasDown) {
                module.setEnabled(!module.enabled());
            }
        }
    }

    private boolean isPanicActive() {
        return modules.find("panic").map(ClientModule::enabled).orElse(false);
    }

    public void onRender(Object context) {
        renders.incrementAndGet();
        minecraft.beginHudFrame(context);
        showModuleStateNotification();
        modules.onRender(RenderEvent.now(context));
        SilentAimSystem silentAim = SilentAimSystem.get();
        DecoupledAimState decoupled = DecoupledAimState.get();
        if (silentAimCrosshairIndicator && silentAim.hasActiveSilentAim() && decoupled.isActive()) {
            SilentAimCrosshairIndicator.render(minecraft, context, decoupled.visualAngles(),
                    new AimAngles(silentAim.lastSilentYaw(), silentAim.lastSilentPitch()));
        }
        toastRenderer.render(context, Instant.now());
    }

    private void showModuleStateNotification() {
        for (ClientModule module : modules.modules()) {
            boolean enabled = module.enabled();
            Boolean previous = notifiedModuleStates.put(module.id(), enabled);
            if (notificationsEnabled && previous != null && previous != enabled) {
                toastRenderer.show(module.displayName(), enabled ? "Enabled" : "Disabled",
                        Duration.ofMillis(notificationDurationMillis));
            }
        }
    }

    private static int numberSetting(String json, String name, int fallback, int minimum, int maximum) {
        Double value = Json.numberField(json, name);
        int number = value == null ? fallback : (int) Math.round(value);
        return Math.max(minimum, Math.min(maximum, number));
    }

    public void onWorldRender(Object matrixStack, Object vertexConsumers) {
        worldRenders.incrementAndGet();
        WorldGeometryBatch geometry = new WorldGeometryBatch(matrixStack, vertexConsumers, minecraft.cameraPose());
        modules.onWorldRender(WorldRenderEvent.now(geometry));
        if (!geometry.flush()) {
            worldRenderFlushFailures.incrementAndGet();
        }
    }

    /** Captures Minecraft's intact world model-view matrix before its entity pass mutates and pops
     * the supplied stack. HUD projection later in the frame uses this immutable snapshot. */
    public void captureWorldProjection(Object matrixStack) {
        minecraft.captureWorldProjection(matrixStack);
    }


    /** Returns whether {@code packet} should be suppressed (held back by {@link PacketRelay} for
     * later replay) instead of actually being sent/received this call. */
    public boolean onPacket(PacketEvent.Direction direction, Object endpoint, Object packet) {
        packets.incrementAndGet();
        if (direction == PacketEvent.Direction.INBOUND) {
            inboundPackets.incrementAndGet();
        } else {
            outboundPackets.incrementAndGet();
        }
        modules.onPacket(PacketEvent.now(direction, packet));
        return direction == PacketEvent.Direction.INBOUND
                ? packetRelay.captureInbound(endpoint, packet)
                : packetRelay.captureOutbound(endpoint, packet);
    }

    /** The Free Look / Freecam view state currently in control of the camera, or {@code null} when
     * neither is active. Freecam wins if both are somehow on. */
    private FreeCameraState activeDetachedCamera() {
        if (freecam.enabled() && freecam.state().isActive()) {
            return freecam.state();
        }
        if (freeLook.enabled() && freeLook.state().isActive()) {
            return freeLook.state();
        }
        return null;
    }

    public boolean onLook(Object entity, double cursorDeltaX, double cursorDeltaY) {
        if (!minecraft.isLocalPlayer(entity)) {
            return false;
        }
        FreeCameraState detached = activeDetachedCamera();
        if (detached != null) {
            detached.applyMouseDelta(cursorDeltaX, cursorDeltaY);
            return true;
        }
        DecoupledAimState state = DecoupledAimState.get();
        if (!state.isActive()) {
            return false;
        }
        state.applyMouseDelta(cursorDeltaX, cursorDeltaY);
        return true;
    }

    public boolean onCameraBegin(Object focusedEntity) {
        if (!minecraft.isLocalPlayer(focusedEntity)) {
            return false;
        }
        FreeCameraState detached = activeDetachedCamera();
        if (detached != null) {
            // Point the entity at the free view (with previous-tick rotation) so the game builds the
            // camera — including any third-person orbit offset — around the free look direction.
            AimAngles view = detached.viewAngles();
            return minecraft.applyCameraEntityRotation(focusedEntity, view.yaw(), view.pitch());
        }
        DecoupledAimState state = DecoupledAimState.get();
        if (!state.isActive()) {
            return false;
        }
        AimAngles visual = state.visualAngles();
        return minecraft.applyEntityRotation(focusedEntity, visual.yaw(), visual.pitch());
    }

    public void onCameraEnd(Object camera, Object focusedEntity, double tickDelta) {
        if (!minecraft.isLocalPlayer(focusedEntity)) {
            return;
        }
        FreeCameraState detached = activeDetachedCamera();
        if (detached != null) {
            // Restore the body to its frozen angle (current and previous tick) so movement, hitboxes
            // and the rendered model stay put. A non-decoupled silent aim owns the body rotation,
            // however, so retain its latest rotation instead of overwriting it with the angle Free
            // Look captured on activation. The detached camera view remains independent.
            SilentAimSystem silentAim = SilentAimSystem.get();
            if (silentAim.hasActiveSilentAim() && !DecoupledAimState.get().isActive()) {
                detached.updateBodyAngles(new AimAngles(silentAim.lastSilentYaw(), silentAim.lastSilentPitch()));
            }
            // Then point the render camera at the free view and (for Freecam) relocate it to the
            // interpolated free-flying position.
            AimAngles body = detached.bodyAngles();
            minecraft.applyCameraEntityRotation(focusedEntity, body.yaw(), body.pitch());
            AimAngles view = detached.viewAngles();
            minecraft.applyCameraRotation(camera, view.yaw(), view.pitch());
            detached.interpolatedPosition(tickDelta).ifPresent(pos -> {
                // Force the "third person" flag so the player model renders and the hand is hidden
                // even though Freecam runs in first-person perspective.
                minecraft.setCameraDetached(camera, true);
                minecraft.applyCameraPosition(camera, pos[0], pos[1], pos[2]);
            });
            return;
        }
        DecoupledAimState state = DecoupledAimState.get();
        if (!state.isActive()) {
            return;
        }
        AimAngles silent = state.silentAngles();
        minecraft.applyEntityRotation(focusedEntity, silent.yaw(), silent.pitch());
        AimAngles visual = state.visualAngles();
        minecraft.applyCameraRotation(camera, visual.yaw(), visual.pitch());
    }

    public void onMouseScroll(double amount) {
        // No in-game GUI listens for scroll anymore; module control lives in the external panel.
    }

    public void onMovementInput(Object input) {
        if (GameplayInputGate.isActive()) {
            minecraft.freezeMovementInput(input);
            return;
        }
        FreeCameraState detached = activeDetachedCamera();
        if (detached != null && detached.freezeMovement()) {
            // Freecam: keep the body stationary while the detached camera flies.
            minecraft.freezeMovementInput(input);
            return;
        }
        DecoupledAimState state = DecoupledAimState.get();
        if (!state.isActive()) {
            return;
        }
        AimAngles visual = state.visualAngles();
        AimAngles silent = state.silentAngles();
        minecraft.steerMovementInput(input, visual.yaw(), silent.yaw());
    }

    private void publishStatus(String message) {
        ipc.send(IpcMessage.Type.STATUS, Json.object(Map.of(
                "attached", true,
                "message", message,
                "environment", minecraft.environment(),
                "updatedAt", Instant.now().toString()
        )));
    }

    private void publishModules() {
        ipc.send(IpcMessage.Type.MODULE_LIST, Json.array(modules.modules().stream()
                .map(this::moduleJson)
                .map(Json.Raw::new)
                .toList()));
    }

    private String moduleJson(ClientModule module) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", module.id());
        payload.put("displayName", module.displayName());
        payload.put("category", module.category());
        payload.put("description", module.description());
        payload.put("enabled", module.enabled());
        payload.put("keybind", module.keybind());
        payload.put("requiresKeybind", module.requiresKeybind());
        payload.put("settings", new Json.Raw(Json.array(module.settings().stream()
                .map(this::settingJson)
                .map(Json.Raw::new)
                .toList())));
        return Json.object(payload);
    }

    private String settingJson(ModuleSetting setting) {
        return Json.object(Map.ofEntries(
                Map.entry("id", setting.id()),
                Map.entry("displayName", setting.displayName()),
                Map.entry("description", setting.description()),
                Map.entry("type", setting.kind().name().toLowerCase()),
                Map.entry("options", setting.options()),
                Map.entry("value", setting.value()),
                Map.entry("default", setting.defaultValue()),
                Map.entry("min", setting.min()),
                Map.entry("max", setting.max()),
                Map.entry("step", setting.step()),
                Map.entry("visible", setting.visible())
        ));
    }

    private void publishSample() {
        PacketRelay.Metrics relay = packetRelay.metrics();
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("ticks", ticks.get());
        sample.put("hudRenders", renders.get());
        sample.put("worldRenders", worldRenders.get());
        sample.put("worldRenderFailures", worldRenderFlushFailures.get());
        sample.put("inboundPackets", inboundPackets.get());
        sample.put("outboundPackets", outboundPackets.get());
        sample.put("heldInbound", relay.heldInbound());
        sample.put("heldOutbound", relay.heldOutbound());
        sample.put("inboundLatencyMs", relay.latencyMs());
        sample.put("replayedInbound", relay.replayedInboundTotal());
        sample.put("replayedOutbound", relay.replayedOutboundTotal());
        sample.put("relayFailures", relay.replayFailures());
        String lastError = !relay.lastError().isBlank() ? relay.lastError() : WorldGeometryBatch.lastError();
        sample.put("lastError", lastError);
        sample.put("onlinePlayers", minecraft.onlinePlayerNames());
        ipc.send(IpcMessage.Type.EVENT_SAMPLE, Json.object(sample));
    }

    private void startHookWatchdog() {
        hookWatchdog.scheduleAtFixedRate(
                this::publishHookHealth,
                HOOK_WATCHDOG_INITIAL_DELAY_SECONDS,
                HOOK_WATCHDOG_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void publishHookHealth() {
        long tickCount = ticks.get();
        long renderCount = renders.get();
        long worldRenderCount = worldRenders.get();
        boolean worldRenderingExpected = hooks.supportsWorldRendering();
        if (tickCount > 0 && renderCount > 0 && (!worldRenderingExpected || worldRenderCount > 0)) {
            if (!hookHealthyLogged) {
                hookHealthyLogged = true;
                ipc.log("INFO", "Aurora hooks active. ticks=" + tickCount + ", HUD renders="
                        + renderCount + ", world renders=" + worldRenderCount
                        + (worldRenderingExpected ? "." : " (unsupported for this render pipeline)."));
            }
            return;
        }
        if (tickCount == 0 && !deadTickHookLogged) {
            deadTickHookLogged = true;
            ipc.log("ERROR", "Aurora tick hook has not fired. Modules will not run; this Minecraft version/mapping is probably not covered by the hook profile.");
        }
        if (renderCount == 0 && !deadRenderHookLogged) {
            deadRenderHookLogged = true;
            ipc.log("WARN", "Aurora render hook has not fired. In-game overlay text may not render; module logic can still run if ticks are active.");
        }
        if (worldRenderingExpected && worldRenderCount == 0 && !deadWorldRenderHookLogged) {
            deadWorldRenderHookLogged = true;
            ipc.log("WARN", "Aurora 3D world-render hook has not fired. Target Ring, Trail and ghost boxes cannot render.");
        }
    }

    private String moduleStateFingerprint() {
        StringBuilder builder = new StringBuilder();
        for (ClientModule module : modules.modules()) {
            builder.append(module.id()).append('=').append(module.enabled()).append(':').append(module.keybind());
            for (ModuleSetting setting : module.settings()) {
                builder.append(',').append(setting.id()).append('=').append(setting.value());
            }
            builder.append(';');
        }
        return builder.toString();
    }
}
