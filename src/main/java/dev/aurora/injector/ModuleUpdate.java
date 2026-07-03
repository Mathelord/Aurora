package dev.aurora.injector;

import dev.aurora.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** Wire representation of a module mutation sent from the desktop panel to the agent. */
public record ModuleUpdate(String id, Boolean enabled, Integer keybind, Map<String, Double> settings) {
    public ModuleUpdate {
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    public String toJson() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        if (enabled != null) {
            payload.put("enabled", enabled);
        }
        if (keybind != null) {
            payload.put("keybind", keybind);
        }
        payload.put("settings", settings);
        return Json.object(payload);
    }

    public static ModuleUpdate fromJson(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("Module update must be a JSON object");
        }
        String id = payload.get("id") instanceof String value ? value : null;
        Boolean enabled = payload.get("enabled") instanceof Boolean value ? value : null;
        Integer keybind = payload.get("keybind") instanceof Number value ? value.intValue() : null;
        Map<String, Double> settings = new LinkedHashMap<>();
        if (payload.get("settings") instanceof Map<?, ?> values) {
            values.forEach((key, value) -> {
                if (value instanceof Number number) {
                    settings.put(String.valueOf(key), number.doubleValue());
                }
            });
        }
        return new ModuleUpdate(id, enabled, keybind, settings);
    }
}
