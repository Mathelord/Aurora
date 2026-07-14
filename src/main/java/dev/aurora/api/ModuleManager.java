package dev.aurora.api;

import dev.aurora.api.events.PacketEvent;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.api.events.WorldRenderEvent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class ModuleManager {
    private final Map<String, ClientModule> modules = new LinkedHashMap<>();
    private final Consumer<String> diagnosticSink;
    private String panicModuleId;

    public ModuleManager() {
        this(message -> System.err.println("[Aurora] " + message));
    }

    public ModuleManager(Consumer<String> diagnosticSink) {
        this.diagnosticSink = diagnosticSink == null ? ignored -> { } : diagnosticSink;
    }

    public synchronized void register(ClientModule module) {
        if (modules.containsKey(module.id())) {
            throw new IllegalArgumentException("duplicate module id: " + module.id());
        }
        modules.put(module.id(), module);
        if (module.id().equals("panic")) {
            panicModuleId = module.id();
        }
    }

    public synchronized Collection<ClientModule> modules() {
        return ListCopy.copyOf(modules.values());
    }

    public synchronized Optional<ClientModule> find(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public synchronized boolean update(String id, Boolean enabled, Map<String, Double> settings) {
        return update(id, enabled, null, settings);
    }

    public synchronized boolean update(String id, Boolean enabled, Integer keybind, Map<String, Double> settings) {
        ClientModule module = modules.get(id);
        if (module == null) {
            return false;
        }
        if (Boolean.TRUE.equals(enabled) && module.requiresKeybind() && module.keybind() < 0) {
            return false;
        }
        if (Boolean.TRUE.equals(enabled) && panicModuleId != null
                && !module.id().equals(panicModuleId)
                && modules.get(panicModuleId).enabled()) {
            return false;
        }
        for (ModuleSetting setting : module.settings()) {
            Double value = settings.get(setting.id());
            if (value != null) {
                setting.setValue(value);
            }
        }
        if (enabled != null) {
            module.setEnabled(enabled);
        }
        if (keybind != null) {
            module.setKeybind(keybind);
        }
        return true;
    }

    public void onTick(TickEvent event) {
        for (ClientModule module : modules()) {
            if (module.enabled()) {
                invoke(module, "tick", () -> module.onTick(event));
            }
        }
    }

    public void onRender(RenderEvent event) {
        for (ClientModule module : modules()) {
            if (module.enabled()) {
                invoke(module, "HUD render", () -> module.onRender(event));
            }
        }
    }

    public void onWorldRender(WorldRenderEvent event) {
        for (ClientModule module : modules()) {
            if (module.enabled()) {
                invoke(module, "3D render", () -> module.onWorldRender(event));
            }
        }
    }

    public void onPacket(PacketEvent event) {
        for (ClientModule module : modules()) {
            if (module.enabled()) {
                invoke(module, "packet", () -> module.onPacket(event));
            }
        }
    }

    private void invoke(ClientModule module, String phase, Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException exception) {
            diagnosticSink.accept("Module " + module.id() + " failed during " + phase + ": "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static final class ListCopy {
        private static <T> Collection<T> copyOf(Collection<T> values) {
            return java.util.List.copyOf(values);
        }
    }
}
