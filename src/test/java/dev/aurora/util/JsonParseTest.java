package dev.aurora.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonParseTest {
    @Test
    void parsesNestedArraysOfObjectsBackIntoJavaCollections() {
        String encoded = Json.array(List.of(new Json.Raw(Json.object(Map.of(
                "id", "reach",
                "enabled", true,
                "settings", new Json.Raw(Json.array(List.of(new Json.Raw(Json.object(Map.of(
                        "id", "range",
                        "value", 5.5
                ))))))
        )))));

        Object parsed = Json.parse(encoded);

        assertTrue(parsed instanceof List<?>);
        List<?> modules = (List<?>) parsed;
        assertEquals(1, modules.size());
        Map<?, ?> module = (Map<?, ?>) modules.get(0);
        assertEquals("reach", module.get("id"));
        assertEquals(true, module.get("enabled"));
        List<?> settings = (List<?>) module.get("settings");
        Map<?, ?> setting = (Map<?, ?>) settings.get(0);
        assertEquals("range", setting.get("id"));
        assertEquals(5.5, (Double) setting.get("value"), 0.0001D);
    }

    @Test
    void parsesEscapesAndNullLiterals() {
        Object parsed = Json.parse("{\"text\":\"a\\\"b\\n\",\"nothing\":null}");

        Map<?, ?> map = (Map<?, ?>) parsed;
        assertEquals("a\"b\n", map.get("text"));
        assertNull(map.get("nothing"));
    }
}
