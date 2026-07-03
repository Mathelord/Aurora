package dev.aurora.minecraft;

import dev.aurora.aim.Vec3;

/** Where the player is actually looking from right now, used to place world-space geometry relative
 * to Minecraft's active camera. */
public record CameraPose(boolean available, Vec3 eye, float yaw, float pitch, double fovDegrees, int screenWidth, int screenHeight) {
    private static final CameraPose UNAVAILABLE = new CameraPose(false, Vec3.ZERO, 0.0F, 0.0F, 70.0D, 0, 0);

    public static CameraPose unavailable() {
        return UNAVAILABLE;
    }
}
