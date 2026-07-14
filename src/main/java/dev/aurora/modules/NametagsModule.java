package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.RenderEvent;
import dev.aurora.minecraft.CameraPose;
import dev.aurora.minecraft.MinecraftBridge;
import dev.aurora.minecraft.NametagTarget;
import dev.aurora.minecraft.ScreenPosition;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Screen-space entity nametags ported from Meteor's layout and distance scaling. */
public final class NametagsModule extends AbstractModule {
    private static final List<String> ENTITY_OPTIONS = List.of(
            "Players", "Items", "Hostile Mobs", "Passive Mobs", "Other");

    private final MinecraftBridge minecraft;
    private final ModuleSetting entities;
    private final ModuleSetting scale;
    private final ModuleSetting ignoreSelf;
    private final ModuleSetting ignoreFriends;
    private final ModuleSetting health;
    private final ModuleSetting gameMode;
    private final ModuleSetting ping;
    private final ModuleSetting distance;
    private final ModuleSetting itemCount;
    private final ModuleSetting background;
    private final ModuleSetting backgroundOpacity;
    private final ModuleSetting nameColor;
    private final ModuleSetting friendColor;
    private final ModuleSetting pingColor;
    private final ModuleSetting gameModeColor;
    private final ModuleSetting distanceColor;
    private final ModuleSetting culling;
    private final ModuleSetting cullingRange;
    private final ModuleSetting cullingCount;

