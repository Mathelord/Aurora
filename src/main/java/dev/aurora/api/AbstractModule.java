package dev.aurora.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractModule implements ClientModule {
    private final String id;
    private final String displayName;
    private final String category;
    private final String description;
    private final CopyOnWriteArrayList<ModuleSetting> settings = new CopyOnWriteArrayList<>();
    private volatile boolean enabled;
    private volatile int keybind = -1;

    protected AbstractModule(String id, String displayName) {
        this(id, displayName, "Misc", "");
    }

    protected AbstractModule(String id, String displayName, String category, String description) {
        this.id = id;
        this.displayName = displayName;
        this.category = category == null || category.isBlank() ? "Misc" : category;
        this.description = description == null ? "" : description;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    @Override
    public int keybind() {
        return keybind;
    }

    @Override
    public void setKeybind(int keyCode) {
        keybind = keyCode < 0 ? -1 : keyCode;
    }

    @Override
    public List<ModuleSetting> settings() {
        return List.copyOf(settings);
    }

    protected ModuleSetting setting(String id, String displayName, double value, double min, double max, double step) {
        ModuleSetting setting = new ModuleSetting(id, displayName, value, min, max, step);
        settings.add(setting);
        return setting;
    }

    protected ModuleSetting booleanSetting(String id, String displayName, boolean value) {
        ModuleSetting setting = new ModuleSetting(
                id,
                displayName,
                ModuleSetting.Kind.BOOLEAN,
                java.util.List.of("Off", "On"),
                value ? 1.0D : 0.0D,
                0.0D,
                1.0D,
                1.0D
        );
        settings.add(setting);
        return setting;
    }

    protected ModuleSetting colorSetting(String id, String displayName, int rgb) {
        ModuleSetting setting = new ModuleSetting(
                id,
                displayName,
                ModuleSetting.Kind.COLOR,
                java.util.List.of(),
                rgb & 0xFFFFFF,
                0.0D,
                0xFFFFFF,
                1.0D
        );
        settings.add(setting);
        return setting;
    }

    protected ModuleSetting optionSetting(String id, String displayName, int value, java.util.List<String> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }
        ModuleSetting setting = new ModuleSetting(
                id,
                displayName,
                ModuleSetting.Kind.OPTION,
                options,
                value,
                0.0D,
                options.size() - 1.0D,
                1.0D
        );
        settings.add(setting);
        return setting;
    }

    /** A launcher for a module-specific entity type selector. */
    protected ModuleSetting entityListSetting(String id, String displayName, String description) {
        ModuleSetting setting = new ModuleSetting(id, displayName, ModuleSetting.Kind.ENTITY_LIST,
                java.util.List.of(), 0.0D, 0.0D, 0.0D, 1.0D).description(description);
        settings.add(setting);
        return setting;
    }

    /** A compact multi-select entity picker encoded as an option bit mask. */
    protected ModuleSetting entityListSetting(String id, String displayName, String description,
                                              java.util.List<String> options, int defaultMask) {
        if (options.isEmpty() || options.size() > 30) {
            throw new IllegalArgumentException("entity options must contain between 1 and 30 entries");
        }
        int maxMask = (1 << options.size()) - 1;
        ModuleSetting setting = new ModuleSetting(id, displayName, ModuleSetting.Kind.ENTITY_LIST,
                options, defaultMask & maxMask, 0, maxMask, 1).description(description);
        settings.add(setting);
        return setting;
    }

}
