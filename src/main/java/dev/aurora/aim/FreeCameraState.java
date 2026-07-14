package dev.aurora.aim;

import java.util.Optional;

/**
 * Decoupled view state shared by Free Look and Freecam. While active, the camera's look angles are
 * driven purely by mouse movement ({@link #applyMouseDelta}) while the player's body stays frozen at
 * the {@code body} angles it held on activation, so the character keeps facing (and walking) in its
 * original direction. Freecam additionally detaches the camera <em>position</em> from the player.
 *
 * <p>The camera position is stepped once per game tick ({@link #beginTick} then {@link #moveBy}) and
 * read back interpolated with the render partial-tick ({@link #interpolatedPosition}) so the free
 * camera glides smoothly at any frame rate instead of stuttering one tick-step at a time.
 *
 * <p>Instances are per-module (unlike the silent-aim {@link DecoupledAimState} singleton) and every
 * accessor is synchronized because activation may arrive on the IPC thread while the render/tick
 * thread reads the state.
 */
public final class FreeCameraState {
    /** Mouse-to-degrees factor matching the game's own look scaling used elsewhere in the client. */
    private static final double MOUSE_SCALE = 0.15D;

    private boolean active;
    private float viewYaw;
    private float viewPitch;
    private float bodyYaw;
    private float bodyPitch;
    private boolean positionDetached;
    private double posX;
    private double posY;
    private double posZ;
    private double prevPosX;
    private double prevPosY;
    private double prevPosZ;

    /**
     * Begins decoupling. {@code eyeX/Y/Z} seed the freecam position and are ignored unless
     * {@code detachPosition} is set. Repeated calls while already active are ignored so an
     * already-moved camera is not snapped back to the player.
     */
    public synchronized void activate(AimAngles bodyAngles, AimAngles viewAngles,
                                      double eyeX, double eyeY, double eyeZ, boolean detachPosition) {
        if (active) {
            return;
        }
        AimAngles body = bodyAngles == null ? new AimAngles(0.0F, 0.0F) : bodyAngles.clampedPitch();
        AimAngles view = viewAngles == null ? body : viewAngles.clampedPitch();
        bodyYaw = body.yaw();
        bodyPitch = body.pitch();
        viewYaw = view.yaw();
        viewPitch = view.pitch();
        positionDetached = detachPosition;
        posX = prevPosX = eyeX;
        posY = prevPosY = eyeY;
        posZ = prevPosZ = eyeZ;
        active = true;
    }

    public synchronized void deactivate() {
        active = false;
        positionDetached = false;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void applyMouseDelta(double cursorDeltaX, double cursorDeltaY) {
        if (!active) {
            return;
        }
        viewYaw += (float) (cursorDeltaX * MOUSE_SCALE);
        viewPitch = (float) AimMath.clamp(viewPitch + cursorDeltaY * MOUSE_SCALE, -90.0D, 90.0D);
    }

    /** Yaw the free camera is currently pointing, wrapped to [-180, 180). */
    public synchronized float viewYaw() {
        return (float) AimMath.wrapDegrees(viewYaw);
    }

    /** Camera look angles the render camera should adopt. */
    public synchronized AimAngles viewAngles() {
        return new AimAngles(viewYaw, viewPitch).clampedPitch();
    }

    /** Frozen player body angles to restore onto the entity so movement/hitboxes stay put. */
    public synchronized AimAngles bodyAngles() {
        return new AimAngles(bodyYaw, bodyPitch).clampedPitch();
    }

    /**
     * Updates the body direction restored after a camera pass without changing the detached view.
     * This lets a non-decoupled aim controller continue to steer the player while Free Look owns
     * only the camera direction.
     */
    public synchronized void updateBodyAngles(AimAngles angles) {
        if (!active || angles == null) {
            return;
        }
        AimAngles body = angles.clampedPitch();
        bodyYaw = body.yaw();
        bodyPitch = body.pitch();
    }

    /** Whether the player's body should be prevented from moving (Freecam only). */
    public synchronized boolean freezeMovement() {
        return active && positionDetached;
    }

    /** Snapshots the current position as the previous-tick position. Call once at the start of every
     * game tick before {@link #moveBy}, so {@link #interpolatedPosition} can glide between the two. */
    public synchronized void beginTick() {
        if (!active || !positionDetached) {
            return;
        }
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
    }

    /** Advances the free-flying camera position by a world-space offset (per tick). */
    public synchronized void moveBy(double dx, double dy, double dz) {
        if (!active || !positionDetached) {
            return;
        }
        posX += dx;
        posY += dy;
        posZ += dz;
    }

    /**
     * The world-space eye the renderer should use, interpolated between the previous and current tick
     * positions by {@code partialTicks} in [0, 1]. Empty when the position is not detached.
     */
    public synchronized Optional<double[]> interpolatedPosition(double partialTicks) {
        if (!active || !positionDetached) {
            return Optional.empty();
        }
        double t = AimMath.clamp(partialTicks, 0.0D, 1.0D);
        return Optional.of(new double[]{
                prevPosX + (posX - prevPosX) * t,
                prevPosY + (posY - prevPosY) * t,
                prevPosZ + (posZ - prevPosZ) * t
        });
    }
}
