package dev.aurora.injector;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.util.function.Consumer;

/** Shared dark-theme colors and hand-painted Swing widgets used by both the instance launcher and
 * the control panel, so the two windows look like one product instead of two. */
final class SwingTheme {
    static final Color BACKGROUND = new Color(0x0F, 0x0F, 0x15);
    static final Color CARD = new Color(0x1C, 0x1C, 0x26);
    static final Color CARD_HOVER = new Color(0x25, 0x25, 0x32);
    static final Color CARD_BORDER = new Color(0x2E, 0x2E, 0x3C);
    static final Color TEXT = new Color(0xF2, 0xF2, 0xF7);
    static final Color MUTED_TEXT = new Color(0x9A, 0x9A, 0xAA);
    static Color ACCENT = GuiPreferences.DEFAULT_ACCENT;
    static Color ACCENT_HOVER = lighten(ACCENT, 0.14D);
    static final Color DANGER = new Color(0xFC, 0x5C, 0x5C);
    static final Color DANGER_HOVER = new Color(0xFF, 0x74, 0x74);
    static final Color TOGGLE_OFF = new Color(0x3A, 0x3A, 0x48);

    private SwingTheme() {
    }

    static void setAccent(Color accent) {
        ACCENT = accent == null ? GuiPreferences.DEFAULT_ACCENT : accent;
        ACCENT_HOVER = lighten(ACCENT, 0.14D);
    }

    /** Scales a pixel size (widths, heights, insets, gaps) by the current GUI scale factor. */
    static int scale(int px) {
        return Math.max(1, Math.round(px * (float) UiScale.factor()));
    }

    /** Scales a point size by the current GUI scale factor. */
    static float scaleFont(float pt) {
        return (float) (pt * UiScale.factor());
    }

    /** Chooses whichever of black or white has the stronger WCAG contrast against the background. */
    static Color contrastText(Color background) {
        double luminance = relativeLuminance(background);
        double blackContrast = (luminance + 0.05D) / 0.05D;
        double whiteContrast = 1.05D / (luminance + 0.05D);
        return blackContrast >= whiteContrast ? Color.BLACK : Color.WHITE;
    }

    static Color contrastMuted(Color background) {
        Color foreground = contrastText(background);
        double weight = 0.72D;
        return new Color(
                (int) Math.round(background.getRed() * (1.0D - weight) + foreground.getRed() * weight),
                (int) Math.round(background.getGreen() * (1.0D - weight) + foreground.getGreen() * weight),
                (int) Math.round(background.getBlue() * (1.0D - weight) + foreground.getBlue() * weight)
        );
    }

    private static double relativeLuminance(Color color) {
        return 0.2126D * linear(color.getRed())
                + 0.7152D * linear(color.getGreen())
                + 0.0722D * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0D;
        return value <= 0.04045D ? value / 12.92D : Math.pow((value + 0.055D) / 1.055D, 2.4D);
    }

    static Color lighten(Color color, double amount) {
        return new Color(
                color.getRed() + (int) Math.round((255 - color.getRed()) * amount),
                color.getGreen() + (int) Math.round((255 - color.getGreen()) * amount),
                color.getBlue() + (int) Math.round((255 - color.getBlue()) * amount)
        );
    }

    /** A JPanel that paints its background as a filled, outlined rounded rectangle. */
    static final class RoundedPanel extends javax.swing.JPanel {
        private final int arc;
        private Color background;

        RoundedPanel(int arc, Color background) {
            this.arc = scale(arc);
            this.background = background;
            setOpaque(false);
        }

