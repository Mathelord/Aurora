package dev.aurora.injector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileStoreTest {
    @Test
    void savedProfileRoundTripsThroughDisk(@TempDir Path dir) {
        ProfileStore store = new ProfileStore(dir);
        Map<String, Double> reachSettings = new LinkedHashMap<>();
        reachSettings.put("range-min", 4.5D);
        reachSettings.put("range-max", 6.0D);
        Map<String, ProfileStore.ModuleState> modules = new LinkedHashMap<>();
        modules.put("reach", new ProfileStore.ModuleState(true, 42, reachSettings));
        modules.put("esp", new ProfileStore.ModuleState(false, -1, new LinkedHashMap<>()));

        store.save("Combat", new ProfileStore.Snapshot(modules, false));

        ProfileStore.Snapshot loaded = store.load("Combat");
        assertEquals(2, loaded.modules().size());
        ProfileStore.ModuleState reach = loaded.modules().get("reach");
        assertTrue(reach.enabled());
        assertEquals(42, reach.keybind());
        assertEquals(4.5D, reach.settings().get("range-min"), 0.0001D);
        assertEquals(6.0D, reach.settings().get("range-max"), 0.0001D);
        assertFalse(loaded.modules().get("esp").enabled());
        assertFalse(loaded.silentAimCrosshairIndicator());
    }

    @Test
    void listRenameAndDeleteReflectDiskState(@TempDir Path dir) {
        ProfileStore store = new ProfileStore(dir);
        store.save("Alpha", new ProfileStore.Snapshot(new LinkedHashMap<>(), true));
        store.save("Beta", new ProfileStore.Snapshot(new LinkedHashMap<>(), true));

        assertEquals(List.of("Alpha", "Beta"), store.list());
        assertTrue(store.exists("Alpha"));

        store.rename("Alpha", "Gamma");
        assertFalse(store.exists("Alpha"));
        assertEquals(List.of("Beta", "Gamma"), store.list());

        store.delete("Beta");
        assertEquals(List.of("Gamma"), store.list());
    }

    @Test
    void nameValidationRejectsUnsafeNames() {
        assertTrue(ProfileStore.isValidName("My Profile_1"));
        assertFalse(ProfileStore.isValidName(""));
        assertFalse(ProfileStore.isValidName("../escape"));
        assertFalse(ProfileStore.isValidName("bad/name"));
    }

    @Test
    void importCreatesNumberedCopyAndExportIsPortable(@TempDir Path dir) throws Exception {
        ProfileStore store = new ProfileStore(dir.resolve("profiles"));
        store.save("PvP", new ProfileStore.Snapshot(new LinkedHashMap<>(), true));
        Path exported = dir.resolve("export.properties");
        store.exportTo("PvP", exported);
        assertTrue(Files.isRegularFile(exported));

        String imported = store.importFrom(exported);
        assertEquals("export", imported);
        Files.copy(exported, dir.resolve("PvP.properties"));
        assertEquals("PvP 2", store.importFrom(dir.resolve("PvP.properties")));
    }
}
