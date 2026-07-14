package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ClientModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.minecraft.HudSize;
import dev.aurora.minecraft.MinecraftBridge;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Modern HUD array list showing enabled modules. */
public final class TextGuiModule extends AbstractModule {
    private static final int MARGIN = 6;
    private static final int HORIZONTAL_PADDING = 5;
    private static final int VERTICAL_PADDING = 2;
    private static final int LINE_HEIGHT = 9;
    private static final int ROW_GAP = 2;
    private static final int ACCENT_WIDTH = 2;
    private static final int BACKGROUND_RGB = 0x101018;
    private static final int TEXT_RGB = 0xF2F2F7;
    private static final int DEFAULT_ACCENT = 0x1866DF;
    private static final double ANIMATION_MILLIS = 180.0D;
    private static final double MAX_FRAME_MILLIS = 50.0D;
    private static final long RAINBOW_CYCLE_MILLIS = 8_000L;

    private final MinecraftBridge minecraft;
    private final Supplier<? extends Collection<ClientModule>> modules;
    private final Map<String, AnimatedRow> rows = new LinkedHashMap<>();
    private final ModuleSetting position;
    private final ModuleSetting sorting;
    private final ModuleSetting accentColor;
    private final ModuleSetting rainbow;
    private final ModuleSetting background;
    private final ModuleSetting surface;
    private final ModuleSetting blurRadius;
    private final ModuleSetting cornerRadius;
    private final ModuleSetting showSelf;
    private Instant lastFrame;

