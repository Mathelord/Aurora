package dev.aurora.modules;

import dev.aurora.aim.AimAngles;
import dev.aurora.aim.Vec3;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.EntityBox;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.render.WorldGeometryBatch;

import java.util.List;
import java.util.Objects;

/**
 * Draws the predicted flight path of the projectile the player is holding (bow, crossbow, trident,
 * or a thrown item such as a snowball, egg, ender pearl, potion or experience bottle) by stepping
 * the same tick-based physics the game uses — apply velocity, apply drag, then subtract gravity —
 * until the arc hits a solid block or an entity, or leaves the world. A marker box is always drawn
 * at the landing point, and if the arc would strike an entity a box is drawn around it (in the
 * trajectory color, regardless of the ESP module's settings).
 */
public final class TrajectoriesModule extends AbstractModule {
    private static final int MAX_STEPS = 160;
    private static final double LANDING_BOX_HALF = 0.15D;
    /** Segment sampling stride, in blocks. Must be < 1 so a one-block-thick floor is never skipped. */
    private static final double SAMPLE_STEP = 0.5D;
    /** Upper bound on collision samples per frame, so aiming into open sky stays cheap. */
    private static final int SAMPLE_BUDGET = 400;
    /** Nudges the arc's start forward so the first segment does not clip against the near plane. */
    private static final double START_FORWARD_OFFSET = 0.2D;
    /** Radius searched for entities the arc might hit; comfortably covers a projectile's range. */
    private static final double ENTITY_SEARCH_RANGE = 96.0D;
    /** How far outside an entity's box still counts as a hit (a projectile's own radius). */
    private static final double ENTITY_HIT_PADDING = 0.3D;
    private static final int ESP_FILL_ALPHA = 0x50;
    private static final int ESP_BORDER_ALPHA = 0xE0;

    private final MinecraftBridge minecraft;
    private final ModuleSetting color;
    /** Id of the entity the arc currently strikes, for the ESP module to defer to; null if none. */
    private volatile String hitEntityId;

