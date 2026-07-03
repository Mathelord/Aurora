package dev.aurora.modules;

import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.TargetPose;
import dev.aurora.render.TargetRingColorPreset;
import dev.aurora.render.TargetRingRenderer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Draws a configurable ring around Silent Aura's current target. The ring only appears while Silent
 * Aura is enabled and locked onto an entity; enabling/disabling this module is the master toggle for
 * the effect. All appearance options are exposed as settings so the control panel can surface them
 * under a dedicated Target Ring tab.
 */
public final class TargetRingModule extends AbstractModule {
    private final MinecraftBridge minecraft;
    private final Supplier<String> targetIdSupplier;
    private final TargetRingRenderer renderer = new TargetRingRenderer();

    private final ModuleSetting wall;
    private final ModuleSetting floor;
    private final ModuleSetting rainbow;
    private final ModuleSetting colorPreset;
    private final ModuleSetting radiusPadding;
    private final ModuleSetting heightScale;
    private final ModuleSetting opacity;
    private final ModuleSetting rainbowSpeed;

    public TargetRingModule(MinecraftBridge minecraft, Supplier<String> targetIdSupplier) {
        super("target-ring", "Target Ring", "Render", "Draws a ring around Silent Aura's current target.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.targetIdSupplier = Objects.requireNonNull(targetIdSupplier, "targetIdSupplier");
        wall = booleanSetting("wall", "Wall", true)
                .description("Draws the vertical wall of the ring that rises from the target's feet.");
        floor = booleanSetting("floor", "Floor", true)
                .description("Fills the disc on the ground inside the ring.");
        rainbow = booleanSetting("rainbow", "Rainbow", true)
                .description("Cycles an animated rainbow around the ring instead of a fixed color preset.");
        colorPreset = optionSetting("color-preset", "Color Preset", 0, TargetRingColorPreset.titles())
                .description("Two-stop color gradient used when Rainbow is disabled.")
                .visibleWhen(() -> !enabled(rainbow));
        radiusPadding = setting("radius-padding", "Radius Padding", 0.28D, 0.0D, 2.0D, 0.02D)
                .description("Extra radius, in blocks, added beyond the target's own width.");
        heightScale = setting("height-scale", "Height Scale", 0.6D, 0.1D, 2.0D, 0.05D)
                .description("Height of the ring wall relative to the target's height.");
        opacity = setting("opacity", "Opacity", 205.0D, 20.0D, 255.0D, 5.0D)
                .description("Base opacity of the ring at the feet; it fades toward the top of the wall.");
        rainbowSpeed = setting("rainbow-speed", "Rainbow Speed", 0.16D, 0.0D, 1.0D, 0.02D)
                .description("How many full rainbow cycles the ring completes per second.")
                .visibleWhen(() -> enabled(rainbow));
    }

    @Override
    public void onEnable() {
        renderer.reset();
    }

    @Override
    public void onDisable() {
        renderer.reset();
    }

    @Override
    public void onWorldRender(WorldRenderEvent event) {
        if (event.geometry() == null) {
            return;
        }
        String targetId = targetIdSupplier.get();
        if (targetId == null) {
            renderer.reset();
            return;
        }
        Optional<TargetPose> pose = minecraft.targetPose(targetId);
        if (pose.isEmpty()) {
            renderer.reset();
            return;
        }
        renderer.render(event.geometry(), targetId, pose.get(), config());
    }

    private TargetRingRenderer.Config config() {
        return new TargetRingRenderer.Config(
                enabled(wall),
                enabled(floor),
                enabled(rainbow),
                TargetRingColorPreset.byIndex((int) Math.round(colorPreset.value())),
                radiusPadding.value(),
                heightScale.value(),
                (int) Math.round(opacity.value()),
                rainbowSpeed.value());
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }
}
