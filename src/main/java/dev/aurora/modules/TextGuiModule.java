package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ClientModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Compact top-left HUD listing the modules that are currently enabled. */
public final class TextGuiModule extends AbstractModule {
    private static final int LEFT = 6;
    private static final int TOP = 6;
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int BACKGROUND = 0xB8101018;

    private final MinecraftBridge minecraft;
    private final Supplier<? extends Collection<ClientModule>> modules;
    private final ModuleSetting background;

    public TextGuiModule(MinecraftBridge minecraft,
                         Supplier<? extends Collection<ClientModule>> modules) {
        super("text-gui", "Text GUI", "Render", "Shows enabled modules in a compact top-left overlay.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.modules = Objects.requireNonNull(modules, "modules");
        background = booleanSetting("background", "Background", true)
                .description("Draws a translucent dark surface behind the text.");
    }

    @Override
    public void onRender(RenderEvent event) {
        if (event.context() == null) return;

        List<String> lines = new ArrayList<>();
        modules.get().stream()
                .filter(ClientModule::enabled)
                .map(ClientModule::displayName)
                .sorted(Comparator.comparingInt(this::textWidth).reversed().thenComparing(String.CASE_INSENSITIVE_ORDER))
                .forEach(lines::add);
        if (lines.isEmpty()) return;

        int contentWidth = lines.stream().mapToInt(this::textWidth).max().orElse(0);
        int width = contentWidth + PADDING * 2;
        int height = lines.size() * LINE_HEIGHT + PADDING * 2;

        if (enabled(background)) {
            minecraft.fill(event.context(), LEFT, TOP, LEFT + width, TOP + height, BACKGROUND);
        }

        int y = TOP + PADDING;
        for (String line : lines) {
            minecraft.drawText(event.context(), line, LEFT + PADDING, y, 0xFFF2F2F7);
            y += LINE_HEIGHT;
        }
    }

    private int textWidth(String text) {
        int measured = minecraft.textWidth(text);
        return measured > 0 ? measured : text.length() * 6;
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }
}
