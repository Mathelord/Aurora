package dev.aurora.render;

import dev.aurora.minecraft.HudSize;
import dev.aurora.minecraft.MinecraftBridge;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Animated top-center notification surface based on the Frosty Toast design handoff. */
public final class FrostedToastRenderer {
    private static final int MAX_WIDTH = 220;
    private static final int SCREEN_MARGIN = 8;
    private static final int TOP = 12;
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 8;
    private static final int TITLE_HEIGHT = 10;
    private static final int BODY_HEIGHT = 11;
    private static final long ENTRY_MILLIS = 450L;
    private static final long EXIT_MILLIS = 250L;

    private final MinecraftBridge minecraft;
    private Toast toast;
    private int radius = 18;
    private float blurRadius = 4.0F;
    private int opacity = 35;

    public FrostedToastRenderer(MinecraftBridge minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    public void show(String title, String message, Duration duration) {
        long visibleMillis = Math.max(500L, duration == null ? 1_500L : duration.toMillis());
        Instant now = Instant.now();
        if (toast == null) {
            toast = new Toast(clean(title), clean(message), now, visibleMillis);
            return;
        }
        toast.title = clean(title);
        toast.message = clean(message);
        // Keep the existing surface fully entered; only its content and lifetime are refreshed.
        toast.createdAt = now.minusMillis(ENTRY_MILLIS);
        toast.visibleMillis = visibleMillis + ENTRY_MILLIS;
        toast.dismissedAt = null;
    }

    public void configure(int radius, float blurRadius, int opacity) {
        this.radius = Math.max(8, Math.min(28, radius));
        this.blurRadius = Math.max(4.0F, Math.min(40.0F, blurRadius));
        this.opacity = Math.max(0, Math.min(100, opacity));
    }

    public void dismiss() {
        if (toast != null && toast.dismissedAt == null) toast.dismissedAt = Instant.now();
    }

    public void dismissImmediately() {
        toast = null;
    }

    public boolean render(Object context, Instant now) {
        Toast current = toast;
        if (current == null || context == null) return false;
        Instant frameTime = now == null ? Instant.now() : now;
        long age = Math.max(0L, Duration.between(current.createdAt, frameTime).toMillis());
        if (age >= current.visibleMillis && current.dismissedAt == null) current.dismissedAt = frameTime;
        double progress = Math.min(1.0D, age / (double) ENTRY_MILLIS);
        if (current.dismissedAt != null) {
            long exitAge = Math.max(0L, Duration.between(current.dismissedAt, frameTime).toMillis());
            if (exitAge >= EXIT_MILLIS) {
                toast = null;
                return false;
            }
            progress *= 1.0D - exitAge / (double) EXIT_MILLIS;
        }

        HudSize hud = minecraft.hudSize(context);
        if (!hud.available()) return false;
        int contentWidth = Math.max(textWidth(current.title), textWidth(current.message));
        int availableScreenWidth = Math.max(1, hud.width() - SCREEN_MARGIN * 2);
        int width = Math.min(MAX_WIDTH, Math.min(availableScreenWidth,
                Math.max(96, contentWidth + PADDING_X * 2)));
        List<String> bodyLines = wrap(current.message, Math.max(1, width - PADDING_X * 2));
        int height = PADDING_Y * 2 + TITLE_HEIGHT + (bodyLines.isEmpty() ? 0 : 3 + bodyLines.size() * BODY_HEIGHT);
        double eased = easeOutBack(progress);
        int left = (hud.width() - width) / 2;
        int top = TOP + (int) Math.round((1.0D - eased) * -16.0D);
        int alpha = clamp((int) Math.round(progress * 255.0D));

        boolean glass = minecraft.drawFrostedPanel(context, left, top, left + width, top + height,
                radius, blurRadius, argb(alpha * opacity / 100, 0x080A10),
                argb(alpha * 36 / 255, 0xFFFFFF));
        if (!glass) {
            minecraft.fillRounded(context, left, top, left + width, top + height, radius,
                    argb(alpha * opacity / 100, 0x181C26));
        }
        int textX = left + PADDING_X;
        int textY = top + PADDING_Y;
        minecraft.drawText(context, current.title, textX, textY, argb(alpha * 242 / 255, 0xFFFFFF));
        textY += TITLE_HEIGHT + 3;
        for (String line : bodyLines) {
            minecraft.drawText(context, line, textX, textY, argb(alpha * 174 / 255, 0xFFFFFF));
            textY += BODY_HEIGHT;
        }
        return true;
    }

    private List<String> wrap(String text, int availableWidth) {
        if (text.isBlank()) return List.of();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && textWidth(candidate) > availableWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private int textWidth(String value) {
        int width = minecraft.textWidth(value);
        return width > 0 ? width : value.length() * 6;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static double easeOutBack(double value) {
        double x = Math.max(0.0D, Math.min(1.0D, value)) - 1.0D;
        double overshoot = 1.70158D;
        return 1.0D + (overshoot + 1.0D) * x * x * x + overshoot * x * x;
    }

    private static int argb(int alpha, int rgb) {
        return (clamp(alpha) << 24) | (rgb & 0xFFFFFF);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class Toast {
        private String title;
        private String message;
        private Instant createdAt;
        private long visibleMillis;
        private Instant dismissedAt;

        private Toast(String title, String message, Instant createdAt, long visibleMillis) {
            this.title = title;
            this.message = message;
            this.createdAt = createdAt;
            this.visibleMillis = visibleMillis;
        }
    }

}
