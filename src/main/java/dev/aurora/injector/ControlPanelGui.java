package dev.aurora.injector;

import dev.aurora.util.Json;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;

import static dev.aurora.injector.SwingTheme.ACCENT;
import static dev.aurora.injector.SwingTheme.BACKGROUND;
import static dev.aurora.injector.SwingTheme.CARD;
import static dev.aurora.injector.SwingTheme.CARD_BORDER;
import static dev.aurora.injector.SwingTheme.CARD_HOVER;
import static dev.aurora.injector.SwingTheme.DANGER;
import static dev.aurora.injector.SwingTheme.DANGER_HOVER;
import static dev.aurora.injector.SwingTheme.MUTED_TEXT;
import static dev.aurora.injector.SwingTheme.TEXT;

/** Desktop control surface for the injected agent. */
public final class ControlPanelGui extends JFrame {
    private static final int POLL_INTERVAL_MS = 300;
    private static final int RAINBOW_INTERVAL_MS = 40;
    private static final Color SIDEBAR = new Color(0x12, 0x11, 0x17);
    private static final Color TOPBAR = new Color(0x17, 0x16, 0x1D);
    private static final Color FIELD = new Color(0x24, 0x23, 0x2C);
    private static final Color SUCCESS = new Color(0x58, 0xD6, 0x8D);
    private static final String FAVORITES = "Favorites";
    private static final String ALL = "All modules";
    private static final String SETTINGS = "Settings";
    private static final String FRIENDS = "Friends";
    private static final String TARGET_RING_MODULE_ID = "target-ring";

    private final AgentConnectionHub agentHub;
    private final Preferences preferences = Preferences.userNodeForPackage(ControlPanelGui.class);
    private final Set<String> favorites = new LinkedHashSet<>();
    private final Set<String> friends = new LinkedHashSet<>();
    private final PlayerHeadCache headCache = new PlayerHeadCache();
    private final Set<String> expandedModules = new LinkedHashSet<>();
    private final JPanel navigationPanel = new JPanel();
    private final JPanel bottomNavigationPanel = new JPanel();
    private final JPanel listPanel = new JPanel();
    private JScrollPane listScroll;
    private final JLabel pageTitle = new JLabel(ALL);
    private final JLabel resultCount = new JLabel("0 modules");
    private final JLabel connectionLabel = new JLabel("Connecting...");
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel footerHelpLabel = new JLabel("Enter applies values  ·  Backspace/Delete clears a keybind");
    private final JTextField searchField = new JTextField();
    private final SwingTheme.PillButton uninjectButton =
            new SwingTheme.PillButton("Uninject", DANGER, DANGER_HOVER);
    private final Timer pollTimer;
    private final Timer rainbowTimer;
    private final List<RainbowCard> rainbowCards = new ArrayList<>();
    private float rainbowPhase;

    private List<ModuleView> modules = List.of();
    private List<String> onlinePlayers = List.of();
    private String selectedCategory = ALL;
    private String lastModulesJson = "";
    private String lastSampleJson = "";
    private String bindingModuleId;
    private boolean pointerInteraction;
    private boolean uninjectRequested;

