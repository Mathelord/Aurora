package dev.aurora.modules;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.FreeCameraState;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.Objects;

/**
 * Detaches the camera's look direction from the player's body: while enabled the mouse rotates the
 * view freely, but the character keeps facing (and moving toward) the direction it held when the
 * module turned on. To make that actually visible, enabling the module switches the game into
 * third-person so you orbit around your own character; disabling restores the previous perspective.
 * The camera and entity rotation is applied by {@code RuntimeController} through the game's camera
 * hooks; this module owns the decoupled {@link FreeCameraState}.
 *
 * <p>With "Hold to activate" on, the bound key works as a momentary hold (look around only while the
 * key is down) rather than a toggle.
 */
public final class FreeLookModule extends AbstractModule {
    /** Perspective ordinal for third-person-behind (see {@link MinecraftBridge#cameraPerspective()}). */
    private static final int THIRD_PERSON_BACK = 1;

    private final MinecraftBridge minecraft;
    private final FreeCameraState state = new FreeCameraState();
    private final ModuleSetting togglePerspective;
    private final ModuleSetting holdToActivate;
    private int previousPerspective = -1;

    public FreeLookModule(MinecraftBridge minecraft) {
        super("freelook", "Free Look", "Render",
                "Look around freely in third person while your body keeps facing forward.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.togglePerspective = booleanSetting("toggle-perspective", "Third Person", true);
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
        // Free Look only decouples rotation, so the freecam position is never used here.
        state.activate(body, body, 0.0D, 0.0D, 0.0D, false);
        if (togglePerspective.value() >= 0.5D) {
            previousPerspective = minecraft.cameraPerspective();
            if (previousPerspective != THIRD_PERSON_BACK) {
                minecraft.setCameraPerspective(THIRD_PERSON_BACK);
            }
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
}