    public TextGuiModule(MinecraftBridge minecraft,
                         Supplier<? extends Collection<ClientModule>> modules) {
        super("text-gui", "Text GUI", "Render", "Shows enabled modules in a modern animated array list.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.modules = Objects.requireNonNull(modules, "modules");
        position = optionSetting("position", "Position", Position.TOP_RIGHT.ordinal(),
                List.of("Top right", "Top left"))
                .description("Chooses which top corner anchors the module list.");
        sorting = optionSetting("sorting", "Sorting", Sorting.WIDTH.ordinal(),
                List.of("Width", "Alphabetical"))
                .description("Orders visible modules by text width or name.");
        accentColor = colorSetting("accent-color", "Accent color", DEFAULT_ACCENT)
                .description("Color of the narrow edge beside every module.");
        rainbow = booleanSetting("rainbow", "Rainbow mode", false)
                .description("Animates a slow color gradient along the accent edges.");
        background = booleanSetting("background", "Background", true)
                .description("Draws a translucent near-black surface behind each module.");
        surface = optionSetting("surface", "Surface", Surface.FROSTED.ordinal(),
                List.of("Frosted glass", "Solid"))
                .description("Uses real framebuffer blur when the graphics pipeline supports it.")
                .visibleWhen(() -> enabled(background));
        blurRadius = setting("blur-radius", "Blur radius", 12.0D, 4.0D, 40.0D, 1.0D)
                .description("Strength of the frosted-glass background blur.")
                .visibleWhen(() -> enabled(background) && option(surface, Surface.values()) == Surface.FROSTED);
        cornerRadius = setting("corner-radius", "Corner radius", 5.0D, 0.0D, 10.0D, 1.0D)
                .description("Rounding of each module surface.")
                .visibleWhen(() -> enabled(background));
        showSelf = booleanSetting("show-self", "Show Text GUI", false)
                .description("Includes Text GUI itself in the module list.");
    }

    @Override
    public void onDisable() {
        rows.clear();
        lastFrame = null;
    }

    @Override
    public void onRender(RenderEvent event) {
        if (event.context() == null) return;

        Instant now = event.createdAt() == null ? Instant.now() : event.createdAt();
        double elapsedMillis = elapsedMillis(now);
        syncRows();
        advanceAnimations(elapsedMillis);

        List<AnimatedRow> visible = rows.values().stream()
                .filter(row -> row.progress > 0.0D)
                .sorted(rowComparator())
                .toList();
        if (visible.isEmpty()) return;

        HudSize size = minecraft.hudSize(event.context());
        Position selectedPosition = option(position, Position.values());
        if (selectedPosition == Position.TOP_RIGHT && !size.available()) {
            selectedPosition = Position.TOP_LEFT;
        }

        int y = MARGIN;
        for (int rowIndex = 0; rowIndex < visible.size(); rowIndex++) {
            AnimatedRow row = visible.get(rowIndex);
            double eased = easeOutCubic(row.progress);
            int width = textWidth(row.name) + HORIZONTAL_PADDING * 2 + ACCENT_WIDTH;
            int height = LINE_HEIGHT + VERTICAL_PADDING * 2;
            int slide = (int) Math.round((1.0D - eased) * (width + MARGIN));
            int left;
            if (selectedPosition == Position.TOP_RIGHT) {
                int right = size.width() - MARGIN + slide;
                left = right - width;
            } else {
                left = MARGIN - slide;
            }
            int right = left + width;
            int alpha = clampAlpha((int) Math.round(255.0D * eased));

            if (enabled(background)) {
                int radius = (int) Math.round(cornerRadius.value());
                boolean frosted = option(surface, Surface.values()) == Surface.FROSTED
                        && minecraft.drawFrostedPanel(event.context(), left, y, right, y + height,
                        radius, (float) blurRadius.value(),
                        argb(scaleAlpha(0x72, alpha), BACKGROUND_RGB),
                        argb(scaleAlpha(0x38, alpha), 0xFFFFFF));
                if (!frosted) {
                    minecraft.fillRounded(event.context(), left, y, right, y + height, radius,
                            argb(scaleAlpha(0xB8, alpha), BACKGROUND_RGB));
                }
            }
            int accent = accentColor(now, rowIndex);
            if (selectedPosition == Position.TOP_RIGHT) {
                minecraft.fillRounded(event.context(), right - ACCENT_WIDTH, y, right, y + height,
                        1, argb(alpha, accent));
            } else {
                minecraft.fillRounded(event.context(), left, y, left + ACCENT_WIDTH, y + height,
                        1, argb(alpha, accent));
            }
            int textX = selectedPosition == Position.TOP_RIGHT
                    ? right - ACCENT_WIDTH - HORIZONTAL_PADDING - textWidth(row.name)
                    : left + ACCENT_WIDTH + HORIZONTAL_PADDING;
            minecraft.drawText(event.context(), row.name, textX, y + VERTICAL_PADDING,
                    argb(alpha, TEXT_RGB));
            y += height + ROW_GAP;
        }
    }

    private void syncRows() {
        Set<String> activeIds = new HashSet<>();
        boolean includeSelf = enabled(showSelf);
        for (ClientModule module : modules.get()) {
            if (!module.enabled() || (!includeSelf && id().equals(module.id()))) continue;
            activeIds.add(module.id());
            rows.compute(module.id(), (id, row) -> {
                if (row == null) return new AnimatedRow(id, module.displayName());
                row.name = module.displayName();
                row.targetVisible = true;
                return row;
            });
        }
        rows.values().forEach(row -> row.targetVisible = activeIds.contains(row.id));
    }

    private void advanceAnimations(double elapsedMillis) {
        double step = elapsedMillis / ANIMATION_MILLIS;
        rows.values().forEach(row -> row.progress = clamp01(
                row.progress + (row.targetVisible ? step : -step)));
        rows.values().removeIf(row -> !row.targetVisible && row.progress <= 0.0D);
    }

    private double elapsedMillis(Instant now) {
        if (lastFrame == null) {
            lastFrame = now;
            return Math.min(1000.0D / 60.0D, MAX_FRAME_MILLIS);
        }
        long nanos = Math.max(0L, Duration.between(lastFrame, now).toNanos());
        lastFrame = now;
        return Math.min(nanos / 1_000_000.0D, MAX_FRAME_MILLIS);
    }

    private Comparator<AnimatedRow> rowComparator() {
        Comparator<AnimatedRow> alphabetical = Comparator.comparing(
                row -> row.name, String.CASE_INSENSITIVE_ORDER);
        if (option(sorting, Sorting.values()) == Sorting.ALPHABETICAL) return alphabetical;
        return Comparator.comparingInt((AnimatedRow row) -> textWidth(row.name)).reversed()
                .thenComparing(alphabetical);
    }

    private int textWidth(String text) {
        int measured = minecraft.textWidth(text);
        return measured > 0 ? measured : text.length() * 6;
    }

    private int accentColor(Instant now, int rowIndex) {
        if (!enabled(rainbow)) {
            return ((int) Math.round(accentColor.value())) & 0xFFFFFF;
        }
        long cyclePosition = Math.floorMod(now.toEpochMilli(), RAINBOW_CYCLE_MILLIS);
        float baseHue = cyclePosition / (float) RAINBOW_CYCLE_MILLIS;
        float hue = (baseHue + rowIndex * 0.055F) % 1.0F;
        return Color.HSBtoRGB(hue, 0.72F, 0.96F) & 0xFFFFFF;
    }

    private static int argb(int alpha, int rgb) {
        return (clampAlpha(alpha) << 24) | (rgb & 0xFFFFFF);
    }

    private static int scaleAlpha(int baseAlpha, int animationAlpha) {
        return baseAlpha * animationAlpha / 255;
    }

    private static int clampAlpha(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double easeOutCubic(double value) {
        double inverse = 1.0D - clamp01(value);
        return 1.0D - inverse * inverse * inverse;
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }

    private static <T> T option(ModuleSetting setting, T[] values) {
        int index = Math.max(0, Math.min(values.length - 1, (int) Math.round(setting.value())));
        return values[index];
    }

    private enum Position { TOP_RIGHT, TOP_LEFT }
    private enum Sorting { WIDTH, ALPHABETICAL }
    private enum Surface { FROSTED, SOLID }

    private static final class AnimatedRow {
        private final String id;
        private String name;
        private boolean targetVisible = true;
        private double progress;

        private AnimatedRow(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
