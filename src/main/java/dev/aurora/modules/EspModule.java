package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.TargetPose;
import dev.aurora.render.WorldGeometryBatch;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class EspModule extends AbstractModule {
    private static final int FILL_ALPHA = 0x50;
    private static final int BORDER_ALPHA = 0xE0;

    private final MinecraftBridge minecraft;
    private final Supplier<String> trajectoryHitEntityId;
    private final ModuleSetting range;
    private final ModuleSetting color;

    public EspModule(MinecraftBridge minecraft) {
        this(minecraft, () -> null);
    }

    public EspModule(MinecraftBridge minecraft, Supplier<String> trajectoryHitEntityId) {
        super("esp", "ESP", "Render", "Shows bounding boxes around nearby players.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.trajectoryHitEntityId = Objects.requireNonNull(trajectoryHitEntityId, "trajectoryHitEntityId");
        this.range = setting("range", "Range", 64.0D, 1.0D, 256.0D, 1.0D)
                .description("Maximum distance at which to render player boxes.");
        this.color = colorSetting("color", "Color", 0xFF6464)
                .description("Color of the ESP box.");
    }

    @Override
    public void onWorldRender(WorldRenderEvent event) {
        AimContext context = minecraft.aimContext(range.value(), true);
        if (!context.available() || event.geometry() == null) {
            return;
        }

        int boxColor = colorArgb();
        // Defer to Trajectories for the entity its arc currently marks, so that entity gets a single
        // box (in the trajectory color) instead of two overlapping ones.
        String deferredId = trajectoryHitEntityId.get();
        for (AimTarget target : context.targets()) {
            if (target != null && !target.id().equals(deferredId)) {
                renderPlayerBox(event.geometry(), target, boxColor);
            }
        }
    }

    private void renderPlayerBox(WorldGeometryBatch geometry, AimTarget target, int boxColor) {
        Optional<TargetPose> pose = minecraft.targetPose(target.id());
        if (pose.isEmpty()) {
            return;
        }
        TargetPose targetPose = pose.get();
        double halfWidth = targetPose.width() / 2.0D;
        Vec3 min = new Vec3(targetPose.feetX() - halfWidth, targetPose.feetY(), targetPose.feetZ() - halfWidth);
        Vec3 max = new Vec3(targetPose.feetX() + halfWidth, targetPose.feetY() + targetPose.height(), targetPose.feetZ() + halfWidth);
        geometry.espBox(min, max, withAlpha(boxColor, FILL_ALPHA), withAlpha(boxColor, BORDER_ALPHA));
    }

    private int colorArgb() {
        return 0xFF000000 | ((int) Math.round(color.value()) & 0x00FFFFFF);
    }

    private static int withAlpha(int colorRgb, int alpha) {
        return (alpha << 24) | (colorRgb & 0x00FFFFFF);
    }
}
