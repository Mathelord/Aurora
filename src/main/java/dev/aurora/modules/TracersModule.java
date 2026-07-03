package dev.aurora.modules;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.Vec3;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.AimContext;
import dev.aurora.minecraft.AimTarget;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.TargetPose;
import dev.aurora.render.WorldGeometryBatch;

import java.util.Objects;
import java.util.Optional;

/**
 * Draws each tracer as a thin, camera-facing 3D quad rather than a raw GL line: Minecraft's line
 * layer only exposes a fixed, screen-size-derived width, so a real "Thickness" slider needs actual
 * ribbon geometry. The outline (if enabled) is a second, wider ribbon drawn first so the main line
 * shows as a colored core with a border around it, the same fill-then-border layering ESP uses.
 */
public final class TracersModule extends AbstractModule {
    private static final double ORIGIN_FORWARD_OFFSET = 0.25D;
    private static final double OUTLINE_SCALE = 1.6D;

    private final MinecraftBridge minecraft;
    private final ModuleSetting range;
    private final ModuleSetting color;
    private final ModuleSetting thickness;
    private final ModuleSetting outline;
    private final ModuleSetting outlineColor;

    public TracersModule(MinecraftBridge minecraft) {
        super("tracers", "Tracers", "Render", "Draws lines from your camera to nearby players.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.range = setting("range", "Range", 64.0D, 1.0D, 256.0D, 1.0D)
                .description("Maximum distance at which to draw a tracer line.");
        this.color = colorSetting("color", "Color", 0xFFE664)
                .description("Color of the tracer lines.");
        this.thickness = setting("thickness", "Thickness", 0.02D, 0.002D, 0.3D, 0.002D)
                .description("World-space thickness, in blocks, of each tracer line.");
        this.outline = booleanSetting("outline", "Outline", true)
                .description("Draws a colored border around each tracer line.");
        this.outlineColor = colorSetting("outline-color", "Outline Color", 0x000000)
                .description("Color of the tracer outline.")
                .visibleWhen(() -> enabled(this.outline));
    }

    @Override
    public void onWorldRender(WorldRenderEvent event) {
        CameraPose camera = minecraft.cameraPose();
        if (!camera.available() || event.geometry() == null) {
            return;
        }
        AimContext context = minecraft.aimContext(range.value(), true);
        if (!context.available()) {
            return;
        }

        // A vertex sitting exactly at the camera eye is a degenerate near-plane case; nudging the
        // origin slightly forward keeps every tracer comfortably inside the frustum.
        Vec3 origin = camera.eye().add(new AimAngles((float) camera.yaw(), (float) camera.pitch())
                .direction().multiply(ORIGIN_FORWARD_OFFSET));

        int lineColor = colorArgb(color);
        boolean drawOutline = enabled(outline);
        int borderColor = colorArgb(outlineColor);
        double halfThickness = thickness.value() / 2.0D;
        double outlineHalfThickness = halfThickness * OUTLINE_SCALE;

        for (AimTarget target : context.targets()) {
            if (target != null) {
                renderTracer(event.geometry(), origin, camera.eye(), target,
                        lineColor, drawOutline, borderColor, halfThickness, outlineHalfThickness);
            }
        }
    }

    private void renderTracer(WorldGeometryBatch geometry, Vec3 origin, Vec3 cameraEye, AimTarget target,
                              int lineColor, boolean drawOutline, int borderColor,
                              double halfThickness, double outlineHalfThickness) {
        Optional<TargetPose> pose = minecraft.targetPose(target.id());
        if (pose.isEmpty()) {
            return;
        }
        TargetPose targetPose = pose.get();
        Vec3 center = new Vec3(targetPose.feetX(), targetPose.feetY() + targetPose.height() / 2.0D, targetPose.feetZ());
        Vec3 direction = center.subtract(origin);
        if (direction.lengthSquared() <= 1.0e-6D) {
            return;
        }
        Vec3 perpendicular = facingPerpendicular(direction, cameraEye.subtract(origin));

        if (drawOutline) {
            Vec3 outlineOffset = perpendicular.multiply(outlineHalfThickness);
            geometry.quad(origin.subtract(outlineOffset), origin.add(outlineOffset),
                    center.add(outlineOffset), center.subtract(outlineOffset), borderColor);
        }
        Vec3 offset = perpendicular.multiply(halfThickness);
        geometry.quad(origin.subtract(offset), origin.add(offset),
                center.add(offset), center.subtract(offset), lineColor);
    }

    /** A unit vector perpendicular to {@code direction}, chosen so the ribbon it builds faces the
     * camera as edge-on as possible. Falls back to world axes when {@code direction} happens to be
     * parallel to the camera-facing vector (e.g. a tracer running straight down the view line). */
    private static Vec3 facingPerpendicular(Vec3 direction, Vec3 towardCamera) {
        Vec3 perpendicular = direction.cross(towardCamera).normalize();
        if (perpendicular.equals(Vec3.ZERO)) {
            perpendicular = direction.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        }
        if (perpendicular.equals(Vec3.ZERO)) {
            perpendicular = direction.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        }
        return perpendicular;
    }

    private static int colorArgb(ModuleSetting setting) {
        return 0xFF000000 | ((int) Math.round(setting.value()) & 0x00FFFFFF);
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5D;
    }
}
