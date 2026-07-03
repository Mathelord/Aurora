package dev.aurora.minecraft;

import java.util.List;

public record AimContext(
        boolean available,
        boolean screenOpen,
        double yaw,
        double pitch,
        double mouseSensitivity,
        List<AimTarget> targets
) {
    private static final AimContext UNAVAILABLE = new AimContext(false, false, 0.0D, 0.0D, 0.5D, List.of());

    public AimContext {
        targets = List.copyOf(targets);
    }

    public static AimContext unavailable() {
        return UNAVAILABLE;
    }
}
