package dev.aurora.agent;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleManager;
import dev.aurora.api.ModuleSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTest {
    @Test
    void savedSettingsAreReappliedOnTheNextLoad(@TempDir Path dir) {
        Path file = dir.resolve("config.properties");
        ClientConfig config = new ClientConfig(file);
        List<String> errors = new ArrayList<>();

        ModuleManager written = new ModuleManager();
        TestModule writtenModule = new TestModule();
        written.register(writtenModule);
        writtenModule.setEnabled(true);
        writtenModule.setKeybind(42);
        writtenModule.amount.setValue(7.0D);
        config.save(written, errors::add);

        ModuleManager loaded = new ModuleManager();
        TestModule loadedModule = new TestModule();
        loaded.register(loadedModule);
        config.applyTo(loaded, errors::add);

        assertTrue(errors.isEmpty());
        assertTrue(loadedModule.enabled());
        assertEquals(42, loadedModule.keybind());
        assertEquals(7.0D, loadedModule.amount.value(), 0.0001D);
    }

    @Test
    void savingWritesAWarningReadmeNextToTheConfigFile(@TempDir Path dir) {
        Path file = dir.resolve("config.properties");
        ClientConfig config = new ClientConfig(file);
        ModuleManager modules = new ModuleManager();
        modules.register(new TestModule());

        config.save(modules, error -> { throw new AssertionError(error); });

        assertTrue(Files.isRegularFile(dir.resolve("README.txt")));
    }

    private static final class TestModule extends AbstractModule {
        private final ModuleSetting amount;

        private TestModule() {
            super("test", "Test module");
            amount = setting("amount", "Amount", 0.0D, 0.0D, 10.0D, 1.0D);
        }
    }
}
