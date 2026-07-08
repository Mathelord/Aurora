package dev.aurora.modules;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.FreeCameraState;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;

/**
 * Detaches the camera entirely from the player: the body is frozen in place while the view flies
 * around under WASD / Space / Shift control, steered by the free-look direction. Enabling forces
 * first-person perspective (the camera is repositioned directly, so no third-person zoom-back
 * interferes) and {@code RuntimeController} flags the render camera as detached each frame so the
 * player model still draws and the hand is hidden. The camera position is stepped once per tick and
 * read back interpolated with the render partial-tick, so movement is smooth at any frame rate.
 *
 * <p>With "Hold to activate" on, the bound key works as a momentary hold rather than a toggle.
 */
public final class FreecamModule extends AbstractModule {
    // Standard GLFW key codes for the default movement binds. Read directly (rather than via the
    // game's KeyBinding objects) because the player's real movement keys are suppressed while the
    // body is frozen, so their pressed-state would otherwise always read false.
    private static final int KEY_W = 87;
    private static final int KEY_A = 65;
    private static final int KEY_S = 83;
    private static final int KEY_D = 68;
    private static final int KEY_SPACE = 32;
    private static final int KEY_LEFT_SHIFT = 340;
    private static final int KEY_LEFT_CONTROL = 341;

    /** Base blocks-per-tick at speed 1.0 (matches Meteor's non-sprinting freecam step). */
    private static final double BASE_STEP = 0.5D;
    private static final int FIRST_PERSON = 0;

    private final MinecraftBridge minecraft;
    private final FreeCameraState state = new FreeCameraState();
    private final ModuleSetting speed;
    private final ModuleSetting holdToActivate;
    private int previousPerspective = -1;

    public FreecamModule(MinecraftBridge minecraft) {
        super("freecam", "Freecam", "Render",
                "Detach the camera and fly it around while your body stays put.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.speed = setting("speed", "Fly Speed", 2.0D, 0.1D, 10.0D, 0.1D);
        this.holdToActivate = booleanSetting("hold", "Hold to activate", false);
    }

    public FreeCameraState state() {
        return state;
    }

    @Override
    public boolean holdToActivate() {
        return holdToActivate.value() >= 0.5D;
    }

    @Override
    public synchronized void onEnable() {
        CameraPose pose = minecraft.cameraPose();
        AimAngles body = pose.available()
                ? new AimAngles(pose.yaw(), pose.pitch())
                : new AimAngles(0.0F, 0.0F);
        double eyeX = pose.available() ? pose.eye().x() : 0.0D;
        double eyeY = pose.available() ? pose.eye().y() : 0.0D;
        double eyeZ = pose.available() ? pose.eye().z() : 0.0D;
        state.activate(body, body, eyeX, eyeY, eyeZ, true);
        // First person keeps the camera at the eye with no zoom-back; RuntimeController then moves it
        // to the freecam position and flips the detached flag so the body still renders.
        previousPerspective = minecraft.cameraPerspective();
        if (previousPerspective != FIRST_PERSON) {
            minecraft.setCameraPerspective(FIRST_PERSON);
        }
    }

    @Override
    public synchronized void onDisable() {
        state.deactivate();
        if (previousPerspective >= 0) {
            minecraft.setCameraPerspective(previousPerspective);
            previousPerspective = -1;
        }
    }

    @Override
    public void onTick(TickEvent event) {
        if (!enabled()) {
            return;
        }
        // Snapshot the previous-tick position for render interpolation, then integrate this tick's
        // movement. When a screen is open the snapshot still advances so releasing the screen does
        // not produce a one-tick interpolation jump.
        state.beginTick();
        if (minecraft.hasOpenScreen()) {
            return;
        }
        double forward = axis(minecraft.isKeyDown(KEY_W), minecraft.isKeyDown(KEY_S));
        double strafe = axis(minecraft.isKeyDown(KEY_D), minecraft.isKeyDown(KEY_A));
        double vertical = axis(minecraft.isKeyDown(KEY_SPACE), minecraft.isKeyDown(KEY_LEFT_SHIFT));
        if (forward == 0.0D && strafe == 0.0D && vertical == 0.0D) {
            return;
        }
        double step = BASE_STEP * speed.value() * (minecraft.isKeyDown(KEY_LEFT_CONTROL) ? 2.0D : 1.0D);
        double yawRad = Math.toRadians(state.viewYaw());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        // Horizontal movement stays on the ground plane (ignores pitch) like a spectator camera:
        // forward follows the look yaw, strafe is perpendicular to it.
        double dx = (forward * -sin) - (strafe * cos);
        double dz = (forward * cos) - (strafe * sin);
        if (forward != 0.0D && strafe != 0.0D) {
            // Normalise diagonal so moving on both axes is not faster than a single axis.
            double diagonal = 1.0D / Math.sqrt(2.0D);
            dx *= diagonal;
            dz *= diagonal;
        }
        state.moveBy(dx * step, vertical * step, dz * step);
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1.0D : 0.0D) - (negative ? 1.0D : 0.0D);
    }
}
