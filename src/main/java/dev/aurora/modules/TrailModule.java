package dev.aurora.modules;

import dev.aurora.aim.Vec3;
import dev.aurora.api.AbstractModule;
import dev.aurora.api.ModuleSetting;
import dev.aurora.api.events.WorldRenderEvent;
import dev.aurora.api.events.TickEvent;
import dev.aurora.minecraft.MinecraftBridge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Leaves a colored wall behind you as you move. Feet positions are sampled on tick (a new node is
 * only added once the player has moved far enough, so standing still doesn't consume the trail) and
 * kept for {@code duration} seconds. Each segment between consecutive nodes is rendered as a real,
 * perspective-correct world-space 3D quad. Older segments fade out toward the tail so the trail
 * dissolves instead of vanishing in one frame.
 */
public final class TrailModule extends AbstractModule {
    private static final double MIN_SEGMENT = 0.15D;
    private static final int MAX_NODES = 192;
    private static final int ALPHA = 0xB0;

    private final MinecraftBridge minecraft;
    private final ModuleSetting color;
    private final ModuleSetting height;
    private final ModuleSetting duration;
    private final ModuleSetting outline;

    private final Deque<Node> nodes = new ArrayDeque<>();

    public TrailModule(MinecraftBridge minecraft) {
        super("trail", "Trail", "Render", "Leaves a colored wall behind you as you move.");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        color = colorSetting("color", "Color", 0x78D2FF)
                .description("Color of the trail.");
        height = setting("height", "Height", 1.0D, 0.1D, 4.0D, 0.05D)
                .description("Vertical height, in blocks, of the rendered trail wall.");
        duration = setting("duration", "Duration s", 1.5D, 0.2D, 8.0D, 0.1D)
                .description("Number of seconds each trail segment remains visible before fading out.");
        outline = setting("outline", "Outline", 1.0D, 0.0D, 1.0D, 1.0D)
                .description("Enables or disables the dark outline along the trail.");
    }

    @Override
    public void onEnable() {
        nodes.clear();
    }

    @Override
    public void onDisable() {
        nodes.clear();
    }

    @Override
    public void onTick(TickEvent event) {
        Vec3 position = minecraft.playerPosition();
        long now = System.currentTimeMillis();
        Node last = nodes.peekLast();
        if (last == null || last.position.squaredDistanceTo(position) >= MIN_SEGMENT * MIN_SEGMENT) {
            nodes.addLast(new Node(position, now));
            if (nodes.size() > MAX_NODES) {
                nodes.removeFirst();
            }
        }
        expire(now);
    }

    @Override
    public void onWorldRender(WorldRenderEvent event) {
        if (nodes.size() < 2 || event.geometry() == null) return;
        int baseColor = colorArgb();
        boolean drawOutline = outline.value() >= 0.5D;
        double wallHeight = height.value();
        double durationMs = duration.value() * 1000.0D;
        long now = System.currentTimeMillis();
        Node[] snapshot = nodes.toArray(new Node[0]);
        for (int i = snapshot.length - 1; i >= 1; i--) {
            Node leading = snapshot[i - 1];
            Node trailing = snapshot[i];
            // Fade using the older (leading) node of the pair, so the tail dissolves smoothly.
            double fade = fadeFor(leading.timeMs, now, durationMs);
            drawSegment(event, leading.position, trailing.position, wallHeight, baseColor, fade, drawOutline);
        }
    }

    public int nodeCount() {
        return nodes.size();
    }

    private void drawSegment(WorldRenderEvent event, Vec3 from, Vec3 to, double wallHeight,
                             int baseColor, double fade, boolean drawOutline) {
        if (fade <= 0.0D) {
            return;
        }
        Vec3 bottomFrom = from;
        Vec3 topFrom = from.add(0.0D, wallHeight, 0.0D);
        Vec3 topTo = to.add(0.0D, wallHeight, 0.0D);
        Vec3 bottomTo = to;
        int fillColor = scaleAlpha(baseColor, fade);
        event.geometry().quad(bottomFrom, topFrom, topTo, bottomTo, fillColor);
        if (drawOutline) {
            int crest = scaleAlpha(0xFF000000 | (baseColor & 0xFFFFFF), fade);
            event.geometry().line(topFrom, topTo, crest);
        }
    }

    /** 1.0 for a freshly laid segment, ramping to 0.0 as it reaches {@code durationMs} of age. */
    private static double fadeFor(long segmentTimeMs, long now, double durationMs) {
        if (durationMs <= 0.0D) {
            return 1.0D;
        }
        double age = now - segmentTimeMs;
        return Math.max(0.0D, Math.min(1.0D, 1.0D - age / durationMs));
    }

    private static int scaleAlpha(int argb, double fade) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0D, Math.min(1.0D, fade)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private void expire(long now) {
        double durationMs = duration.value() * 1000.0D;
        while (true) {
            Node oldest = nodes.peekFirst();
            if (oldest == null || now - oldest.timeMs <= durationMs) {
                break;
            }
            nodes.removeFirst();
        }
    }

    private int colorArgb() {
        return (ALPHA << 24) | ((int) Math.round(color.value()) & 0x00FFFFFF);
    }

    private record Node(Vec3 position, long timeMs) {
    }
}
