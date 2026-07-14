package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ClientModule;
import dev.aurora.api.ModuleManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Disables every module temporarily and restores the previous module state when released. */
public final class PanicModule extends AbstractModule {
    private final ModuleManager modules;
    private Map<String, Boolean> previousStates;

    public PanicModule(ModuleManager modules) {
        super("panic", "Panic", "Misc",
                "Temporarily disables every module and restores the previous state when pressed again.");
        this.modules = Objects.requireNonNull(modules, "modules");
    }

    @Override
    public boolean requiresKeybind() {
        return true;
    }

    @Override
    public synchronized void onEnable() {
        Map<String, Boolean> snapshot = new LinkedHashMap<>();
        for (ClientModule module : modules.modules()) {
            if (!module.id().equals(id())) {
                snapshot.put(module.id(), module.enabled());
            }
        }
        previousStates = snapshot;
        for (ClientModule module : modules.modules()) {
            if (!module.id().equals(id())) {
                module.setEnabled(false);
            }
        }
    }

    @Override
    public synchronized void onDisable() {
        if (previousStates == null) {
            return;
        }
        for (ClientModule module : modules.modules()) {
            Boolean wasEnabled = previousStates.get(module.id());
            if (wasEnabled != null) {
                module.setEnabled(wasEnabled);
            }
        }
        previousStates = null;
    }
}
