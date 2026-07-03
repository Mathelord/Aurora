package dev.aurora.injector;

import dev.aurora.util.Json;

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
import javax.swing.JPanel;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

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
    private static final Color SIDEBAR = new Color(0x12, 0x11, 0x17);
    private static final Color TOPBAR = new Color(0x17, 0x16, 0x1D);
    private static final Color FIELD = new Color(0x24, 0x23, 0x2C);
    private static final Color SUCCESS = new Color(0x58, 0xD6, 0x8D);
    private static final String FAVORITES = "Favorites";
    private static final String ALL = "All modules";
    private static final String SETTINGS = "Settings";
    private static final String TARGET_RING = "Target Ring";
    private static final String TARGET_RING_MODULE_ID = "target-ring";

    private final AgentConnectionHub agentHub;
    private final Preferences preferences = Preferences.userNodeForPackage(ControlPanelGui.class);
    private final Set<String> favorites = new LinkedHashSet<>();
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

    private List<ModuleView> modules = List.of();
    private String selectedCategory = ALL;
    private String lastModulesJson = "";
    private String lastEventSampleJson = "";
    private String bindingModuleId;
    private boolean pointerInteraction;
    private boolean uninjectRequested;

    public ControlPanelGui(AgentConnectionHub agentHub) {
        super("Aurora — Control Panel");
        this.agentHub = agentHub;
        SwingTheme.setAccent(GuiPreferences.accentColor());
        loadFavorites();
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
        add(footer(), BorderLayout.SOUTH);

        pollTimer = new Timer(POLL_INTERVAL_MS, event -> poll());
        pollTimer.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                pollTimer.stop();
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
        bottomNavigationPanel.add(navigationButton("◎  " + TARGET_RING, TARGET_RING));
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

    private JComponent footer() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(TOPBAR);
        footer.setBorder(new EmptyBorder(SwingTheme.scale(7), SwingTheme.scale(24), SwingTheme.scale(7), SwingTheme.scale(24)));
        statusLabel.setForeground(MUTED_TEXT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(SwingTheme.scaleFont(11f)));
        footer.add(statusLabel, BorderLayout.WEST);
        footerHelpLabel.setForeground(MUTED_TEXT);
        footerHelpLabel.setFont(footerHelpLabel.getFont().deriveFont(SwingTheme.scaleFont(10.5f)));
        footer.add(footerHelpLabel, BorderLayout.EAST);
        return footer;
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
        bottomNavigationPanel.add(navigationButton("◎  " + TARGET_RING, TARGET_RING));
        bottomNavigationPanel.add(Box.createVerticalStrut(SwingTheme.scale(5)));
        bottomNavigationPanel.add(navigationButton("⚙  " + SETTINGS, SETTINGS));
        bottomNavigationPanel.add(hint);
        bottomNavigationPanel.revalidate();
        bottomNavigationPanel.repaint();
    }

    private JButton navigationButton(String text, String category) {
        JButton button = new JButton(text);
        boolean selected = category.equals(selectedCategory);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, SwingTheme.scale(38)));
        button.setPreferredSize(new Dimension(SwingTheme.scale(150), SwingTheme.scale(38)));
        button.setBackground(selected ? FIELD : SIDEBAR);
        button.setForeground(selected ? TEXT : MUTED_TEXT);
        button.setFont(button.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN, SwingTheme.scaleFont(13f)));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(SwingTheme.scale(0), SwingTheme.scale(10), SwingTheme.scale(0), SwingTheme.scale(10)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(event -> {
            selectedCategory = category;
            pageTitle.setText(category);
            boolean dedicated = SETTINGS.equals(category) || TARGET_RING.equals(category);
            searchField.setEnabled(!dedicated);
            footerHelpLabel.setText(SETTINGS.equals(category)
                    ? "Appearance changes are saved automatically"
                    : TARGET_RING.equals(category)
                    ? "The ring shows around Silent Aura's target while Silent Aura is on"
                    : "Enter applies values  ·  Backspace/Delete clears a keybind");
            rebuildNavigation();
            rebuildModuleList();
        });
        return button;
    }

    private void poll() {
        updateConnectionStatus();
        if (isEditing()) {
            return;
        }
        String modulesJson = agentHub.modulesJson();
        String eventSampleJson = agentHub.eventSampleJson();
        boolean modulesChanged = !modulesJson.equals(lastModulesJson);
        boolean sampleChanged = !eventSampleJson.equals(lastEventSampleJson);
        if (!modulesChanged && !sampleChanged) {
            return;
        }
        try {
            if (modulesChanged) {
                modules = parseModules(modulesJson);
                lastModulesJson = modulesJson;
                rebuildNavigation();
            }
            lastEventSampleJson = eventSampleJson;
            if (modulesChanged || SETTINGS.equals(selectedCategory)) {
                rebuildModuleList();
            }
        } catch (IllegalArgumentException exception) {
            showStatus("Could not read module data: " + exception.getMessage(), true);
        }
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
            connectionLabel.setText((attached ? "●  " : "○  ") + message);
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
        if (SETTINGS.equals(selectedCategory)) {
            rebuildSettingsPage();
            return;
        }
        if (TARGET_RING.equals(selectedCategory)) {
            rebuildTargetRingPage();
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
            for (ModuleView module : visible) {
                listPanel.add(moduleCard(module));
                listPanel.add(Box.createVerticalStrut(SwingTheme.scale(10)));
            }
        }
        resultCount.setText(visible.size() + (visible.size() == 1 ? " module" : " modules"));
        listPanel.revalidate();
        listPanel.repaint();
    }

    private void rebuildSettingsPage() {
        listPanel.setVisible(false);
        listPanel.removeAll();
        listPanel.add(appearanceCard());
        listPanel.add(Box.createVerticalStrut(SwingTheme.scale(12)));
        listPanel.add(scaleCard());
        listPanel.add(Box.createVerticalStrut(SwingTheme.scale(12)));
        listPanel.add(diagnosticsCard());
        finishDedicatedPageRebuild(SETTINGS, "Appearance & diagnostics");
    }

    private void finishDedicatedPageRebuild(String title, String count) {
        pageTitle.setText(title);
        resultCount.setText(count);
        listPanel.setVisible(true);
        listPanel.revalidate();
        if (listScroll != null) {
            listScroll.getViewport().setViewPosition(new Point(0, 0));
            listScroll.getViewport().revalidate();
            listScroll.getViewport().repaint();
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
                        + "Applies the next time this window opens.");
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
                showStatus("GUI scale set to " + percent + "%. Restart the control panel to apply it.", false);
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

    private JComponent diagnosticsCard() {
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(16, CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(SwingTheme.scale(20), SwingTheme.scale(24), SwingTheme.scale(20), SwingTheme.scale(24)));
        card.setAlignmentX(0f);

        JLabel title = new JLabel("Runtime diagnostics");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(16f)));
        title.setAlignmentX(0f);
        card.add(title);

        Map<?, ?> sample = diagnosticSample();
        long ticks = sampleLong(sample, "ticks");
        long hud = sampleLong(sample, "hudRenders");
        long world = sampleLong(sample, "worldRenders");
        long renderFailures = sampleLong(sample, "worldRenderFailures");
        long inbound = sampleLong(sample, "inboundPackets");
        long outbound = sampleLong(sample, "outboundPackets");
        long heldInbound = sampleLong(sample, "heldInbound");
        long heldOutbound = sampleLong(sample, "heldOutbound");
        long latency = sampleLong(sample, "inboundLatencyMs");

        JLabel hooks = new JLabel("Ticks " + ticks + "   ·   HUD " + hud + "   ·   3D " + world
                + "   ·   Render failures " + renderFailures);
        hooks.setForeground(ticks > 0 && hud > 0 && world > 0 && renderFailures == 0 ? SUCCESS : DANGER);
        hooks.setFont(hooks.getFont().deriveFont(SwingTheme.scaleFont(12f)));
        hooks.setBorder(new EmptyBorder(SwingTheme.scale(10), SwingTheme.scale(0), SwingTheme.scale(5), SwingTheme.scale(0)));
        hooks.setAlignmentX(0f);
        card.add(hooks);

        JLabel network = new JLabel("Inbound " + inbound + "   ·   Outbound " + outbound
                + "   ·   Held " + heldInbound + "/" + heldOutbound + "   ·   Lag " + latency + " ms");
        network.setForeground(MUTED_TEXT);
        network.setFont(network.getFont().deriveFont(SwingTheme.scaleFont(12f)));
        network.setAlignmentX(0f);
        card.add(network);

        Object errorValue = sample.get("lastError");
        String error = errorValue == null ? "" : String.valueOf(errorValue);
        JLabel lastError = new JLabel(error.isBlank() ? "No runtime errors reported."
                : "<html>Last error: " + escapeHtml(error) + "</html>");
        lastError.setForeground(error.isBlank() ? SUCCESS : DANGER);
        lastError.setFont(lastError.getFont().deriveFont(SwingTheme.scaleFont(11.5f)));
        lastError.setBorder(new EmptyBorder(SwingTheme.scale(8), SwingTheme.scale(0), SwingTheme.scale(0), SwingTheme.scale(0)));
        lastError.setAlignmentX(0f);
        card.add(lastError);

        int height = card.getPreferredSize().height;
        card.setPreferredSize(new Dimension(SwingTheme.scale(10), height));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return card;
    }

    private Map<?, ?> diagnosticSample() {
        try {
            Object parsed = Json.parse(agentHub.eventSampleJson());
            return parsed instanceof Map<?, ?> map ? map : Map.of();
        } catch (IllegalArgumentException ignored) {
            return Map.of();
        }
    }

    private static long sampleLong(Map<?, ?> sample, String key) {
        Object value = sample.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private void rebuildTargetRingPage() {
        listPanel.setVisible(false);
        listPanel.removeAll();
        ModuleView ring = modules.stream()
                .filter(module -> TARGET_RING_MODULE_ID.equals(module.id()))
                .findFirst()
                .orElse(null);
        if (ring == null) {
            listPanel.add(targetRingUnavailable());
            finishDedicatedPageRebuild(TARGET_RING, TARGET_RING);
        } else {
            listPanel.add(targetRingCard(ring));
            finishDedicatedPageRebuild(TARGET_RING, ring.enabled() ? "Enabled" : "Disabled");
        }
    }

    private JComponent targetRingUnavailable() {
        JPanel empty = new JPanel();
        empty.setOpaque(false);
        empty.setBorder(new EmptyBorder(SwingTheme.scale(70), SwingTheme.scale(20), SwingTheme.scale(20), SwingTheme.scale(20)));
        JLabel text = new JLabel("Connect the agent to configure the target ring.");
        text.setForeground(MUTED_TEXT);
        text.setFont(text.getFont().deriveFont(SwingTheme.scaleFont(13f)));
        empty.add(text);
        return empty;
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
        card.add(Box.createVerticalStrut(SwingTheme.scale(20)));

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

    private JComponent moduleCard(ModuleView module) {
        boolean expanded = expandedModules.contains(module.id());
        Color cardColor = module.enabled() ? SwingTheme.ACCENT : CARD;
        Color hoverColor = module.enabled() ? SwingTheme.ACCENT_HOVER : CARD_HOVER;
        Color activeText = SwingTheme.contrastText(cardColor);
        Color activeMutedText = SwingTheme.contrastMuted(cardColor);
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(16, cardColor);
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

        JButton favorite = new FavoriteButton(favorites.contains(module.id()), module.enabled());
        favorite.setToolTipText("Toggle favorite");
        favorite.addActionListener(event -> toggleFavorite(module.id()));
        summary.add(favorite, BorderLayout.WEST);

        JPanel identity = new JPanel();
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        identity.setOpaque(false);
        JLabel name = new JLabel(module.displayName());
        name.setForeground(module.enabled() ? activeText : TEXT);
        name.setFont(name.getFont().deriveFont(Font.BOLD, SwingTheme.scaleFont(15f)));
        identity.add(name);
        String detail = moduleSummary(module);
        JLabel details = new JLabel(detail);
        details.setForeground(module.enabled() ? activeMutedText : MUTED_TEXT);
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
        if (module.enabled()) expand.setForeground(activeText);
        expand.setToolTipText(expanded ? "Hide settings" : "Show settings");
        expand.addActionListener(event -> toggleExpanded(module.id()));
        controls.add(expand);
        summary.add(controls, BorderLayout.EAST);
        summary.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { card.setBackgroundColor(hoverColor); }
            @Override public void mouseExited(MouseEvent event) { card.setBackgroundColor(cardColor); }
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
        button.setToolTipText("Click, then press a keyboard key");
        button.addActionListener(event -> {
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
                int keybind = event.getKeyCode() == KeyEvent.VK_BACK_SPACE || event.getKeyCode() == KeyEvent.VK_DELETE
                        ? KeybindCodec.UNBOUND : KeybindCodec.fromKeyEvent(event);
                if (keybind == KeybindCodec.UNBOUND
                        && event.getKeyCode() != KeyEvent.VK_BACK_SPACE && event.getKeyCode() != KeyEvent.VK_DELETE) {
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
        private final boolean activeCard;

        private FavoriteButton(boolean favorite, boolean activeCard) {
            this.favorite = favorite;
            this.activeCard = activeCard;
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
            Color color = activeCard ? SwingTheme.contrastText(SwingTheme.ACCENT) : favorite ? ACCENT : MUTED_TEXT;
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
