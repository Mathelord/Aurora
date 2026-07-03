package dev.aurora.aim;

public final class DecoupledAimState {
    private static final DecoupledAimState INSTANCE = new DecoupledAimState();

    private boolean active;
    private float visualYaw;
    private float visualPitch;
    private float silentYaw;
    private float silentPitch;

    private DecoupledAimState() {
    }

    public static DecoupledAimState get() {
        return INSTANCE;
    }

    public synchronized void activate(AimAngles currentAngles) {
        if (active) {
            return;
        }
        AimAngles current = currentAngles == null ? new AimAngles(0.0F, 0.0F) : currentAngles.clampedPitch();
        visualYaw = current.yaw();
        visualPitch = current.pitch();
        silentYaw = current.yaw();
        silentPitch = current.pitch();
        active = true;
    }

    public synchronized void applyMouseDelta(double cursorDeltaX, double cursorDeltaY) {
        if (!active) {
            return;
        }
        visualYaw += (float) (cursorDeltaX * 0.15D);
        visualPitch = (float) AimMath.clamp(visualPitch + cursorDeltaY * 0.15D, -90.0D, 90.0D);
    }

    public synchronized void updateSilentAngles(AimAngles angles) {
        if (!active || angles == null) {
            return;
        }
        silentYaw = angles.yaw();
        silentPitch = angles.pitch();
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized AimAngles visualAngles() {
        return new AimAngles(visualYaw, visualPitch).clampedPitch();
    }

    public synchronized AimAngles silentAngles() {
        return new AimAngles(silentYaw, silentPitch).clampedPitch();
    }

    public synchronized AimAngles deactivate() {
        AimAngles visual = visualAngles();
        active = false;
        return visual;
    }

    public synchronized void reset() {
        active = false;
        visualYaw = 0.0F;
        visualPitch = 0.0F;
        silentYaw = 0.0F;
        silentPitch = 0.0F;
    }
}