    public NametagsModule(MinecraftBridge minecraft) {
        super("nametags", "Nametags", "Render",
                "Displays customizable nametags above selected entity types.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        // Bit 0 = players, bit 1 = items. This mirrors Meteor's default selection.
        entities = entityListSetting("entities", "Entities", "Select entity groups to draw nametags on.",
                ENTITY_OPTIONS, 0b00011);
        scale = setting("scale", "Scale", 1.1, 0.1, 3, 0.1)
                .description("Base size of each nametag.");
        ignoreSelf = booleanSetting("ignore-self", "Ignore Self", true);
        ignoreFriends = booleanSetting("ignore-friends", "Ignore Friends", false);
        health = booleanSetting("health", "Health", true);
        gameMode = booleanSetting("gamemode", "Game Mode", false);
        ping = booleanSetting("ping", "Ping", true);
        distance = booleanSetting("distance", "Distance", false);
        itemCount = booleanSetting("item-count", "Item Count", true);
        culling = booleanSetting("culling", "Culling", false)
                .description("Only renders a limited number of nearby nametags.");
        cullingRange = setting("culling-range", "Culling Range", 20, 0, 200, 1)
                .description("Maximum nametag distance while culling is enabled.")
                .visibleWhen(() -> enabled(culling));
        cullingCount = setting("culling-count", "Culling Count", 50, 1, 100, 1)
                .description("Maximum nametag count while culling is enabled.")
                .visibleWhen(() -> enabled(culling));
        // Meteor's defaults: black at 75 alpha, white names, cyan ping and gold gamemode/count.
        background = colorSetting("background-color", "Background Color", 0x000000);
        backgroundOpacity = setting("background-opacity", "Background Opacity", 75.0 / 255.0, 0, 1, 0.05);
        nameColor = colorSetting("name-color", "Name Color", 0xFFFFFF);
        friendColor = colorSetting("friend-color", "Friend Color", 0x64C8FF);
        pingColor = colorSetting("ping-color", "Ping Color", 0x14AAAA);
        gameModeColor = colorSetting("gamemode-color", "Game Mode Color", 0xE8B923);
        distanceColor = colorSetting("distance-color", "Distance Color", 0x969696);
    }

    @Override
    public void onRender(RenderEvent event) {
        if (event.context() == null) return;
        CameraPose camera = minecraft.cameraPose();
        if (!camera.available() || camera.screenWidth() <= 0 || camera.screenHeight() <= 0) return;

        int mask = (int) Math.round(entities.value());
        double queryRange = enabled(culling) ? cullingRange.value() : 256.0D;
        long count = enabled(culling) ? Math.max(1, Math.round(cullingCount.value())) : Long.MAX_VALUE;
        minecraft.nametagTargets(queryRange).stream()
                .filter(target -> selected(mask, target.kind()))
                .filter(target -> !enabled(ignoreSelf) || !target.localPlayer())
                .filter(target -> !enabled(ignoreFriends) || !target.friend())
                .sorted(Comparator.comparingDouble(NametagTarget::distance))
                .limit(count)
                .sorted(Comparator.comparingDouble(NametagTarget::distance).reversed())
                .forEach(target -> render(event.context(), target));
    }

    private void render(Object context, NametagTarget target) {
        ScreenPosition point = minecraft.projectToScreen(context, target.position().add(0, target.height(), 0));
        if (!point.visible()) return;

        List<Segment> segments = new java.util.ArrayList<>();
        boolean player = target.kind() == NametagTarget.Kind.PLAYER;
        if (player && enabled(gameMode)) segments.add(new Segment("[" + target.gameMode() + "] ", opaque(gameModeColor)));
        segments.add(new Segment(target.name(), target.friend()
                ? 0xFF000000 | ((int) friendColor.value() & 0xFFFFFF)
                : 0xFF000000 | ((int) nameColor.value() & 0xFFFFFF)));
        if (enabled(health) && target.maxHealth() > 0) {
            double ratio = target.health() / target.maxHealth();
            int healthColor = ratio <= 0.333 ? 0xFFFF1919 : ratio <= 0.666 ? 0xFFFF6919 : 0xFF19FC19;
            segments.add(new Segment(" " + Math.round(target.health()), healthColor));
        }
        if (player && enabled(ping) && target.ping() >= 0) {
            segments.add(new Segment(" [" + target.ping() + "ms]", opaque(pingColor)));
        }
        if (enabled(distance) && !target.localPlayer()) {
            segments.add(new Segment(String.format(Locale.ROOT, " %.1fm", target.distance()), opaque(distanceColor)));
        }
        if (target.kind() == NametagTarget.Kind.ITEM && enabled(itemCount)) {
            segments.add(new Segment(" x" + target.itemCount(), 0xFFE8B923));
        }

        // Same distance scaling used by Meteor's NametagUtils#getScale.
        double visualScale = scale.value() * Math.max(0.5D, 1.0D - target.distance() * 0.01D);
        int unscaledWidth = segments.stream().mapToInt(segment -> minecraft.textWidth(segment.text())).sum();
        double scaledWidth = unscaledWidth * visualScale;
        double scaledHeight = 9 * visualScale;
        int textLeft = (int) Math.round(point.x() - scaledWidth / 2.0D);
        int textTop = (int) Math.round(point.y() - scaledHeight);
        int pad = Math.max(1, (int) Math.ceil(visualScale));
        minecraft.fill(context, textLeft - pad, textTop - pad,
                (int) Math.ceil(textLeft + scaledWidth) + pad,
                (int) Math.ceil(textTop + scaledHeight) + pad,
                ((int) Math.round(backgroundOpacity.value() * 255) << 24)
                        | ((int) background.value() & 0xFFFFFF));
        double x = textLeft;
        for (Segment segment : segments) {
            minecraft.drawScaledText(context, segment.text(), (int) Math.round(x), textTop, visualScale, segment.color());
            x += minecraft.textWidth(segment.text()) * visualScale;
        }
    }

    private static boolean selected(int mask, NametagTarget.Kind kind) {
        return (mask & (1 << kind.ordinal())) != 0;
    }

    private static boolean enabled(ModuleSetting setting) {
        return setting.value() >= 0.5;
    }

    private static int opaque(ModuleSetting setting) {
        return 0xFF000000 | ((int) setting.value() & 0xFFFFFF);
    }

    private record Segment(String text, int color) {}
}