        void setBackgroundColor(Color color) {
            this.background = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(background);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A pill-shaped, flat-colored button with a lighter hover fill. */
    static final class PillButton extends JButton {
        private final Color base;
        private final Color hover;
        private boolean hovered;

        PillButton(String text, Color base, Color hover) {
            super(text);
            this.base = base;
            this.hover = hover;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(contrastText(base));
            setFont(getFont().deriveFont(Font.BOLD, scaleFont(12.5f)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            setForeground(contrastText(hovered ? hover : base));
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered ? hover : base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A small on/off pill switch, similar to a mobile settings toggle. */
    static final class ToggleSwitch extends JComponent {
        private final int width = scale(40);
        private final int height = scale(20);

        private boolean checked;
        private Color checkedTrackColor = ACCENT;
        private Consumer<Boolean> onChange = value -> { };

        ToggleSwitch(boolean initialChecked) {
            this.checked = initialChecked;
            setFocusable(true);
            setPreferredSize(new Dimension(width, height));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    setChecked(!checked);
                    onChange.accept(checked);
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_SPACE || event.getKeyCode() == KeyEvent.VK_ENTER) {
                        event.consume();
                        setChecked(!checked);
                        onChange.accept(checked);
                    }
                }
            });
        }

        void setChecked(boolean checked) {
            if (this.checked != checked) {
                this.checked = checked;
                repaint();
            }
        }

        boolean isChecked() {
            return checked;
        }

        void setCheckedTrackColor(Color color) {
            checkedTrackColor = color == null ? ACCENT : color;
            repaint();
        }

        void onChange(Consumer<Boolean> onChange) {
            this.onChange = onChange;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color trackColor = checked ? checkedTrackColor : TOGGLE_OFF;
            g2.setColor(trackColor);
            g2.fillRoundRect(0, 0, width, height, height, height);
            int thumbDiameter = height - scale(4);
            int thumbX = checked ? width - thumbDiameter - scale(2) : scale(2);
            g2.setColor(checked ? contrastText(trackColor) : Color.WHITE);
            g2.fillOval(thumbX, scale(2), thumbDiameter, thumbDiameter);
            if (isFocusOwner()) {
                g2.setColor(checked ? contrastText(trackColor) : TEXT);
                g2.drawRoundRect(0, 0, width - 1, height - 1, height, height);
            }
            g2.dispose();
        }
    }

    /** A JSlider re-skinned with a pill-shaped track and a round "ball" thumb instead of the
     * platform's default look, for single-value numeric settings. */
    static final class BallSlider extends JSlider {
        BallSlider(int minimum, int maximum, int value) {
            super(minimum, maximum, value);
            setOpaque(false);
            setFocusable(false);
            setUI(new BallSliderUI(this));
        }
    }

    private static final class BallSliderUI extends BasicSliderUI {
        private static final int TRACK_HEIGHT = 6;
        private static final int THUMB_DIAMETER = 16;
        private static final int ANIMATION_MILLIS = 180;

        private Timer animation;

        private BallSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int trackHeight = scale(TRACK_HEIGHT);
            int trackTop = trackRect.y + (trackRect.height - trackHeight) / 2;
            int thumbCenter = thumbRect.x + thumbRect.width / 2;
            g2.setColor(TOGGLE_OFF);
            g2.fillRoundRect(trackRect.x, trackTop, trackRect.width, trackHeight, trackHeight, trackHeight);
            g2.setColor(ACCENT);
            g2.fillRoundRect(trackRect.x, trackTop, Math.max(0, thumbCenter - trackRect.x),
                    trackHeight, trackHeight, trackHeight);
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBall(g2, thumbRect.x + thumbRect.width / 2, thumbRect.y + thumbRect.height / 2, scale(THUMB_DIAMETER));
            g2.dispose();
        }

        @Override
        protected Dimension getThumbSize() {
            int diameter = scale(THUMB_DIAMETER);
            return new Dimension(diameter, diameter);
        }

        @Override
        protected TrackListener createTrackListener(JSlider slider) {
            return new TrackListener() {
                private boolean draggingThumb;

                @Override
                public void mousePressed(MouseEvent event) {
                    if (!BallSliderUI.this.slider.isEnabled()) return;
                    draggingThumb = thumbRect.contains(event.getPoint());
                    if (draggingThumb) {
                        stopAnimation();
                        super.mousePressed(event);
                        return;
                    }
                    animateTo(valueForXPosition(event.getX()));
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (draggingThumb) super.mouseDragged(event);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (draggingThumb) super.mouseReleased(event);
                    draggingThumb = false;
                }
            };
        }

        private void animateTo(int target) {
            stopAnimation();
            int start = slider.getValue();
            int boundedTarget = Math.max(slider.getMinimum(), Math.min(slider.getMaximum(), target));
            if (start == boundedTarget) return;
            long startedAt = System.nanoTime();
            slider.setValueIsAdjusting(true);
            animation = new Timer(1000 / 60, event -> {
                double elapsed = (System.nanoTime() - startedAt) / 1_000_000.0D;
                double progress = Math.min(1.0D, elapsed / ANIMATION_MILLIS);
                slider.setValue((int) Math.round(start + (boundedTarget - start) * easeOutCubic(progress)));
                if (progress >= 1.0D) stopAnimation();
            });
            animation.start();
        }

        private void stopAnimation() {
            if (animation != null) {
                animation.stop();
                animation = null;
                slider.setValueIsAdjusting(false);
            }
        }

        @Override
        public void paintFocus(Graphics g) {
            // Intentionally blank: the platform focus rectangle clashes with the custom ball thumb.
        }
    }

