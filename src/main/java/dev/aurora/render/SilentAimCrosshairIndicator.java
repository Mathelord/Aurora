package dev.aurora.render;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.AimMath;
import dev.aurora.aim.Vec3;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;

/** Global HUD pointer showing where the active silent-aim owner is looking. */
public final class SilentAimCrosshairIndicator {
    private static final double DEADZONE = 0.15D;
    private static final double MIN_LENGTH = 8.0D;
    private static final double MAX_LENGTH = 22.0D;
    private static final double SCREEN_SCALE = 34.0D;

    private SilentAimCrosshairIndicator() {
    }

    public static void render(MinecraftBridge minecraft, Object context,
                              AimAngles visualAngles, AimAngles silentAngles) {
        if (minecraft == null || context == null || visualAngles == null || silentAngles == null) return;
        CameraPose camera = minecraft.cameraPose();
        if (camera == null || !camera.available() || camera.screenWidth() <= 0 || camera.screenHeight() <= 0) return;
        Direction direction = direction(visualAngles, silentAngles);
        if (direction == null || direction.magnitude() < DEADZONE) return;
        double length = AimMath.clamp(direction.magnitude() * SCREEN_SCALE, MIN_LENGTH, MAX_LENGTH);
        double centerX = camera.screenWidth() * 0.5D;
        double centerY = camera.screenHeight() * 0.5D;
        minecraft.drawHudLine(context, centerX, centerY,
                centerX + direction.x() * length, centerY + direction.y() * length, 0xFFFFFFFF);
    }

    static Direction direction(AimAngles visual, AimAngles silentAngles) {
        Vec3 forward = visual.direction().normalize();
        Vec3 right = new AimAngles(visual.yaw() + 90.0F, 0.0F).direction().normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 silent = silentAngles.direction().normalize();
        double cameraX = silent.dot(right);
        double cameraY = -silent.dot(up);
        double cameraZ = silent.dot(forward);
        double screenX = cameraZ > 0.01D ? cameraX / cameraZ : cameraX;
        double screenY = cameraZ > 0.01D ? cameraY / cameraZ : cameraY;
        double magnitude = Math.hypot(screenX, screenY);
        if (magnitude >= 0.001D) return new Direction(screenX / magnitude, screenY / magnitude, magnitude);

        double yawDelta = AimMath.wrapDegrees(silentAngles.yaw() - visual.yaw());
        double pitchDelta = silentAngles.pitch() - visual.pitch();
        magnitude = Math.hypot(yawDelta, pitchDelta);
        return magnitude < 0.001D ? null
                : new Direction(yawDelta / magnitude, pitchDelta / magnitude, magnitude / 45.0D);
    }

    record Direction(double x, double y, double magnitude) {
    }
}