    public ControlPanelGui(AgentConnectionHub agentHub) {
        super("Aurora — Control Panel");
        this.agentHub = agentHub;
        SwingTheme.setAccent(GuiPreferences.accentColor());
        loadFavorites();
        friends.addAll(GuiPreferences.friends());
        ToolTipManager.sharedInstance().setInitialDelay(0);
        ToolTipManager.sharedInstance().setReshowDelay(0);
        ToolTipManager.sharedInstance().setDismissDelay(12_000);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(SwingTheme.scale(1024), SwingTheme.scale(680));
        setMinimumSize(new Dimension(SwingTheme.scale(760), SwingTheme.scale(520)));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BACKGROUND);
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder());

        add(topBar(), BorderLayout.NORTH);
        add(sidebar(), BorderLayout.WEST);
        add(content(), BorderLayout.CENTER);

        pollTimer = new Timer(POLL_INTERVAL_MS, event -> poll());
        pollTimer.start();
        rainbowTimer = new Timer(RAINBOW_INTERVAL_MS, event -> tickRainbow());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                pollTimer.stop();
                rainbowTimer.stop();
            }
        });
        poll();
    }

    private JComponent topBar() {
        JPanel bar = new JPanel(new BorderLayout(SwingTheme.scale(24), SwingTheme.scale(0)));
        bar.setBackground(TOPBAR);
        bar.setBorder(new EmptyBorder(SwingTheme.scale(16), SwingTheme.scale(24), SwingTheme.scale(16), SwingTheme.scale(24)));
        bar.setPreferredSize(new Dimension(SwingTheme.scale(10), SwingTheme.scale(72)));

        JLabel wordmark = new JLabel("AURORA");
        wordmark.setForeground(TEXT);
        wordmark.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(SwingTheme.scaleFont(22f))));
        wordmark.setPreferredSize(new Dimension(SwingTheme.scale(150), SwingTheme.scale(38)));
        bar.add(wordmark, BorderLayout.WEST);

        searchField.setToolTipText("Search modules and settings");
        searchField.setBackground(FIELD);
        searchField.setForeground(TEXT);
        searchField.setCaretColor(TEXT);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER), new EmptyBorder(SwingTheme.scale(9), SwingTheme.scale(13), SwingTheme.scale(9), SwingTheme.scale(13))));
        searchField.setFont(searchField.getFont().deriveFont(SwingTheme.scaleFont(13f)));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { rebuildModuleList(); }
            @Override public void removeUpdate(DocumentEvent event) { rebuildModuleList(); }
            @Override public void changedUpdate(DocumentEvent event) { rebuildModuleList(); }
        });
        JPanel searchHolder = new JPanel(new BorderLayout());
        searchHolder.setOpaque(false);
        searchHolder.setBorder(new EmptyBorder(SwingTheme.scale(1), SwingTheme.scale(40), SwingTheme.scale(1), SwingTheme.scale(40)));
        searchHolder.add(searchField);
        bar.add(searchHolder, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingTheme.scale(12), SwingTheme.scale(0)));
        actions.setOpaque(false);
        connectionLabel.setForeground(MUTED_TEXT);
        connectionLabel.setFont(connectionLabel.getFont().deriveFont(SwingTheme.scaleFont(12f)));
        actions.add(connectionLabel);
        uninjectButton.setPreferredSize(new Dimension(SwingTheme.scale(98), SwingTheme.scale(36)));
        uninjectButton.addActionListener(event -> requestUninject());
        actions.add(uninjectButton);
        bar.add(actions, BorderLayout.EAST);
        return bar;
    }

    private JComponent sidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(SIDEBAR);
        sidebar.setBorder(new EmptyBorder(SwingTheme.scale(24), SwingTheme.scale(18), SwingTheme.scale(20), SwingTheme.scale(18)));
        sidebar.setPreferredSize(new Dimension(SwingTheme.scale(190), SwingTheme.scale(10)));

        navigationPanel.setLayout(new BoxLayout(navigationPanel, BoxLayout.Y_AXIS));
        navigationPanel.setOpaque(false);
        sidebar.add(navigationPanel, BorderLayout.NORTH);

        bottomNavigationPanel.setLayout(new BoxLayout(bottomNavigationPanel, BoxLayout.Y_AXIS));
        bottomNavigationPanel.setOpaque(false);
        JLabel hint = new JLabel("Configure modules and appearance");
        hint.setForeground(MUTED_TEXT);
        hint.setFont(hint.getFont().deriveFont(SwingTheme.scaleFont(10.5f)));
        hint.setBorder(new EmptyBorder(SwingTheme.scale(8), SwingTheme.scale(0), SwingTheme.scale(0), SwingTheme.scale(0)));
        bottomNavigationPanel.add(navigationButton("☺  " + FRIENDS, FRIENDS));
        bottomNavigationPanel.add(Box.createVerticalStrut(SwingTheme.scale(5)));
        bottomNavigationPanel.add(navigationButton("⚙  " + SETTINGS, SETTINGS));
        bottomNavigationPanel.add(hint);
        sidebar.add(bottomNavigationPanel, BorderLayout.SOUTH);
        rebuildNavigation();
        return sidebar;
    }

    private JComponent content() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BACKGROUND);
        content.setBorder(new EmptyBorder(SwingTheme.scale(24), SwingTheme.scale(28), SwingTheme.scale(16), SwingTheme.scale(24)));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(SwingTheme.scale(0), SwingTheme.scale(4), SwingTheme.scale(18), SwingTheme.scale(4)));
        pageTitle.setForeground(TEXT);
        pageTitle.setFont(pageTitle.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(21f)));
        heading.add(pageTitle, BorderLayout.WEST);
        resultCount.setForeground(MUTED_TEXT);
        resultCount.setFont(resultCount.getFont().deriveFont(SwingTheme.scaleFont(12f)));
        heading.add(resultCount, BorderLayout.EAST);
        content.add(heading, BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(BACKGROUND);
        listPanel.setBorder(new EmptyBorder(SwingTheme.scale(0), SwingTheme.scale(0), SwingTheme.scale(8), SwingTheme.scale(8)));
        listScroll = new JScrollPane(listPanel);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.getViewport().setBackground(BACKGROUND);
        listScroll.getVerticalScrollBar().setUnitIncrement(18);
        listScroll.getVerticalScrollBar().setUI(new SwingTheme.DarkScrollBarUI());
        listScroll.getHorizontalScrollBar().setUnitIncrement(18);
        content.add(listScroll, BorderLayout.CENTER);
        return content;
    }

    private void rebuildNavigation() {
        navigationPanel.removeAll();
        JLabel heading = new JLabel("LIBRARY");
        heading.setForeground(MUTED_TEXT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(10.5f)));
        heading.setBorder(new EmptyBorder(SwingTheme.scale(0), SwingTheme.scale(10), SwingTheme.scale(10), SwingTheme.scale(0)));
        heading.setAlignmentX(0f);
        navigationPanel.add(heading);
        navigationPanel.add(navigationButton("⌂  " + ALL, ALL));
        navigationPanel.add(Box.createVerticalStrut(SwingTheme.scale(5)));
        navigationPanel.add(navigationButton("★  " + FAVORITES, FAVORITES));
        navigationPanel.add(Box.createVerticalStrut(SwingTheme.scale(20)));

        JLabel categoryHeading = new JLabel("CATEGORIES");
        categoryHeading.setForeground(MUTED_TEXT);
        categoryHeading.setFont(categoryHeading.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(10.5f)));
        categoryHeading.setBorder(new EmptyBorder(SwingTheme.scale(0), SwingTheme.scale(10), SwingTheme.scale(9), SwingTheme.scale(0)));
        categoryHeading.setAlignmentX(0f);
        navigationPanel.add(categoryHeading);
        modules.stream().map(ModuleView::category).distinct().sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(category -> {
                    navigationPanel.add(navigationButton("•  " + category, category));
                    navigationPanel.add(Box.createVerticalStrut(SwingTheme.scale(3)));
                });
        navigationPanel.revalidate();
        navigationPanel.repaint();
        rebuildBottomNavigation();
    }

    private void rebuildBottomNavigation() {
        if (bottomNavigationPanel.getComponentCount() == 0) return;
        Component hint = bottomNavigationPanel.getComponent(bottomNavigationPanel.getComponentCount() - 1);
        bottomNavigationPanel.removeAll();
        bottomNavigationPanel.add(navigationButton("☺  " + FRIENDS, FRIENDS));
        bottomNavigationPanel.add(Box.createVerticalStrut(SwingTheme.scale(5)));
        bottomNavigationPanel.add(navigationButton("⚙  " + SETTINGS, SETTINGS));
        bottomNavigationPanel.add(hint);
        bottomNavigationPanel.revalidate();
        bottomNavigationPanel.repaint();
    }

    private JButton navigationButton(String text, String category) {
        boolean selected = category.equals(selectedCategory);
        NavButton button = new NavButton(text, selected);
        button.addActionListener(event -> {
            selectedCategory = category;
            pageTitle.setText(category);
            resetListScroll();
            boolean dedicated = SETTINGS.equals(category) || FRIENDS.equals(category);
            searchField.setEnabled(!dedicated);
            footerHelpLabel.setText(SETTINGS.equals(category)
                    ? "Appearance changes are saved automatically"
                    : FRIENDS.equals(category)
                    ? "Friends are ignored by combat modules and shown in the ESP friend color"
                    : "Enter applies values  ·  Backspace/Delete clears a keybind");
            rebuildNavigation();
            rebuildModuleList();
        });
        return button;
    }

    private void poll() {
        updateConnectionStatus();
        updateOnlinePlayers();
        if (isEditing()) {
            return;
        }
        String modulesJson = agentHub.modulesJson();
        if (modulesJson.equals(lastModulesJson)) {
            return;
        }
        try {
            modules = parseModules(modulesJson);
            lastModulesJson = modulesJson;
            rebuildNavigation();
            rebuildModuleList();
        } catch (IllegalArgumentException exception) {
            showStatus("Could not read module data: " + exception.getMessage(), true);
        }
    }

    private void updateOnlinePlayers() {
        String sampleJson = agentHub.eventSampleJson();
        if (sampleJson.equals(lastSampleJson)) {
            return;
        }
        lastSampleJson = sampleJson;
        List<String> names = new ArrayList<>();
        try {
            if (Json.parse(sampleJson) instanceof Map<?, ?> sample
                    && sample.get("onlinePlayers") instanceof List<?> players) {
                for (Object player : players) {
                    if (player instanceof String name && !name.isBlank()) {
                        names.add(name);
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            return;
        }
        onlinePlayers = names;
    }

    private void updateConnectionStatus() {
        try {
            Object parsed = Json.parse(agentHub.statusJson());
            if (!(parsed instanceof Map<?, ?> status)) {
                return;
            }
            boolean attached = Boolean.TRUE.equals(status.get("attached"));
            Object rawMessage = status.get("message");
            String message = rawMessage == null ? "Unknown status" : String.valueOf(rawMessage);
            // Show a stable connection state instead of echoing transient agent messages like
            // "Updated module esp" or "Agent active".
            connectionLabel.setText((attached ? "●  Connected" : "○  " + message));
            connectionLabel.setForeground(attached ? SUCCESS : DANGER);
            if (uninjectRequested && !attached) {
                uninjectButton.setText("Uninjected");
                statusLabel.setText("Aurora has been uninjected. You can close this window.");
            }
        } catch (IllegalArgumentException ignored) {
            connectionLabel.setText("○  Invalid agent status");
            connectionLabel.setForeground(DANGER);
        }
    }

    private boolean isEditing() {
        if (pointerInteraction || bindingModuleId != null) {
            return true;
        }
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return (focus instanceof JTextField || focus instanceof JComboBox<?>)
                && SwingUtilities.isDescendingFrom(focus, listPanel);
    }

    private void rebuildModuleList() {
        if (listPanel == null) {
            return;
        }
        stopRainbowAnimation();
        if (SETTINGS.equals(selectedCategory)) {
            rebuildSettingsPage();
            return;
        }
        if (FRIENDS.equals(selectedCategory)) {
            rebuildFriendsPage();
            return;
        }
        String query = searchField.getText().strip().toLowerCase(Locale.ROOT);
        List<ModuleView> visible = modules.stream()
                .filter(module -> !TARGET_RING_MODULE_ID.equals(module.id()))
                .filter(module -> matchesCategory(module, selectedCategory))
                .filter(module -> module.matches(query))
                .toList();

        listPanel.removeAll();
        if (visible.isEmpty()) {
            listPanel.add(emptyState(query));
        } else {
            for (int index = 0; index < visible.size(); index++) {
                listPanel.add(moduleCard(visible.get(index), index, visible.size()));
                listPanel.add(Box.createVerticalStrut(SwingTheme.scale(10)));
            }
        }
        resultCount.setText(visible.size() + (visible.size() == 1 ? " module" : " modules"));
        listPanel.revalidate();
        listPanel.repaint();
        if (!rainbowCards.isEmpty()) {
            rainbowTimer.start();
        }
    }

    private void stopRainbowAnimation() {
        if (rainbowTimer != null) {
            rainbowTimer.stop();
        }
        rainbowCards.clear();
    }

    /** Advances the rainbow animation one step and recolors every enrolled module card from the shared
     * phase. All cards are recolored together (not just the visible ones) so the rainbow is one
     * continuous flow anchored to the whole list — the first module is the start of the spectrum and
     * the last is the end — and scrolling simply reveals more of it instead of re-tinting whatever
     * card sits at the top of the viewport. Off-screen repaints are clipped away, and the flat card
     * fills are cheap, so recoloring all of them stays smooth while scrolling. */
    private void tickRainbow() {
        if (rainbowCards.isEmpty()) {
            return;
        }
        rainbowPhase += 0.02f;
        if (rainbowPhase >= 1f) {
            rainbowPhase -= 1f;
        }
        for (RainbowCard card : rainbowCards) {
            Color color = rainbowColor(card.position, rainbowPhase, card.enabled);
            card.panel.setBackgroundColor(card.hovered ? SwingTheme.lighten(color, 0.12D) : color);
        }
    }

    /** A module card enrolled in the rainbow animation, remembering its fixed position in the list so
     * its hue can be recomputed each frame from the shared phase. */
    private static final class RainbowCard {
        private final SwingTheme.RoundedPanel panel;
        private final float position;
        private final boolean enabled;
        private boolean hovered;

        private RainbowCard(SwingTheme.RoundedPanel panel, float position, boolean enabled) {
            this.panel = panel;
            this.position = position;
            this.enabled = enabled;
        }
    }

    private void rebuildSettingsPage() {
        int scrollY = currentListScrollY();
        listPanel.setVisible(false);
        listPanel.removeAll();
        listPanel.add(appearanceCard());
        listPanel.add(Box.createVerticalStrut(SwingTheme.scale(12)));
        listPanel.add(silentAimCard());
        listPanel.add(Box.createVerticalStrut(SwingTheme.scale(12)));
        listPanel.add(targetRingSettingsCard());
        listPanel.add(Box.createVerticalStrut(SwingTheme.scale(12)));
        listPanel.add(scaleCard());
        finishDedicatedPageRebuild(SETTINGS, "Appearance, aim, ring & scale", scrollY);
    }

    private JComponent targetRingSettingsCard() {
        ModuleView ring = modules.stream()
                .filter(module -> TARGET_RING_MODULE_ID.equals(module.id()))
                .findFirst()
                .orElse(null);
        return ring == null ? targetRingUnavailable() : targetRingCard(ring);
    }

    private JComponent silentAimCard() {
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(16, CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(SwingTheme.scale(22), SwingTheme.scale(24),
                SwingTheme.scale(22), SwingTheme.scale(24)));
        card.setAlignmentX(0f);

        JLabel title = new JLabel("Silent Aim");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(16f)));
        title.setAlignmentX(0f);
        card.add(title);

        JLabel description = new JLabel("Global visual feedback shared by every feature that uses silent aim.");
        description.setForeground(MUTED_TEXT);
        description.setFont(description.getFont().deriveFont(SwingTheme.scaleFont(11.5f)));
        description.setBorder(new EmptyBorder(SwingTheme.scale(5), 0, SwingTheme.scale(18), 0));
        description.setAlignmentX(0f);
        card.add(description);

        JPanel row = new JPanel(new BorderLayout(SwingTheme.scale(14), 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(38)));
        row.setAlignmentX(0f);
        JLabel label = new JLabel("Crosshair direction line");
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(12.5f)));
        label.setPreferredSize(new Dimension(SwingTheme.scale(185), SwingTheme.scale(34)));
        label.setToolTipText("Shows where the active decoupled silent aim is looking.");
        row.add(label, BorderLayout.WEST);

        SwingTheme.ToggleSwitch toggle = new SwingTheme.ToggleSwitch(GuiPreferences.silentAimCrosshairIndicator());
        toggle.onChange(enabled -> {
            GuiPreferences.setSilentAimCrosshairIndicator(enabled);
            if (!agentHub.sendGlobalSettings(enabled)) {
                showStatus("Saved; it will apply when an agent connects.", false);
            } else {
                showStatus("Silent Aim crosshair line " + (enabled ? "enabled" : "disabled"), false);
            }
        });
        markInteractive(toggle);
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, SwingTheme.scale(7)));
        holder.setOpaque(false);
        holder.add(toggle);
        row.add(holder, BorderLayout.CENTER);
        card.add(row);

        int contentHeight = card.getPreferredSize().height;
        card.setPreferredSize(new Dimension(SwingTheme.scale(10), contentHeight));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentHeight));
        return card;
    }

    /** Rebuilds the whole window from scratch so a changed {@link UiScale} factor takes effect right
     * away instead of only after the panel is reopened: every panel recomputes its sizes and fonts
     * from {@code SwingTheme.scale(...)} as it is rebuilt. */
    private void applyScaleLive() {
        stopRainbowAnimation();
        getContentPane().removeAll();
        add(topBar(), BorderLayout.NORTH);
        add(sidebar(), BorderLayout.WEST);
        add(content(), BorderLayout.CENTER);
        setMinimumSize(new Dimension(SwingTheme.scale(760), SwingTheme.scale(520)));
        rebuildNavigation();
        rebuildModuleList();
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private void finishDedicatedPageRebuild(String title, String count, int scrollY) {
        pageTitle.setText(title);
        resultCount.setText(count);
        listPanel.setVisible(true);
        listPanel.revalidate();
        if (listScroll != null) {
            listScroll.getViewport().revalidate();
            listScroll.getViewport().repaint();
            SwingUtilities.invokeLater(() -> restoreListScrollY(scrollY));
        }
        // Repaint the full content subtree. Linux Swing can otherwise retain pixels from the
        // previous module page when an opaque card list is replaced by custom non-opaque cards.
        listPanel.repaint();
        JComponent root = (JComponent) listPanel.getParent();
        while (root.getParent() instanceof JComponent parent) root = parent;
        root.revalidate();
        root.repaint();
    }

    private JComponent scaleCard() {
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(16, CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(SwingTheme.scale(22), SwingTheme.scale(24), SwingTheme.scale(22), SwingTheme.scale(24)));
        card.setAlignmentX(0f);

        JLabel title = new JLabel("Interface scale");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(16f)));
        title.setAlignmentX(0f);
        card.add(title);

        JLabel description = new JLabel(
                "Scales module cards, text and controls up for high-resolution displays. "
                        + "Applies immediately.");
        description.setForeground(MUTED_TEXT);
        description.setFont(description.getFont().deriveFont(SwingTheme.scaleFont(11.5f)));
        description.setBorder(new EmptyBorder(SwingTheme.scale(5), 0, SwingTheme.scale(18), 0));
        description.setAlignmentX(0f);
        card.add(description);

        JPanel scaleRow = new JPanel(new BorderLayout(SwingTheme.scale(14), 0));
        scaleRow.setOpaque(false);
        scaleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(38)));
        scaleRow.setAlignmentX(0f);
        JLabel scaleLabel = new JLabel("GUI scale");
        scaleLabel.setForeground(TEXT);
        scaleLabel.setFont(scaleLabel.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(12.5f)));
        scaleLabel.setPreferredSize(new Dimension(SwingTheme.scale(150), SwingTheme.scale(34)));
        scaleRow.add(scaleLabel, BorderLayout.WEST);

        int minPercent = (int) Math.round(UiScale.MIN_FACTOR * 100);
        int maxPercent = (int) Math.round(UiScale.MAX_FACTOR * 100);
        int currentPercent = (int) Math.round(UiScale.factor() * 100);
        JSlider scaleSlider = new JSlider(minPercent, maxPercent, currentPercent);
        scaleSlider.setOpaque(false);
        scaleSlider.setFocusable(false);
        scaleSlider.setToolTipText(currentPercent + "%");
        markInteractive(scaleSlider);
        JLabel scaleValue = new JLabel(currentPercent + "%");
        scaleValue.setForeground(MUTED_TEXT);
        scaleValue.setFont(scaleValue.getFont().deriveFont(SwingTheme.scaleFont(12f)));
        scaleValue.setPreferredSize(new Dimension(SwingTheme.scale(46), SwingTheme.scale(30)));
        scaleSlider.addChangeListener(event -> {
            int percent = scaleSlider.getValue();
            scaleValue.setText(percent + "%");
            scaleSlider.setToolTipText(percent + "%");
            if (!scaleSlider.getValueIsAdjusting()) {
                UiScale.setFactor(percent / 100.0D);
                showStatus("GUI scale set to " + percent + "%", false);
                SwingUtilities.invokeLater(this::applyScaleLive);
            }
        });
        JPanel scaleEditor = new JPanel(new BorderLayout(SwingTheme.scale(10), 0));
        scaleEditor.setOpaque(false);
        scaleEditor.add(scaleSlider, BorderLayout.CENTER);
        scaleEditor.add(scaleValue, BorderLayout.EAST);
        scaleRow.add(scaleEditor, BorderLayout.CENTER);
        card.add(scaleRow);

        int contentHeight = card.getPreferredSize().height;
        card.setPreferredSize(new Dimension(SwingTheme.scale(10), contentHeight));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentHeight));
        return card;
    }

    private JComponent targetRingUnavailable() {
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(16, CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(SwingTheme.scale(22), SwingTheme.scale(24), SwingTheme.scale(22), SwingTheme.scale(24)));
        card.setAlignmentX(0f);

        JLabel title = new JLabel("Target Ring");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(16f)));
        title.setAlignmentX(0f);
        card.add(title);

        JLabel text = new JLabel("Connect the agent to configure the target ring.");
        text.setForeground(MUTED_TEXT);
        text.setFont(text.getFont().deriveFont(SwingTheme.scaleFont(11.5f)));
        text.setBorder(new EmptyBorder(SwingTheme.scale(5), 0, 0, 0));
        text.setAlignmentX(0f);
        card.add(text);

        int contentHeight = card.getPreferredSize().height;
        card.setPreferredSize(new Dimension(SwingTheme.scale(10), contentHeight));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentHeight));
        return card;
    }

    private JComponent targetRingCard(ModuleView ring) {
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(16, CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(SwingTheme.scale(22), SwingTheme.scale(24), SwingTheme.scale(22), SwingTheme.scale(24)));
        card.setAlignmentX(0f);

        JLabel title = new JLabel("Target Ring");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(16f)));
        title.setAlignmentX(0f);
        card.add(title);

        JLabel description = new JLabel("<html>Draws a ring around Silent Aura's current target. "
                + "The ring only appears while Silent Aura is enabled and locked onto an entity.</html>");
        description.setForeground(MUTED_TEXT);
        description.setFont(description.getFont().deriveFont(SwingTheme.scaleFont(11.5f)));
        description.setBorder(new EmptyBorder(SwingTheme.scale(5), SwingTheme.scale(0), SwingTheme.scale(18), SwingTheme.scale(0)));
        description.setAlignmentX(0f);
        card.add(description);

        JPanel enableRow = new JPanel(new BorderLayout(SwingTheme.scale(14), SwingTheme.scale(0)));
        enableRow.setOpaque(false);
        enableRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(42)));
        enableRow.setPreferredSize(new Dimension(SwingTheme.scale(10), SwingTheme.scale(42)));
        enableRow.setAlignmentX(0f);
        JLabel enableLabel = new JLabel("Enabled");
        enableLabel.setForeground(TEXT);
        enableLabel.setFont(enableLabel.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(12.5f)));
        enableLabel.setPreferredSize(new Dimension(SwingTheme.scale(185), SwingTheme.scale(34)));
        enableRow.add(enableLabel, BorderLayout.WEST);
        SwingTheme.ToggleSwitch enableToggle = new SwingTheme.ToggleSwitch(ring.enabled());
        enableToggle.setToolTipText(ring.enabled() ? "Disable target ring" : "Enable target ring");
        enableToggle.onChange(value -> {
            sendUpdate(ring.id(), value, null, Map.of());
            showStatus("Target Ring " + (value ? "enabled" : "disabled"), false);
        });
        markInteractive(enableToggle);
        JPanel toggleHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingTheme.scale(0), SwingTheme.scale(7)));
        toggleHolder.setOpaque(false);
        toggleHolder.add(enableToggle);
        enableRow.add(toggleHolder, BorderLayout.CENTER);
        card.add(enableRow);
        card.add(Box.createVerticalStrut(SwingTheme.scale(4)));

        addSettingRows(card, ring, ring.settings().stream().filter(SettingView::visible).toList());

        int contentHeight = card.getPreferredSize().height;
        card.setPreferredSize(new Dimension(SwingTheme.scale(10), contentHeight));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentHeight));
        return card;
    }

    private void rebuildFriendsPage() {
        int scrollY = currentListScrollY();
        listPanel.setVisible(false);
        listPanel.removeAll();
        listPanel.add(friendsCard());
        finishDedicatedPageRebuild(FRIENDS,
                friends.size() + (friends.size() == 1 ? " friend" : " friends"), scrollY);
    }

    private int currentListScrollY() {
        return listScroll == null ? 0 : listScroll.getViewport().getViewPosition().y;
    }

    private void resetListScroll() {
        restoreListScrollY(0);
    }

    private void restoreListScrollY(int requestedY) {
        if (listScroll == null) return;
        int maximumY = Math.max(0,
                listPanel.getPreferredSize().height - listScroll.getViewport().getExtentSize().height);
        listScroll.getViewport().setViewPosition(new Point(0, Math.min(Math.max(0, requestedY), maximumY)));
    }

    private JComponent friendsCard() {
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(16, CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(SwingTheme.scale(22), SwingTheme.scale(24), SwingTheme.scale(22), SwingTheme.scale(24)));
        card.setAlignmentX(0f);

        JLabel title = new JLabel("Friends");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(16f)));
        title.setAlignmentX(0f);
        card.add(title);

        JLabel description = new JLabel("<html>Friended players are never targeted by combat modules "
                + "(Silent Aura, Auto Anchor, Aim Assist, TriggerBot…) and are drawn in the ESP friend "
                + "color instead of the normal box color.</html>");
        description.setForeground(MUTED_TEXT);
        description.setFont(description.getFont().deriveFont(SwingTheme.scaleFont(11.5f)));
        description.setBorder(new EmptyBorder(SwingTheme.scale(5), 0, SwingTheme.scale(18), 0));
        description.setAlignmentX(0f);
        card.add(description);

        JPanel addRow = new JPanel(new BorderLayout(SwingTheme.scale(9), 0));
        addRow.setOpaque(false);
        addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(38)));
        addRow.setAlignmentX(0f);
        JTextField nameField = new JTextField();
        styleValueField(nameField);
        nameField.setHorizontalAlignment(SwingConstants.LEFT);
        markInteractive(nameField);
        SwingTheme.PillButton addButton = new SwingTheme.PillButton("Add", SwingTheme.ACCENT, SwingTheme.ACCENT_HOVER);
        addButton.setPreferredSize(new Dimension(SwingTheme.scale(76), SwingTheme.scale(34)));
        JPopupMenu suggestions = new JPopupMenu();
        suggestions.setFocusable(false);
        suggestions.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
        Runnable addAction = () -> {
            suggestions.setVisible(false);
            if (addFriend(nameField.getText())) {
                nameField.setText("");
                rebuildFriendsPage();
            }
        };
        nameField.addActionListener(event -> addAction.run());
        addButton.addActionListener(event -> addAction.run());
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateSuggestions(nameField, suggestions); }
            @Override public void removeUpdate(DocumentEvent event) { updateSuggestions(nameField, suggestions); }
            @Override public void changedUpdate(DocumentEvent event) { updateSuggestions(nameField, suggestions); }
        });
        addRow.add(nameField, BorderLayout.CENTER);
        addRow.add(addButton, BorderLayout.EAST);
        card.add(addRow);
        card.add(Box.createVerticalStrut(SwingTheme.scale(16)));

        if (friends.isEmpty()) {
            JLabel empty = new JLabel("No friends added yet.");
            empty.setForeground(MUTED_TEXT);
            empty.setFont(empty.getFont().deriveFont(SwingTheme.scaleFont(12f)));
            empty.setAlignmentX(0f);
            card.add(empty);
        } else {
            for (String friend : friends) {
                card.add(friendRow(friend));
                card.add(Box.createVerticalStrut(SwingTheme.scale(8)));
            }
        }

        int contentHeight = card.getPreferredSize().height;
        card.setPreferredSize(new Dimension(SwingTheme.scale(10), contentHeight));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentHeight));
        return card;
    }

    private JComponent friendRow(String friend) {
        JPanel row = new JPanel(new BorderLayout(SwingTheme.scale(12), 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(40)));
        row.setAlignmentX(0f);

        JPanel identity = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingTheme.scale(10), 0));
        identity.setOpaque(false);
        identity.add(new PlayerHead(friend, SwingTheme.scale(28), headCache));
        JLabel name = new JLabel(friend);
        name.setForeground(TEXT);
        name.setFont(name.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(13f)));
        identity.add(name);
        row.add(identity, BorderLayout.WEST);

        SwingTheme.PillButton remove = new SwingTheme.PillButton("Remove", DANGER, DANGER_HOVER);
        remove.setPreferredSize(new Dimension(SwingTheme.scale(88), SwingTheme.scale(32)));
        remove.addActionListener(event -> {
            removeFriend(friend);
            rebuildFriendsPage();
        });
        JPanel removeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, SwingTheme.scale(4)));
        removeHolder.setOpaque(false);
        removeHolder.add(remove);
        row.add(removeHolder, BorderLayout.EAST);
        return row;
    }

    /** Shows up to eight online-player suggestions once at least three characters are typed, like a
     * shell tab-completion. Selecting one fills the field so the user can review it before adding. */
    private void updateSuggestions(JTextField nameField, JPopupMenu suggestions) {
        String query = nameField.getText().strip().toLowerCase(Locale.ROOT);
        if (query.length() < 3) {
            suggestions.setVisible(false);
            return;
        }
        List<String> matches = onlinePlayers.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(query))
                .filter(name -> friends.stream().noneMatch(friend -> friend.equalsIgnoreCase(name)))
                .distinct()
                .limit(8)
                .toList();
        if (matches.isEmpty()) {
            suggestions.setVisible(false);
            return;
        }
        suggestions.removeAll();
        for (String name : matches) {
            JMenuItem item = new JMenuItem(name, new PlayerHeadIcon(name, SwingTheme.scale(18), headCache));
            item.setBackground(FIELD);
            item.setForeground(TEXT);
            item.setOpaque(true);
            item.setIconTextGap(SwingTheme.scale(8));
            item.addActionListener(event -> {
                suggestions.setVisible(false);
                nameField.setText(name);
                nameField.requestFocusInWindow();
            });
            suggestions.add(item);
        }
        suggestions.pack();
        suggestions.show(nameField, 0, nameField.getHeight());
        nameField.requestFocusInWindow();
    }

    private boolean addFriend(String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()) {
            showStatus("Enter a player name to add as a friend.", true);
            return false;
        }
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            showStatus("\"" + name + "\" is not a valid Minecraft username.", true);
            return false;
        }
        boolean exists = friends.stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
        if (exists) {
            showStatus(name + " is already a friend.", false);
            return false;
        }
        friends.add(name);
        persistAndPushFriends();
        showStatus("Added " + name + " to friends.", false);
        return true;
    }

    private void removeFriend(String name) {
        friends.removeIf(existing -> existing.equalsIgnoreCase(name));
        persistAndPushFriends();
        showStatus("Removed " + name + " from friends.", false);
    }

    private void persistAndPushFriends() {
        List<String> ordered = new ArrayList<>(friends);
        GuiPreferences.setFriends(ordered);
        if (!agentHub.sendFriends(ordered)) {
            showStatus("Saved; friends will sync when an agent connects.", false);
        }
    }

    private JComponent appearanceCard() {
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(16, CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(SwingTheme.scale(22), SwingTheme.scale(24), SwingTheme.scale(22), SwingTheme.scale(24)));
        card.setAlignmentX(0f);

        JLabel title = new JLabel("Interface color");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(16f)));
        title.setAlignmentX(0f);
        card.add(title);

        JLabel description = new JLabel("Choose the accent used by active modules, switches, favorites, and highlights.");
        description.setForeground(MUTED_TEXT);
        description.setFont(description.getFont().deriveFont(SwingTheme.scaleFont(11.5f)));
        description.setBorder(new EmptyBorder(SwingTheme.scale(5), SwingTheme.scale(0), SwingTheme.scale(18), SwingTheme.scale(0)));
        description.setAlignmentX(0f);
        card.add(description);

        JPanel colorRow = new JPanel(new BorderLayout(SwingTheme.scale(14), SwingTheme.scale(0)));
        colorRow.setOpaque(false);
        colorRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(38)));
        colorRow.setAlignmentX(0f);
        JLabel colorLabel = new JLabel("Accent color");
        colorLabel.setForeground(TEXT);
        colorLabel.setFont(colorLabel.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(12.5f)));
        colorLabel.setPreferredSize(new Dimension(SwingTheme.scale(150), SwingTheme.scale(34)));
        colorRow.add(colorLabel, BorderLayout.WEST);

        JTextField hex = new JTextField(GuiPreferences.formatColor(SwingTheme.ACCENT));
        styleValueField(hex);
        hex.setHorizontalAlignment(SwingConstants.LEFT);
        hex.setPreferredSize(new Dimension(SwingTheme.scale(110), SwingTheme.scale(34)));
        Runnable applyHex = () -> {
            try {
                applyAccent(GuiPreferences.parseColor(hex.getText()));
            } catch (IllegalArgumentException exception) {
                showStatus(exception.getMessage(), true);
                hex.setText(GuiPreferences.formatColor(SwingTheme.ACCENT));
            }
        };
        hex.addActionListener(event -> applyHex.run());
        SwingTheme.PillButton apply = new SwingTheme.PillButton("Apply", SwingTheme.ACCENT, SwingTheme.ACCENT_HOVER);
        apply.setPreferredSize(new Dimension(SwingTheme.scale(76), SwingTheme.scale(34)));
        apply.addActionListener(event -> applyHex.run());
        ColorPickerButton picker = new ColorPickerButton();
        picker.addActionListener(event -> {
            Color selected = JColorChooser.showDialog(
                    ControlPanelGui.this, "Choose Aurora interface color", SwingTheme.ACCENT);
            if (selected != null) applyAccent(selected);
        });
        JPanel editor = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingTheme.scale(9), SwingTheme.scale(0)));
        editor.setOpaque(false);
        editor.add(hex);
        editor.add(apply);
        editor.add(picker);
        colorRow.add(editor, BorderLayout.CENTER);
        card.add(colorRow);
        card.add(Box.createVerticalStrut(SwingTheme.scale(16)));

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingTheme.scale(10), SwingTheme.scale(0)));
        presets.setOpaque(false);
        presets.setAlignmentX(0f);
        presets.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(38)));
        JLabel presetsLabel = new JLabel("Presets");
        presetsLabel.setForeground(MUTED_TEXT);
        presetsLabel.setPreferredSize(new Dimension(SwingTheme.scale(140), SwingTheme.scale(30)));
        presets.add(presetsLabel);
        for (Color color : List.of(
                new Color(0x18, 0x66, 0xDF), new Color(0x7C, 0x5C, 0xFC),
                new Color(0x13, 0xA0, 0x78), new Color(0xD9, 0x46, 0x78), new Color(0xE2, 0x7A, 0x24))) {
            ColorSwatch swatch = new ColorSwatch(color, color.equals(SwingTheme.ACCENT));
            swatch.addActionListener(event -> applyAccent(color));
            presets.add(swatch);
        }
        card.add(presets);
        card.add(Box.createVerticalStrut(SwingTheme.scale(18)));

        JPanel rainbowRow = new JPanel(new BorderLayout(SwingTheme.scale(14), SwingTheme.scale(0)));
        rainbowRow.setOpaque(false);
        rainbowRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(38)));
        rainbowRow.setAlignmentX(0f);
        JLabel rainbowLabel = new JLabel("Rainbow modules");
        rainbowLabel.setForeground(TEXT);
        rainbowLabel.setFont(rainbowLabel.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(12.5f)));
        rainbowLabel.setToolTipText("Paints the module cards as a top-to-bottom rainbow gradient instead of the accent color.");
        rainbowLabel.setPreferredSize(new Dimension(SwingTheme.scale(150), SwingTheme.scale(34)));
        rainbowRow.add(rainbowLabel, BorderLayout.WEST);
        SwingTheme.ToggleSwitch rainbowToggle = new SwingTheme.ToggleSwitch(GuiPreferences.rainbowModules());
        rainbowToggle.setToolTipText("Paints the module cards as a top-to-bottom rainbow gradient instead of the accent color.");
        rainbowToggle.onChange(value -> {
            GuiPreferences.setRainbowModules(value);
            showStatus(value ? "Rainbow module colors enabled" : "Rainbow module colors disabled", false);
        });
        markInteractive(rainbowToggle);
        JPanel rainbowHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingTheme.scale(0), SwingTheme.scale(7)));
        rainbowHolder.setOpaque(false);
        rainbowHolder.add(rainbowToggle);
        rainbowRow.add(rainbowHolder, BorderLayout.CENTER);
        card.add(rainbowRow);
        card.add(Box.createVerticalStrut(SwingTheme.scale(18)));

        JButton reset = new JButton("Reset appearance");
        styleSmallButton(reset);
        reset.setAlignmentX(0f);
        reset.addActionListener(event -> {
            GuiPreferences.resetAppearance();
            applyAccent(GuiPreferences.DEFAULT_ACCENT);
        });
        card.add(reset);

        int contentHeight = card.getPreferredSize().height;
        card.setPreferredSize(new Dimension(SwingTheme.scale(10), contentHeight));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentHeight));
        return card;
    }

    private void applyAccent(Color color) {
        GuiPreferences.setAccentColor(color);
        SwingTheme.setAccent(color);
        showStatus("Interface color set to " + GuiPreferences.formatColor(color), false);
        rebuildNavigation();
        rebuildModuleList();
        repaint();
    }

    private boolean matchesCategory(ModuleView module, String category) {
        if (ALL.equals(category)) return true;
        if (FAVORITES.equals(category)) return favorites.contains(module.id());
        return category.equals(module.category());
    }

    private JComponent emptyState(String query) {
        JPanel empty = new JPanel();
        empty.setOpaque(false);
        empty.setBorder(new EmptyBorder(SwingTheme.scale(70), SwingTheme.scale(20), SwingTheme.scale(20), SwingTheme.scale(20)));
        JLabel text = new JLabel(query.isEmpty() ? "No modules in this category." : "No modules match your search.");
        text.setForeground(MUTED_TEXT);
        text.setFont(text.getFont().deriveFont(SwingTheme.scaleFont(13f)));
        empty.add(text);
        return empty;
    }

    /** Maps a vertical position in [0, 1] down the module list, plus an animation {@code phase}, to a
     * rainbow color: enabled cards get a bright, saturated hue, disabled cards a dark muted one so the
     * flow stays visible but the enabled modules still stand out. Each card is a flat fill (not a
     * gradient) so the list paints as cheaply as a normal module list and scrolling stays smooth; the
     * top-to-bottom rainbow reads from the per-card hue steps. */
    private static Color rainbowColor(float position, float phase, boolean enabled) {
        float hue = position * 0.82f + phase;
        return enabled ? Color.getHSBColor(hue, 0.60f, 0.92f) : Color.getHSBColor(hue, 0.15f, 0.28f);
    }

    private JComponent moduleCard(ModuleView module, int index, int total) {
        boolean expanded = expandedModules.contains(module.id());
        boolean rainbow = GuiPreferences.rainbowModules();
        final Color cardColor;
        final Color hoverColor;
        final float rainbowPos = total <= 1 ? 0f : index / (float) (total - 1);
        if (rainbow) {
            cardColor = rainbowColor(rainbowPos, rainbowPhase, module.enabled());
            hoverColor = SwingTheme.lighten(cardColor, 0.12D);
        } else {
            cardColor = module.enabled() ? SwingTheme.ACCENT : CARD;
            hoverColor = module.enabled() ? SwingTheme.ACCENT_HOVER : CARD_HOVER;
        }
        boolean colored = rainbow || module.enabled();
        Color activeText = SwingTheme.contrastText(cardColor);
        Color activeMutedText = SwingTheme.contrastMuted(cardColor);
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(16, cardColor);
        final RainbowCard rainbowCard = rainbow ? new RainbowCard(card, rainbowPos, module.enabled()) : null;
        if (rainbowCard != null) rainbowCards.add(rainbowCard);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(SwingTheme.scale(0), SwingTheme.scale(0), SwingTheme.scale(expanded ? 14 : 0), SwingTheme.scale(0)));
        card.setAlignmentX(0f);

        JPanel summary = new JPanel(new BorderLayout(SwingTheme.scale(14), SwingTheme.scale(0)));
        summary.setOpaque(false);
        summary.setBorder(new EmptyBorder(SwingTheme.scale(15), SwingTheme.scale(16), SwingTheme.scale(15), SwingTheme.scale(16)));
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(76)));
        summary.setPreferredSize(new Dimension(SwingTheme.scale(10), SwingTheme.scale(76)));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton favorite = new FavoriteButton(favorites.contains(module.id()), colored, activeText);
        favorite.setToolTipText("Toggle favorite");
        favorite.addActionListener(event -> toggleFavorite(module.id()));
        summary.add(favorite, BorderLayout.WEST);

        JPanel identity = new JPanel();
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        identity.setOpaque(false);
        JPanel nameRow = new JPanel();
        nameRow.setLayout(new BoxLayout(nameRow, BoxLayout.X_AXIS));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel(module.displayName());
        name.setForeground(colored ? activeText : TEXT);
        name.setFont(name.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(15f)));
        nameRow.add(name);
        if ("reach".equals(module.id())) {
            nameRow.add(Box.createRigidArea(new Dimension(SwingTheme.scale(6), 0)));
            nameRow.add(new WarningBadge("Unsafe"));
        }
        identity.add(nameRow);
        String detail = moduleSummary(module);
        JLabel details = new JLabel(detail);
        details.setForeground(colored ? activeMutedText : MUTED_TEXT);
        details.setFont(details.getFont().deriveFont(SwingTheme.scaleFont(11.5f)));
        details.setBorder(new EmptyBorder(SwingTheme.scale(4), SwingTheme.scale(0), SwingTheme.scale(0), SwingTheme.scale(0)));
        identity.add(details);
        summary.add(identity, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingTheme.scale(10), SwingTheme.scale(7)));
        controls.setOpaque(false);
        JButton bind = keybindButton(module);
        controls.add(bind);
        SwingTheme.ToggleSwitch toggle = new SwingTheme.ToggleSwitch(module.enabled());
        if (module.enabled()) toggle.setCheckedTrackColor(CARD);
        toggle.setToolTipText(module.enabled() ? "Disable module" : "Enable module");
        toggle.onChange(value -> sendUpdate(module.id(), value, null, Map.of()));
        markInteractive(toggle);
        controls.add(toggle);
        JButton expand = iconButton(expanded ? "⌃" : "⌄");
        if (colored) expand.setForeground(activeText);
        expand.setToolTipText(expanded ? "Hide settings" : "Show settings");
        expand.addActionListener(event -> toggleExpanded(module.id()));
        controls.add(expand);
        summary.add(controls, BorderLayout.EAST);
        summary.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) {
                if (rainbowCard != null) rainbowCard.hovered = true; else card.setBackgroundColor(hoverColor);
            }
            @Override public void mouseExited(MouseEvent event) {
                if (rainbowCard != null) rainbowCard.hovered = false; else card.setBackgroundColor(cardColor);
            }
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getSource() == summary) toggleExpanded(module.id());
            }
        });
        card.add(summary);

        if (expanded) {
            card.add(moduleConfiguration(module));
        }
        int contentHeight = card.getPreferredSize().height;
        card.setPreferredSize(new Dimension(SwingTheme.scale(10), contentHeight));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentHeight));
        return card;
    }

    private JComponent moduleConfiguration(ModuleView module) {
        SwingTheme.RoundedPanel body = new SwingTheme.RoundedPanel(12, CARD);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(SwingTheme.scale(12), SwingTheme.scale(40), SwingTheme.scale(6), SwingTheme.scale(18)));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        if (!module.description().isBlank()) {
            JLabel description = new JLabel("<html>" + escapeHtml(module.description()) + "</html>");
            description.setForeground(MUTED_TEXT);
            description.setFont(description.getFont().deriveFont(SwingTheme.scaleFont(11.5f)));
            description.setBorder(new EmptyBorder(SwingTheme.scale(0), SwingTheme.scale(0), SwingTheme.scale(9), SwingTheme.scale(0)));
            description.setAlignmentX(0f);
            body.add(description);
        }
        List<SettingView> visibleSettings = module.settings().stream().filter(SettingView::visible).toList();
        if (visibleSettings.isEmpty()) {
            JLabel empty = new JLabel("This module has no additional settings.");
            empty.setForeground(MUTED_TEXT);
            empty.setBorder(new EmptyBorder(SwingTheme.scale(5), SwingTheme.scale(0), SwingTheme.scale(5), SwingTheme.scale(0)));
            body.add(empty);
        } else {
            addSettingRows(body, module, visibleSettings);
        }
        JPanel inset = new JPanel(new BorderLayout());
        inset.setOpaque(false);
        inset.setBorder(new EmptyBorder(SwingTheme.scale(0), SwingTheme.scale(14), SwingTheme.scale(0), SwingTheme.scale(14)));
        inset.setAlignmentX(Component.LEFT_ALIGNMENT);
        inset.add(body, BorderLayout.CENTER);
        int height = inset.getPreferredSize().height;
        inset.setPreferredSize(new Dimension(SwingTheme.scale(10), height));
        inset.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return inset;
    }

    /**
     * Renders {@code visibleSettings} into {@code container}, one row per setting, except that a
     * "-min"/"-max" id pair (e.g. {@code reaction-min}/{@code reaction-max}) is merged into a single
     * two-thumb range row instead of two separate sliders.
     */
    private void addSettingRows(JComponent container, ModuleView module, List<SettingView> visibleSettings) {
        Map<String, SettingView> byId = new LinkedHashMap<>();
        for (SettingView setting : visibleSettings) {
            byId.put(setting.id(), setting);
        }
        Set<String> consumed = new LinkedHashSet<>();
        for (SettingView setting : visibleSettings) {
            if (consumed.contains(setting.id())) continue;
            SettingPair pair = "number".equals(setting.type()) ? findMinMaxPair(setting, byId) : null;
            JComponent row;
            if (pair != null) {
                consumed.add(pair.min().id());
                consumed.add(pair.max().id());
                row = rangeSettingRow(module, pair.min(), pair.max());
            } else {
                row = settingRow(module, setting);
            }
            row.setAlignmentX(0f);
            container.add(row);
            container.add(Box.createVerticalStrut(SwingTheme.scale(7)));
        }
    }

    private record SettingPair(SettingView min, SettingView max) {
    }

    /** Finds {@code setting}'s min/max counterpart by swapping the "min"/"max" token in its id (e.g.
     * {@code reaction-min} &lt;-&gt; {@code reaction-max}), or {@code null} if there is none. */
    private static SettingPair findMinMaxPair(SettingView setting, Map<String, SettingView> byId) {
        String[] tokens = setting.id().split("-");
        for (int i = 0; i < tokens.length; i++) {
            boolean isMin = tokens[i].equals("min");
            if (!isMin && !tokens[i].equals("max")) continue;
            String[] swapped = tokens.clone();
            swapped[i] = isMin ? "max" : "min";
            SettingView counterpart = byId.get(String.join("-", swapped));
            if (counterpart != null && "number".equals(counterpart.type())) {
                return isMin ? new SettingPair(setting, counterpart) : new SettingPair(counterpart, setting);
            }
        }
        return null;
    }

    private static String combinedLabel(String minDisplayName) {
        String stripped = minDisplayName.replaceFirst("(?i)\\bMin\\b\\s*", "").strip();
        return stripped.isEmpty() ? minDisplayName : stripped;
    }

    private JComponent rangeSettingRow(ModuleView module, SettingView minSetting, SettingView maxSetting) {
        JPanel row = new JPanel(new BorderLayout(SwingTheme.scale(14), SwingTheme.scale(0)));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(42)));
        row.setPreferredSize(new Dimension(SwingTheme.scale(10), SwingTheme.scale(42)));
        JLabel label = new JLabel(combinedLabel(minSetting.displayName()));
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(SwingTheme.scaleFont(12.5f)));
        JPanel labelGroup = new JPanel();
        labelGroup.setLayout(new BoxLayout(labelGroup, BoxLayout.X_AXIS));
        labelGroup.setOpaque(false);
        labelGroup.setPreferredSize(new Dimension(SwingTheme.scale(185), SwingTheme.scale(34)));
        labelGroup.add(label);
        labelGroup.add(Box.createHorizontalStrut(SwingTheme.scale(7)));
        String description = minSetting.description().isBlank() ? maxSetting.description() : minSetting.description();
        labelGroup.add(new HelpButton(description, MUTED_TEXT));
        labelGroup.add(Box.createHorizontalGlue());
        row.add(labelGroup, BorderLayout.WEST);
        JButton reset = resetButton();
        row.add(rangeEditor(module.id(), minSetting, maxSetting, reset), BorderLayout.CENTER);
        row.add(reset, BorderLayout.EAST);
        return row;
    }

    private JComponent settingRow(ModuleView module, SettingView setting) {
        JPanel row = new JPanel(new BorderLayout(SwingTheme.scale(14), SwingTheme.scale(0)));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(42)));
        row.setPreferredSize(new Dimension(SwingTheme.scale(10), SwingTheme.scale(42)));
        JLabel label = new JLabel(setting.displayName());
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(SwingTheme.scaleFont(12.5f)));
        JPanel labelGroup = new JPanel();
        labelGroup.setLayout(new BoxLayout(labelGroup, BoxLayout.X_AXIS));
        labelGroup.setOpaque(false);
        labelGroup.setPreferredSize(new Dimension(SwingTheme.scale(185), SwingTheme.scale(34)));
        labelGroup.add(label);
        labelGroup.add(Box.createHorizontalStrut(SwingTheme.scale(7)));
        labelGroup.add(new HelpButton(setting.description(), MUTED_TEXT));
        labelGroup.add(Box.createHorizontalGlue());
        row.add(labelGroup, BorderLayout.WEST);

        JButton reset = resetButton();
        if ("number".equals(setting.type())) {
            row.add(numberEditor(module.id(), setting, reset), BorderLayout.CENTER);
        } else if ("color".equals(setting.type())) {
            JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingTheme.scale(0), SwingTheme.scale(4)));
            holder.setOpaque(false);
            holder.add(settingColorButton(module.id(), setting, reset));
            row.add(holder, BorderLayout.CENTER);
        } else if ("boolean".equals(setting.type())) {
            SwingTheme.ToggleSwitch toggle = new SwingTheme.ToggleSwitch(setting.value() >= 0.5D);
            toggle.onChange(value -> sendUpdate(module.id(), null, null,
                    Map.of(setting.id(), value ? 1.0D : 0.0D)));
            markInteractive(toggle);
            reset.addActionListener(event -> {
                boolean defaultChecked = setting.defaultValue() >= 0.5D;
                toggle.setChecked(defaultChecked);
                sendUpdate(module.id(), null, null, Map.of(setting.id(), defaultChecked ? 1.0D : 0.0D));
                showStatus(setting.displayName() + " reset to default", false);
            });
            JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingTheme.scale(0), SwingTheme.scale(7)));
            holder.setOpaque(false);
            holder.add(toggle);
            row.add(holder, BorderLayout.CENTER);
        } else {
            JComboBox<String> options = new JComboBox<>(setting.options().toArray(String[]::new));
            styleComboBox(options);
            options.setSelectedIndex(Math.max(0, Math.min(setting.options().size() - 1,
                    (int) Math.round(setting.value()))));
            options.addActionListener(event -> sendUpdate(module.id(), null, null,
                    Map.of(setting.id(), (double) options.getSelectedIndex())));
            reset.addActionListener(event -> options.setSelectedIndex(Math.max(0, Math.min(setting.options().size() - 1,
                    (int) Math.round(setting.defaultValue())))));
            row.add(options, BorderLayout.CENTER);
        }
        row.add(reset, BorderLayout.EAST);
        return row;
    }

    private JComponent numberEditor(String moduleId, SettingView setting, JButton reset) {
        JPanel editor = new JPanel(new BorderLayout(SwingTheme.scale(10), SwingTheme.scale(0)));
        editor.setOpaque(false);
        int scale = sliderScale(setting);
        int minimum = safeScaled(setting.min(), scale);
        int maximum = safeScaled(setting.max(), scale);
        int current = Math.max(minimum, Math.min(maximum, safeScaled(setting.value(), scale)));
        JSlider slider = new SwingTheme.BallSlider(minimum, maximum, current);
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setToolTipText(formatValue(setting.value(), setting.step()));
        markInteractive(slider);

        JTextField value = new JTextField(formatValue(setting.value(), setting.step()));
        styleValueField(value);
        value.setPreferredSize(new Dimension(SwingTheme.scale(82), SwingTheme.scale(32)));
        slider.addChangeListener(event -> {
            double changed = snap(slider.getValue() / (double) scale, setting);
            value.setText(formatValue(changed, setting.step()));
            slider.setToolTipText(value.getText());
            if (!slider.getValueIsAdjusting()) {
                sendUpdate(moduleId, null, null, Map.of(setting.id(), changed));
            }
        });
        Runnable commit = () -> commitTypedValue(moduleId, setting, slider, scale, value);
        value.addActionListener(event -> commit.run());
        value.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) { commit.run(); }
        });
        reset.addActionListener(event -> slider.setValue(safeScaled(setting.defaultValue(), scale)));
        editor.add(slider, BorderLayout.CENTER);
        editor.add(value, BorderLayout.EAST);
        return editor;
    }

    private JComponent rangeEditor(String moduleId, SettingView minSetting, SettingView maxSetting, JButton reset) {
        JPanel editor = new JPanel(new BorderLayout(SwingTheme.scale(8), SwingTheme.scale(0)));
        editor.setOpaque(false);
        int scale = Math.max(sliderScale(minSetting), sliderScale(maxSetting));
        // The two settings can carry different bounds (e.g. reaction-min tops out at 250 while
        // reaction-max goes to 350); the shared slider track needs to span the union of both so
        // neither thumb's legitimate range is clipped.
        int minimum = safeScaled(Math.min(minSetting.min(), maxSetting.min()), scale);
        int maximum = safeScaled(Math.max(minSetting.max(), maxSetting.max()), scale);
        int low = Math.max(minimum, Math.min(maximum, safeScaled(minSetting.value(), scale)));
        int high = Math.max(minimum, Math.min(maximum, safeScaled(maxSetting.value(), scale)));

        SwingTheme.RangeSlider slider = new SwingTheme.RangeSlider(minimum, maximum, low, high);
        slider.setToolTipText(formatValue(minSetting.value(), minSetting.step()) + " - "
                + formatValue(maxSetting.value(), maxSetting.step()));
        markInteractive(slider);

        JTextField lowField = new JTextField(formatValue(minSetting.value(), minSetting.step()));
        styleValueField(lowField);
        lowField.setPreferredSize(new Dimension(SwingTheme.scale(62), SwingTheme.scale(32)));
        JTextField highField = new JTextField(formatValue(maxSetting.value(), maxSetting.step()));
        styleValueField(highField);
        highField.setPreferredSize(new Dimension(SwingTheme.scale(62), SwingTheme.scale(32)));

        slider.onChange(adjusting -> {
            double newLow = snap(slider.lowValue() / (double) scale, minSetting);
            double newHigh = snap(slider.highValue() / (double) scale, maxSetting);
            lowField.setText(formatValue(newLow, minSetting.step()));
            highField.setText(formatValue(newHigh, maxSetting.step()));
            slider.setToolTipText(lowField.getText() + " - " + highField.getText());
            if (!adjusting) {
                sendUpdate(moduleId, null, null, Map.of(minSetting.id(), newLow, maxSetting.id(), newHigh));
            }
        });

        Runnable commitLow = () -> commitTypedRangeValue(moduleId, minSetting, maxSetting, slider, scale, lowField, true);
        Runnable commitHigh = () -> commitTypedRangeValue(moduleId, minSetting, maxSetting, slider, scale, highField, false);
        lowField.addActionListener(event -> commitLow.run());
        lowField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) { commitLow.run(); }
        });
        highField.addActionListener(event -> commitHigh.run());
        highField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) { commitHigh.run(); }
        });
        reset.addActionListener(event -> {
            slider.setLowValue(safeScaled(minSetting.defaultValue(), scale));
            slider.setHighValue(safeScaled(maxSetting.defaultValue(), scale));
            double newLow = snap(slider.lowValue() / (double) scale, minSetting);
            double newHigh = snap(slider.highValue() / (double) scale, maxSetting);
            lowField.setText(formatValue(newLow, minSetting.step()));
            highField.setText(formatValue(newHigh, maxSetting.step()));
            slider.setToolTipText(lowField.getText() + " - " + highField.getText());
            sendUpdate(moduleId, null, null, Map.of(minSetting.id(), newLow, maxSetting.id(), newHigh));
            showStatus(combinedLabel(minSetting.displayName()) + " reset to default", false);
        });

        editor.add(lowField, BorderLayout.WEST);
        editor.add(slider, BorderLayout.CENTER);
        editor.add(highField, BorderLayout.EAST);
        return editor;
    }

    private JComponent settingColorButton(String moduleId, SettingView setting, JButton reset) {
        Color current = new Color((int) Math.round(setting.value()) & 0xFFFFFF);
        SettingColorButton button = new SettingColorButton(current);
        button.addActionListener(event -> {
            Color chosen = JColorChooser.showDialog(this, "Choose " + setting.displayName(), button.color());
            if (chosen == null) {
                return;
            }
            button.setColor(chosen);
            sendUpdate(moduleId, null, null, Map.of(setting.id(), (double) (chosen.getRGB() & 0xFFFFFF)));
            showStatus(setting.displayName() + " set to " + GuiPreferences.formatColor(chosen), false);
        });
        reset.addActionListener(event -> {
            Color defaultColor = new Color((int) Math.round(setting.defaultValue()) & 0xFFFFFF);
            button.setColor(defaultColor);
            sendUpdate(moduleId, null, null, Map.of(setting.id(), (double) (defaultColor.getRGB() & 0xFFFFFF)));
            showStatus(setting.displayName() + " reset to default", false);
        });
        markInteractive(button);
        return button;
    }

    private void commitTypedValue(String moduleId, SettingView setting, JSlider slider, int scale, JTextField field) {
        try {
            double parsed = Double.parseDouble(field.getText().strip().replace(',', '.'));
            if (!Double.isFinite(parsed)) throw new NumberFormatException();
            double value = snap(parsed, setting);
            slider.setValue(safeScaled(value, scale));
            field.setText(formatValue(value, setting.step()));
            sendUpdate(moduleId, null, null, Map.of(setting.id(), value));
            showStatus(setting.displayName() + " set to " + field.getText(), false);
        } catch (NumberFormatException exception) {
            field.setText(formatValue(setting.value(), setting.step()));
            showStatus("Enter a number between " + formatValue(setting.min(), setting.step()) + " and "
                    + formatValue(setting.max(), setting.step()), true);
        }
    }

    private void commitTypedRangeValue(String moduleId, SettingView minSetting, SettingView maxSetting,
                                        SwingTheme.RangeSlider slider, int scale, JTextField field, boolean editingLow) {
        SettingView setting = editingLow ? minSetting : maxSetting;
        try {
            double parsed = Double.parseDouble(field.getText().strip().replace(',', '.'));
            if (!Double.isFinite(parsed)) throw new NumberFormatException();
            double value = snap(parsed, setting);
            if (editingLow) {
                slider.setLowValue(safeScaled(value, scale));
            } else {
                slider.setHighValue(safeScaled(value, scale));
            }
            field.setText(formatValue(value, setting.step()));
            sendUpdate(moduleId, null, null, Map.of(
                    minSetting.id(), snap(slider.lowValue() / (double) scale, minSetting),
                    maxSetting.id(), snap(slider.highValue() / (double) scale, maxSetting)));
            showStatus(setting.displayName() + " set to " + field.getText(), false);
        } catch (NumberFormatException exception) {
            field.setText(formatValue(setting.value(), setting.step()));
            showStatus("Enter a number between " + formatValue(setting.min(), setting.step()) + " and "
                    + formatValue(setting.max(), setting.step()), true);
        }
    }

    private JButton keybindButton(ModuleView module) {
        JButton button = new JButton("Key: " + KeybindCodec.displayName(module.keybind()));
        styleSmallButton(button);
        button.setPreferredSize(new Dimension(SwingTheme.scale(112), SwingTheme.scale(30)));
        button.setToolTipText("Click, then press a key. Click again while listening to clear the bind.");
        button.addActionListener(event -> {
            if (module.id().equals(bindingModuleId)) {
                // A second click while listening clears the keybind — no keyboard needed.
                bindingModuleId = null;
                button.setText("Key: " + KeybindCodec.displayName(KeybindCodec.UNBOUND));
                sendUpdate(module.id(), null, KeybindCodec.UNBOUND, Map.of());
                showStatus(module.displayName() + " keybind cleared", false);
                return;
            }
            bindingModuleId = module.id();
            button.setText("Press a key…");
            button.requestFocusInWindow();
        });
        button.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (!module.id().equals(bindingModuleId)) return;
                event.consume();
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    bindingModuleId = null;
                    button.setText("Key: " + KeybindCodec.displayName(module.keybind()));
                    return;
                }
                int keybind = KeybindCodec.fromKeyEvent(event);
                if (keybind == KeybindCodec.UNBOUND) {
                    showStatus("That key is not supported as a Minecraft keybind.", true);
                    return;
                }
                bindingModuleId = null;
                button.setText("Key: " + KeybindCodec.displayName(keybind));
                sendUpdate(module.id(), null, keybind, Map.of());
                showStatus(module.displayName() + " keybind: " + KeybindCodec.displayName(keybind), false);
            }
        });
        return button;
    }

    private void sendUpdate(String moduleId, Boolean enabled, Integer keybind, Map<String, Double> settings) {
        if (!agentHub.sendModuleUpdate(moduleId, enabled, keybind, new LinkedHashMap<>(settings))) {
            showStatus("No agent is connected; the change was not applied.", true);
        }
    }

    private void requestUninject() {
        if (uninjectRequested) return;
        uninjectRequested = true;
        uninjectButton.setEnabled(false);
        if (!agentHub.sendUninject()) {
            uninjectRequested = false;
            uninjectButton.setEnabled(true);
            showStatus("No agent is connected.", true);
        } else {
            showStatus("Uninjecting Aurora…", false);
        }
    }

    private void toggleFavorite(String id) {
        if (!favorites.remove(id)) favorites.add(id);
        saveFavorites();
        rebuildModuleList();
    }

    private void toggleExpanded(String id) {
        if (!expandedModules.remove(id)) expandedModules.add(id);
        rebuildModuleList();
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setForeground(error ? DANGER : MUTED_TEXT);
        statusLabel.setText(message);
    }

    private void markInteractive(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { pointerInteraction = true; }
            @Override public void mouseReleased(MouseEvent event) { pointerInteraction = false; }
            @Override public void mouseExited(MouseEvent event) { pointerInteraction = false; }
        });
    }

    /** A sidebar category tab painted as a clean rounded pill, echoing the module cards: the
     * selected tab fills with the accent, an unselected tab shows a soft rounded hover fill. */
    private static final class NavButton extends JButton {
        private final boolean selected;
        private boolean hovered;

        private NavButton(String text, boolean selected) {
            super(text);
            this.selected = selected;
            setHorizontalAlignment(SwingConstants.LEFT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(38)));
            setPreferredSize(new Dimension(SwingTheme.scale(150), SwingTheme.scale(38)));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(selected ? SwingTheme.contrastText(SwingTheme.ACCENT) : MUTED_TEXT);
            setFont(getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN, SwingTheme.scaleFont(13f)));
            setBorder(new EmptyBorder(0, SwingTheme.scale(14), 0, SwingTheme.scale(12)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    if (!selected) setForeground(TEXT);
                    repaint();
                }
                @Override public void mouseExited(MouseEvent event) {
                    hovered = false;
                    if (!selected) setForeground(MUTED_TEXT);
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = SwingTheme.scale(11);
            if (selected) {
                g2.setColor(SwingTheme.ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            } else if (hovered) {
                g2.setColor(CARD_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            }
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static JButton iconButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(TEXT);
        button.setFont(button.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(16f)));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(SwingTheme.scale(32), SwingTheme.scale(32)));
        return button;
    }

    /** A small "reset this setting to its default value" icon button, placed on the right edge of
     * every setting row. Its arrow is hand-painted (see {@link ResetButton}) rather than drawn from
     * a font glyph, since not every platform font ships a rotation-arrow character and falls back
     * to tofu/dots when it's missing. */
    private static JButton resetButton() {
        ResetButton button = new ResetButton();
        button.setToolTipText("Reset to default");
        button.getAccessibleContext().setAccessibleName("Reset to default");
        return button;
    }

    /** Paints the favorite icon directly so it is independent of platform font glyph support. */
    private static final class FavoriteButton extends JButton {
        private final boolean favorite;
        private final boolean colored;
        private final Color activeColor;

        private FavoriteButton(boolean favorite, boolean colored, Color activeColor) {
            this.favorite = favorite;
            this.colored = colored;
            this.activeColor = activeColor;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(SwingTheme.scale(32), SwingTheme.scale(32)));
            setToolTipText(favorite ? "Remove from favorites" : "Add to favorites");
            getAccessibleContext().setAccessibleName(getToolTipText());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Path2D star = star(getWidth() / 2.0D, getHeight() / 2.0D,
                    9.0D * UiScale.factor(), 4.2D * UiScale.factor());
            Color color = colored ? activeColor : favorite ? ACCENT : MUTED_TEXT;
            g2.setColor(color);
            if (favorite) {
                g2.fill(star);
            } else {
                g2.setStroke(new BasicStroke(SwingTheme.scaleFont(1.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(star);
            }
            g2.dispose();
        }

        private static Path2D star(double centerX, double centerY, double outerRadius, double innerRadius) {
            Path2D path = new Path2D.Double();
            for (int point = 0; point < 10; point++) {
                double radius = point % 2 == 0 ? outerRadius : innerRadius;
                double angle = -Math.PI / 2.0D + point * Math.PI / 5.0D;
                double x = centerX + Math.cos(angle) * radius;
                double y = centerY + Math.sin(angle) * radius;
                if (point == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.closePath();
            return path;
        }
    }

    /** Paints a counter-clockwise reset arrow directly so it renders identically across platforms,
     * instead of depending on a font glyph (see {@link #resetButton()}). */
    private static final class ResetButton extends JButton {
        private ResetButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(SwingTheme.scale(24), SwingTheme.scale(24)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double centerX = getWidth() / 2.0D;
            double centerY = getHeight() / 2.0D;
            double radius = 6.0D * UiScale.factor();
            g2.setColor(getModel().isRollover() ? ACCENT : MUTED_TEXT);
            g2.setStroke(new BasicStroke(SwingTheme.scaleFont(1.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Arc2D arc = new Arc2D.Double(centerX - radius, centerY - radius, radius * 2.0D, radius * 2.0D,
                    40.0D, 280.0D, Arc2D.OPEN);
            g2.draw(arc);
            // Arrowhead at the arc's open (start) end, pointing along the direction of travel.
            double angle = Math.toRadians(40.0D);
            double tipX = centerX + Math.cos(angle) * radius;
            double tipY = centerY - Math.sin(angle) * radius;
            double wingLength = radius * 0.75D;
            double wingAngle1 = angle + Math.toRadians(150.0D);
            double wingAngle2 = angle - Math.toRadians(150.0D);
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(tipX + Math.cos(wingAngle1) * wingLength, tipY - Math.sin(wingAngle1) * wingLength);
            arrow.lineTo(tipX, tipY);
            arrow.lineTo(tipX + Math.cos(wingAngle2) * wingLength, tipY - Math.sin(wingAngle2) * wingLength);
            g2.draw(arrow);
            g2.dispose();
        }
    }

    private static final class ColorSwatch extends JButton {
        private final Color color;
        private final boolean selected;

        private ColorSwatch(Color color, boolean selected) {
            this.color = color;
            this.selected = selected;
            setPreferredSize(new Dimension(SwingTheme.scale(30), SwingTheme.scale(30)));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(GuiPreferences.formatColor(color));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                g2.setColor(SwingTheme.contrastText(color));
                g2.fillOval(SwingTheme.scale(2), SwingTheme.scale(2),
                        getWidth() - SwingTheme.scale(4), getHeight() - SwingTheme.scale(4));
            }
            g2.setColor(color);
            int inset = SwingTheme.scale(selected ? 5 : 3);
            g2.fillOval(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2);
            g2.dispose();
        }
    }

    /** Per-setting swatch button that opens Swing's platform color chooser. */
    private static final class SettingColorButton extends JButton {
        private Color color;

        private SettingColorButton(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(SwingTheme.scale(60), SwingTheme.scale(30)));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createLineBorder(CARD_BORDER));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            updateTooltip();
        }

        Color color() {
            return color;
        }

        void setColor(Color color) {
            this.color = color;
            updateTooltip();
            repaint();
        }

        private void updateTooltip() {
            setToolTipText("Open color picker (" + GuiPreferences.formatColor(color) + ")");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(3, 3, getWidth() - 6, getHeight() - 6, 8, 8);
            g2.dispose();
        }
    }

    /** Painted eyedropper button that opens Swing's platform color chooser. */
    private static final class ColorPickerButton extends JButton {
        private ColorPickerButton() {
            setPreferredSize(new Dimension(SwingTheme.scale(34), SwingTheme.scale(34)));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createLineBorder(CARD_BORDER));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Open color picker");
            getAccessibleContext().setAccessibleName("Open color picker");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Coordinates below are hand-tuned for a 34x34 canvas; scale the transform once instead
            // of every literal so the icon keeps its proportions at any GUI scale.
            float factor = (float) UiScale.factor();
            g2.scale(factor, factor);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(TEXT);
            g2.drawLine(11, 23, 22, 12);
            g2.drawLine(16, 8, 26, 18);
            g2.drawLine(19, 9, 24, 14);
            g2.drawLine(10, 24, 9, 27);
            g2.drawLine(9, 27, 12, 26);
            g2.setColor(SwingTheme.ACCENT);
            g2.fillOval(7, 25, 5, 5);
            g2.dispose();
        }
    }

    /** Renders a player's Minecraft head avatar, fetched lazily from a public avatar service and
     * cached, with a rounded initial-letter placeholder shown until (or if) the image loads. */
    private static final class PlayerHead extends JComponent {
        private final String name;
        private final int size;
        private final PlayerHeadCache cache;

        private PlayerHead(String name, int size, PlayerHeadCache cache) {
            this.name = name;
            this.size = size;
            this.cache = cache;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
            setToolTipText(name);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            BufferedImage image = cache.cached(name);
            if (image != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(image, 0, 0, size, size, null);
            } else {
                cache.request(name, this::repaint);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SwingTheme.lighten(CARD, 0.10D));
                g2.fill(new RoundRectangle2D.Float(0, 0, size, size, size / 4f, size / 4f));
                g2.setColor(MUTED_TEXT);
                g2.setFont(getFont().deriveFont(Font.BOLD, size * 0.5f));
                String initial = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
                int textWidth = g2.getFontMetrics().stringWidth(initial);
                int ascent = g2.getFontMetrics().getAscent();
                int descent = g2.getFontMetrics().getDescent();
                g2.drawString(initial, (size - textWidth) / 2f, (size + ascent - descent) / 2f);
            }
            g2.dispose();
        }
    }

    /** {@link javax.swing.Icon} form of a player head, for use in autocomplete menu items. Repaints
     * its host component once the image finishes loading. */
    private static final class PlayerHeadIcon implements javax.swing.Icon {
        private final String name;
        private final int size;
        private final PlayerHeadCache cache;

        private PlayerHeadIcon(String name, int size, PlayerHeadCache cache) {
            this.name = name;
            this.size = size;
            this.cache = cache;
        }

        @Override public int getIconWidth() { return size; }

        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            BufferedImage image = cache.cached(name);
            if (image != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(image, x, y, size, size, null);
            } else {
                cache.request(name, c::repaint);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SwingTheme.lighten(CARD, 0.10D));
                g2.fill(new RoundRectangle2D.Float(x, y, size, size, size / 4f, size / 4f));
            }
            g2.dispose();
        }
    }

    /** Loads and caches player head images off the Swing thread. Failures (offline, blocked) are
     * silent — the placeholder simply remains. */
    private static final class PlayerHeadCache {
        private final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();
        private final Set<String> pending = ConcurrentHashMap.newKeySet();

        BufferedImage cached(String name) {
            return cache.get(key(name));
        }

        void request(String name, Runnable onLoaded) {
            String key = key(name);
            if (cache.containsKey(key) || !pending.add(key)) {
                return;
            }
            Thread.ofVirtual().name("aurora-head-" + key).start(() -> {
                BufferedImage image = fetch(name);
                if (image != null) {
                    cache.put(key, image);
                }
                pending.remove(key);
                if (image != null) {
                    SwingUtilities.invokeLater(onLoaded);
                }
            });
        }

        private static BufferedImage fetch(String name) {
            try {
                URLConnection connection = URI.create(
                        "https://minotar.net/helm/" + name + "/64.png").toURL().openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setRequestProperty("User-Agent", "Aurora");
                try (InputStream input = connection.getInputStream()) {
                    return ImageIO.read(input);
                }
            } catch (Exception exception) {
                return null;
            }
        }

        private static String key(String name) {
            return name.strip().toLowerCase(Locale.ROOT);
        }
    }

    /** A font-independent help badge with an immediate explanatory tooltip. */
    static final class HelpButton extends JComponent {
        private final Color foreground;

        HelpButton(String description) {
            this(description, MUTED_TEXT);
        }

        HelpButton(String description, Color foreground) {
            this.foreground = foreground;
            setPreferredSize(new Dimension(SwingTheme.scale(18), SwingTheme.scale(18)));
            setMinimumSize(new Dimension(SwingTheme.scale(18), SwingTheme.scale(18)));
            setMaximumSize(new Dimension(SwingTheme.scale(18), SwingTheme.scale(18)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String text = description == null || description.isBlank()
                    ? "No description is available for this setting."
                    : description;
            setToolTipText("<html><div style='width: 260px'>" + escapeHtml(text) + "</div></html>");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(foreground);
            g2.setStroke(new BasicStroke(SwingTheme.scaleFont(1.3f)));
            g2.drawOval(2, 2, getWidth() - 5, getHeight() - 5);
            g2.setFont(getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(11f)));
            String question = "?";
            int x = (getWidth() - g2.getFontMetrics().stringWidth(question)) / 2;
            int y = (getHeight() - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
            g2.drawString(question, x, y);
            g2.dispose();
        }
    }

    /** Compact warning marker used for modules that carry an elevated detection risk. */
    static final class WarningBadge extends JComponent {
        private static final Color WARNING = new Color(0xF5C451);

        WarningBadge(String warning) {
            int size = SwingTheme.scale(17);
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String text = warning == null || warning.isBlank() ? "Warning" : warning.strip();
            setToolTipText(text);
            getAccessibleContext().setAccessibleName(text);
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleWarningBadge();
            }
            return accessibleContext;
        }

        private final class AccessibleWarningBadge extends AccessibleJComponent {
            @Override
            public AccessibleRole getAccessibleRole() {
                return AccessibleRole.ICON;
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int inset = Math.max(1, SwingTheme.scale(1));
            Path2D triangle = new Path2D.Float();
            triangle.moveTo(getWidth() / 2.0D, inset);
            triangle.lineTo(getWidth() - inset, getHeight() - inset);
            triangle.lineTo(inset, getHeight() - inset);
            triangle.closePath();
            g2.setColor(WARNING);
            g2.fill(triangle);
            g2.setColor(new Color(0x241D0B));
            g2.setFont(getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(10f)));
            String mark = "!";
            int x = (getWidth() - g2.getFontMetrics().stringWidth(mark)) / 2;
            int y = (getHeight() - g2.getFontMetrics().getHeight()) / 2
                    + g2.getFontMetrics().getAscent() + SwingTheme.scale(2);
            g2.drawString(mark, x, y);
            g2.dispose();
        }
    }

    private static void styleSmallButton(JButton button) {
        button.setBackground(FIELD);
        button.setForeground(TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER), new EmptyBorder(SwingTheme.scale(5), SwingTheme.scale(9), SwingTheme.scale(5), SwingTheme.scale(9))));
        button.setFont(button.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(10.5f)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void styleValueField(JTextField field) {
        field.setBackground(FIELD);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER), new EmptyBorder(SwingTheme.scale(5), SwingTheme.scale(7), SwingTheme.scale(5), SwingTheme.scale(7))));
    }

    private static void styleComboBox(JComboBox<String> combo) {
        combo.setBackground(FIELD);
        combo.setForeground(TEXT);
        combo.setFocusable(true);
        combo.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focus) {
                Component component = super.getListCellRendererComponent(list, value, index, selected, focus);
                component.setBackground(selected ? ACCENT : FIELD);
                component.setForeground(TEXT);
                return component;
            }
        });
    }

    private String moduleSummary(ModuleView module) {
        List<String> values = module.settings().stream().filter(SettingView::visible).limit(2)
                .map(setting -> setting.displayName() + " " + setting.displayValue()).toList();
        String settings = values.isEmpty() ? "No settings" : String.join("  ·  ", values);
        return module.category() + "  ·  " + settings;
    }

    private void loadFavorites() {
        String stored = preferences.get("favorites", "");
        if (!stored.isBlank()) favorites.addAll(List.of(stored.split(",")));
    }

    private void saveFavorites() {
        preferences.put("favorites", String.join(",", favorites));
    }

    private static List<ModuleView> parseModules(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof List<?> entries)) return List.of();
        List<ModuleView> result = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> module) result.add(ModuleView.from(module));
        }
        result.sort(Comparator.comparing(ModuleView::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static int sliderScale(SettingView setting) {
        if (setting.step() <= 0) return 100;
        int decimals = Math.max(0, BigDecimal.valueOf(setting.step()).stripTrailingZeros().scale());
        return (int) Math.pow(10, Math.min(decimals, 4));
    }

    private static int safeScaled(double value, int scale) {
        long scaled = Math.round(value * scale);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, scaled));
    }

    private static double snap(double value, SettingView setting) {
        double clamped = Math.max(setting.min(), Math.min(setting.max(), value));
        if (setting.step() <= 0) return clamped;
        double steps = Math.round((clamped - setting.min()) / setting.step());
        double snapped = setting.min() + steps * setting.step();
        return BigDecimal.valueOf(snapped).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().doubleValue();
    }

    private static String formatValue(double value, double step) {
        int decimals = step > 0 ? Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale()) : 2;
        decimals = Math.min(decimals, 4);
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record ModuleView(String id, String displayName, String category, String description,
                              boolean enabled, int keybind, List<SettingView> settings) {
        static ModuleView from(Map<?, ?> map) {
            List<SettingView> settings = new ArrayList<>();
            if (map.get("settings") instanceof List<?> entries) {
                for (Object entry : entries) {
                    if (entry instanceof Map<?, ?> setting) settings.add(SettingView.from(setting));
                }
            }
            return new ModuleView(string(map, "id", "unknown"), string(map, "displayName", "Unnamed"),
                    string(map, "category", "Misc"), string(map, "description", ""),
                    Boolean.TRUE.equals(map.get("enabled")), integer(map.get("keybind"), KeybindCodec.UNBOUND),
                    List.copyOf(settings));
        }

        boolean matches(String query) {
            if (query.isEmpty()) return true;
            if ((displayName + " " + category + " " + description).toLowerCase(Locale.ROOT).contains(query)) return true;
            return settings.stream().anyMatch(setting -> setting.displayName().toLowerCase(Locale.ROOT).contains(query));
        }
    }

    private record SettingView(String id, String displayName, String description, String type, List<String> options,
                               double value, double defaultValue, double min, double max, double step, boolean visible) {
        static SettingView from(Map<?, ?> map) {
            List<String> options = map.get("options") instanceof List<?> values
                    ? values.stream().map(String::valueOf).toList() : List.of();
            double value = number(map.get("value"));
            double defaultValue = map.containsKey("default") ? number(map.get("default")) : value;
            return new SettingView(string(map, "id", "unknown"), string(map, "displayName", "Unnamed"),
                    string(map, "description", ""), string(map, "type", "number"), options, value, defaultValue, number(map.get("min")),
                    number(map.get("max")), number(map.get("step")), !Boolean.FALSE.equals(map.get("visible")));
        }

        String displayValue() {
            if ("boolean".equals(type)) return value >= 0.5D ? "On" : "Off";
            if ("color".equals(type)) return GuiPreferences.formatColor(new Color((int) Math.round(value) & 0xFFFFFF));
            if ("option".equals(type) && !options.isEmpty()) {
                return options.get(Math.max(0, Math.min(options.size() - 1, (int) Math.round(value))));
            }
            return formatValue(value, step);
        }
    }

    private static String string(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