    /** A horizontal slider with two independently draggable ball thumbs sharing one track, for
     * settings defined as a min/max pair (e.g. reaction time, clicks-per-second) instead of two
     * separate single-value sliders. */
    static final class RangeSlider extends JComponent {
        private static final int TRACK_HEIGHT = 6;
        private static final int THUMB_DIAMETER = 16;
        private static final int ANIMATION_MILLIS = 180;

        private final int minimum;
        private final int maximum;
        private int low;
        private int high;
        private boolean draggingHigh;
        private boolean draggingThumb;
        private boolean coincidentThumbsAtPress;
        private int pressX;
        private Timer animation;
        private Consumer<Boolean> onChange = adjusting -> { };

        RangeSlider(int minimum, int maximum, int low, int high) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.low = clamp(Math.min(low, high));
            this.high = clamp(Math.max(low, high));
            setOpaque(false);
            setFocusable(true);
            setPreferredSize(new Dimension(scale(120), scale(THUMB_DIAMETER) + scale(8)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                    pressX = event.getX();
                    int lowX = xFor(RangeSlider.this.low);
                    int highX = xFor(RangeSlider.this.high);
                    int lowDistance = Math.abs(pressX - lowX);
                    int highDistance = Math.abs(pressX - highX);
                    coincidentThumbsAtPress = lowX == highX;
                    if (coincidentThumbsAtPress) {
                        // A collapsed range must be expandable in either direction. Selecting the
                        // low thumb unconditionally makes a 0-0 range impossible to move right,
                        // while selecting high makes a max-max range impossible to move left.
                        draggingHigh = pressX > lowX;
                    } else if (pressX <= lowX) {
                        draggingHigh = false;
                    } else if (pressX >= highX) {
                        draggingHigh = true;
                    } else {
                        draggingHigh = highDistance < lowDistance;
                    }
                    draggingThumb = Math.min(lowDistance, highDistance) <= scale(THUMB_DIAMETER) / 2;
                    if (draggingThumb) {
                        stopAnimation(false);
                    } else {
                        animateTo(valueFor(event.getX()));
                    }
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (coincidentThumbsAtPress && event.getX() != pressX) {
                        draggingHigh = event.getX() > pressX;
                        coincidentThumbsAtPress = false;
                    }
                    if (!draggingThumb) {
                        stopAnimation(false);
                        draggingThumb = true;
                    }
                    dragTo(event.getX());
                    onChange.accept(true);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (draggingThumb) onChange.accept(false);
                    draggingThumb = false;
                    coincidentThumbsAtPress = false;
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        int lowValue() {
            return low;
        }

        int highValue() {
            return high;
        }

        void setLowValue(int value) {
            stopAnimation(false);
            low = Math.min(clamp(value), high);
            repaint();
        }

        void setHighValue(int value) {
            stopAnimation(false);
            high = Math.max(clamp(value), low);
            repaint();
        }

        void onChange(Consumer<Boolean> onChange) {
            this.onChange = onChange;
        }

        private int clamp(int value) {
            return Math.max(minimum, Math.min(maximum, value));
        }

        private void dragTo(int mouseX) {
            int value = valueFor(mouseX);
            if (draggingHigh) {
                high = Math.max(value, low);
            } else {
                low = Math.min(value, high);
            }
            repaint();
        }

        private void animateTo(int target) {
            stopAnimation(false);
            int start = draggingHigh ? high : low;
            int boundedTarget = draggingHigh ? Math.max(clamp(target), low) : Math.min(clamp(target), high);
            if (start == boundedTarget) return;
            long startedAt = System.nanoTime();
            animation = new Timer(1000 / 60, event -> {
                double elapsed = (System.nanoTime() - startedAt) / 1_000_000.0D;
                double progress = Math.min(1.0D, elapsed / ANIMATION_MILLIS);
                int value = (int) Math.round(start + (boundedTarget - start) * easeOutCubic(progress));
                if (draggingHigh) high = value; else low = value;
                repaint();
                onChange.accept(progress < 1.0D);
                if (progress >= 1.0D) stopAnimation(false);
            });
            animation.start();
        }

        private void stopAnimation(boolean commit) {
            if (animation == null) return;
            animation.stop();
            animation = null;
            if (commit) onChange.accept(false);
        }

        private int trackInset() {
            return scale(THUMB_DIAMETER) / 2;
        }

        private int valueFor(int mouseX) {
            int inset = trackInset();
            int span = Math.max(1, getWidth() - inset * 2);
            double ratio = Math.max(0.0D, Math.min(1.0D, (mouseX - inset) / (double) span));
            return (int) Math.round(minimum + ratio * (maximum - minimum));
        }

        private int xFor(int value) {
            int inset = trackInset();
            int span = Math.max(1, getWidth() - inset * 2);
            if (maximum == minimum) {
                return inset;
            }
            double ratio = (value - minimum) / (double) (maximum - minimum);
            return inset + (int) Math.round(ratio * span);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int trackHeight = scale(TRACK_HEIGHT);
            int trackY = (getHeight() - trackHeight) / 2;
            int lowX = xFor(low);
            int highX = xFor(high);
            g2.setColor(TOGGLE_OFF);
            g2.fillRoundRect(0, trackY, getWidth(), trackHeight, trackHeight, trackHeight);
            g2.setColor(ACCENT);
            g2.fillRoundRect(lowX, trackY, Math.max(0, highX - lowX), trackHeight, trackHeight, trackHeight);
            int diameter = scale(THUMB_DIAMETER);
            int thumbY = getHeight() / 2;
            // Thumbs point inward at each other (low points right, high points left) so it reads at
            // a glance which edge of the selected range each handle controls.
            paintArrowThumb(g2, lowX, thumbY, diameter, true);
            paintArrowThumb(g2, highX, thumbY, diameter, false);
            g2.dispose();
        }
    }

    private static double easeOutCubic(double progress) {
        double remaining = 1.0D - progress;
        return 1.0D - remaining * remaining * remaining;
    }

    /** Paints a sleek arrow-shaped thumb (flat outer edge, pointed inner tip) centered at {@code
     * (centerX, centerY)}, used by {@link RangeSlider} so its two handles are visually distinct from
     * the single-thumb {@link BallSlider} and from each other. */
    private static void paintArrowThumb(Graphics2D g2, int centerX, int centerY, int size, boolean pointRight) {
        double halfWidth = size / 2.0D;
        double halfHeight = size / 2.0D;
        double flatX = pointRight ? centerX - halfWidth : centerX + halfWidth;
        double tipX = pointRight ? centerX + halfWidth : centerX - halfWidth;
        double shoulderX = pointRight ? flatX + size * 0.32D : flatX - size * 0.32D;
        double top = centerY - halfHeight;
        double bottom = centerY + halfHeight;
        double corner = size * 0.2D;
        double cornerSign = pointRight ? 1.0D : -1.0D;

        Path2D.Double path = new Path2D.Double();
        path.moveTo(flatX, top + corner);
        path.quadTo(flatX, top, flatX + corner * cornerSign, top);
        path.lineTo(shoulderX, top);
        path.lineTo(tipX, centerY);
        path.lineTo(shoulderX, bottom);
        path.lineTo(flatX + corner * cornerSign, bottom);
        path.quadTo(flatX, bottom, flatX, bottom - corner);
        path.closePath();

        g2.setColor(ACCENT);
        g2.fill(path);
        g2.setColor(CARD);
        g2.setStroke(new BasicStroke(Math.max(1f, (float) (size * 0.09D))));
        double gripInset = size * 0.3D;
        double gripX = flatX + gripInset * cornerSign;
        g2.draw(new java.awt.geom.Line2D.Double(gripX, centerY - size * 0.16D, gripX, centerY + size * 0.16D));
    }

    /** Paints a filled ball thumb (accent ring, card-colored center) centered at {@code (centerX,
     * centerY)}, shared by {@link BallSliderUI} and {@link RangeSlider}. */
    private static void paintBall(Graphics2D g2, int centerX, int centerY, int diameter) {
        int x = centerX - diameter / 2;
        int y = centerY - diameter / 2;
        g2.setColor(ACCENT);
        g2.fillOval(x, y, diameter, diameter);
        int inset = scale(4);
        if (diameter - inset * 2 > 0) {
            g2.setColor(CARD);
            g2.fillOval(x + inset, y + inset, diameter - inset * 2, diameter - inset * 2);
        }
    }

    /** Minimal dark-themed scrollbar so the default light thumb doesn't clash with the theme. */
    static final class DarkScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = CARD_BORDER;
            thumbDarkShadowColor = CARD_BORDER;
            thumbHighlightColor = CARD_BORDER;
            thumbLightShadowColor = CARD_BORDER;
            trackColor = BACKGROUND;
            trackHighlightColor = BACKGROUND;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent component, Rectangle bounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(bounds.x + 2, bounds.y, bounds.width - 4, bounds.height, 6, 6);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent component, Rectangle bounds) {
            g.setColor(trackColor);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }
}