    public TrajectoriesModule(MinecraftBridge minecraft) {
        super("trajectories", "Trajectories", "Render",
                "Predicts and draws the flight path of the projectile you are holding.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.color = colorSetting("color", "Color", 0x33FF66)
                .description("Color of the trajectory line, landing marker, and hit-entity box.");
    }

    /** The id of the entity the predicted arc currently strikes, or {@code null} if the arc hits no
     * entity (or the module is inactive). The ESP module defers to this so a single box is drawn. */
    public String currentHitEntityId() {
        return enabled() ? hitEntityId : null;
    }

    @Override
    public void onDisable() {
        hitEntityId = null;
    }

    @Override
    public void onWorldRender(WorldRenderEvent event) {
        hitEntityId = null;
        WorldGeometryBatch geometry = event.geometry();
        if (geometry == null) {
            return;
        }
        CameraPose camera = minecraft.cameraPose();
        if (!camera.available()) {
            return;
        }
        Launch launch = launchFor(minecraft.heldItemId().orElse(null));
        if (launch == null) {
            return;
        }

        Vec3 look = new AimAngles(camera.yaw(), camera.pitch()).direction();
        // Projectiles spawn a little below eye level and inherit the player's motion this tick. The
        // forward nudge keeps the first segment clear of the camera near plane.
        Vec3 position = camera.eye().add(0.0D, -0.1D, 0.0D).add(look.multiply(START_FORWARD_OFFSET));
        Vec3 velocity = look.multiply(launch.speed()).add(minecraft.playerVelocity());
        List<EntityBox> entities = minecraft.nearbyEntityBoxes(camera.eye(), ENTITY_SEARCH_RANGE);

        int lineColor = colorArgb(color);
        int samplesUsed = 0;
        for (int step = 0; step < MAX_STEPS && samplesUsed < SAMPLE_BUDGET; step++) {
            Vec3 next = position.add(velocity);
            // Motion is a straight line within a tick, so sample the segment finely enough that a
            // thin floor between position and next cannot be tunnelled through.
            Hit hit = firstHitAlong(position, next, entities);
            samplesUsed += sampleCount(position, next);
            Vec3 segmentEnd = hit != null ? hit.point() : next;
            geometry.line(position, segmentEnd, lineColor);
            if (hit != null) {
                drawLandingBox(geometry, hit.point(), lineColor);
                if (hit.entity() != null) {
                    hitEntityId = hit.entity().id();
                    drawEntityBox(geometry, hit.entity(), lineColor);
                }
                return;
            }
            position = next;
            velocity = velocity.multiply(launch.drag()).add(0.0D, -launch.gravity(), 0.0D);
            if (position.y() < -128.0D || position.y() > 512.0D) {
                return;
            }
        }
    }

    /** Walks from {@code from} to {@code to} in {@link #SAMPLE_STEP} increments, returning the first
     * sampled point that strikes an entity or lies inside a solid block, or {@code null} if the
     * segment is clear. Entities take precedence over blocks at the same sample. */
    private Hit firstHitAlong(Vec3 from, Vec3 to, List<EntityBox> entities) {
        Vec3 delta = to.subtract(from);
        int samples = sampleCount(from, to);
        for (int i = 1; i <= samples; i++) {
            Vec3 point = from.add(delta.multiply((double) i / samples));
            for (EntityBox entity : entities) {
                if (entity.contains(point, ENTITY_HIT_PADDING)) {
                    return new Hit(point, entity);
                }
            }
            if (minecraft.isBlockSolidAt(
                    (int) Math.floor(point.x()), (int) Math.floor(point.y()), (int) Math.floor(point.z()))) {
                return new Hit(point, null);
            }
        }
        return null;
    }

    private void drawLandingBox(WorldGeometryBatch geometry, Vec3 at, int lineColor) {
        geometry.box(
                at.add(-LANDING_BOX_HALF, -LANDING_BOX_HALF, -LANDING_BOX_HALF),
                at.add(LANDING_BOX_HALF, LANDING_BOX_HALF, LANDING_BOX_HALF),
                (lineColor & 0x00FFFFFF) | 0x40000000, lineColor);
    }

    /** Draws an ESP-style box (visible through walls) around the entity the arc would strike, in the
     * trajectory color — independent of the ESP module's own state or settings. */
    private void drawEntityBox(WorldGeometryBatch geometry, EntityBox entity, int lineColor) {
        geometry.espBox(entity.min(), entity.max(),
                withAlpha(lineColor, ESP_FILL_ALPHA), withAlpha(lineColor, ESP_BORDER_ALPHA));
    }

    private static int sampleCount(Vec3 from, Vec3 to) {
        return Math.max(1, (int) Math.ceil(to.subtract(from).length() / SAMPLE_STEP));
    }

    /** Launch parameters for the held item, or {@code null} if it is not a supported projectile. The
     * translation key looks like {@code "item.minecraft.bow"}, so matching on its suffix is stable
     * across obfuscation. Constants mirror vanilla's projectile speed, per-tick drag and gravity. */
    private static Launch launchFor(String translationKey) {
        if (translationKey == null) {
            return null;
        }
        if (translationKey.endsWith("bow") && !translationKey.endsWith("crossbow")) {
            return new Launch(3.0D, 0.05D, 0.99D);
        }
        if (translationKey.endsWith("crossbow")) {
            return new Launch(3.15D, 0.05D, 0.99D);
        }
        if (translationKey.endsWith("trident")) {
            return new Launch(2.5D, 0.05D, 0.99D);
        }
        if (translationKey.endsWith("splash_potion") || translationKey.endsWith("lingering_potion")) {
            return new Launch(0.5D, 0.05D, 0.99D);
        }
        if (translationKey.endsWith("snowball") || translationKey.endsWith("egg")
                || translationKey.endsWith("ender_pearl") || translationKey.endsWith("experience_bottle")) {
            return new Launch(1.5D, 0.03D, 0.99D);
        }
        return null;
    }

    private static int colorArgb(ModuleSetting setting) {
        return 0xFF000000 | ((int) Math.round(setting.value()) & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private record Launch(double speed, double gravity, double drag) {
    }

    private record Hit(Vec3 point, EntityBox entity) {
    }
}
