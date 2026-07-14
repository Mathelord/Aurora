package dev.aurora.minecraft;

import dev.aurora.aim.Vec3;

/** Render-ready entity information used by Nametags without leaking mapped Minecraft classes. */
public record NametagTarget(
        String id,
        String name,
        Kind kind,
        Vec3 position,
        double height,
        double distance,
        double health,
        double maxHealth,
        int ping,
        String gameMode,
        int itemCount,
        boolean friend,
        boolean localPlayer
) {
    public enum Kind {
        PLAYER("Players"),
        ITEM("Items"),
        HOSTILE("Hostile Mobs"),
        PASSIVE("Passive Mobs"),
        OTHER("Other");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
