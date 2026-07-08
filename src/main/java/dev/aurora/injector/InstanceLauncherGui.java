package dev.aurora.injector;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static dev.aurora.injector.SwingTheme.ACCENT;
import static dev.aurora.injector.SwingTheme.ACCENT_HOVER;
import static dev.aurora.injector.SwingTheme.BACKGROUND;
import static dev.aurora.injector.SwingTheme.CARD;
import static dev.aurora.injector.SwingTheme.CARD_HOVER;
import static dev.aurora.injector.SwingTheme.CARD_BORDER;
import static dev.aurora.injector.SwingTheme.DANGER;
import static dev.aurora.injector.SwingTheme.MUTED_TEXT;
import static dev.aurora.injector.SwingTheme.TEXT;

/**
 * Standalone desktop launcher: lists detected Minecraft instances and injects the Aurora agent
 * into whichever one the user clicks. On success this window closes and hands off to
 * {@link ControlPanelGui}, which is where modules/settings/uninject are controlled from now on.
 */
public final class InstanceLauncherGui extends JFrame {
    private static final Color BADGE_FABRIC = new Color(0xDF, 0xA6, 0x2B);
    private static final Color BADGE_FORGE = new Color(0xC4, 0x6B, 0x4A);
    private static final Color BADGE_QUILT = new Color(0xA0, 0x6A, 0xF2);
    private static final Color BADGE_VANILLA = new Color(0x5C, 0xC8, 0x8F);
    private static final int REFRESH_INTERVAL_MS = 2000;
    private static final int CARD_ARC = 16;
    private static final int CARD_HEIGHT = 76;
    private static final String AUTO_VERSION = "Auto";
    private static final String[] VERSION_MODES = {
            AUTO_VERSION, "1.21.4", "1.21.11"
    };
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("1.21.4", "1.21.11");

    private final ProcessDiscovery processDiscovery;
    private final AttachService attachService;
    private final AgentConnectionHub agentHub;
    private final String token;
    private final JPanel listPanel = new JPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private String versionMode = AUTO_VERSION;
    private boolean injecting;

    public InstanceLauncherGui(ProcessDiscovery processDiscovery, AttachService attachService,
                                AgentConnectionHub agentHub, String token) {
        super("Aurora");
        this.processDiscovery = processDiscovery;
        this.attachService = attachService;
        this.agentHub = agentHub;
        this.token = token;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(560, 520);
        setMinimumSize(new Dimension(420, 320));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BACKGROUND);
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder());

        add(header(), BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(BACKGROUND);
        listPanel.setBorder(new EmptyBorder(4, 24, 4, 20));
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(BACKGROUND);
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setUI(new SwingTheme.DarkScrollBarUI());
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);

        statusLabel.setForeground(MUTED_TEXT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        statusLabel.setBorder(new EmptyBorder(6, 28, 18, 28));
        add(statusLabel, BorderLayout.SOUTH);

        refresh();
        Timer timer = new Timer(REFRESH_INTERVAL_MS, event -> {
            if (!injecting) {
                refresh();
            }
        });
        timer.start();
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(26, 28, 18, 28));

        JPanel branding = new JPanel();
        branding.setLayout(new BoxLayout(branding, BoxLayout.Y_AXIS));
        branding.setOpaque(false);

        JLabel wordmark = new JLabel("AURORA");
        wordmark.setForeground(TEXT);
        wordmark.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        wordmark.setAlignmentX(0f);
        branding.add(wordmark);

        JPanel underline = new JPanel();
        underline.setBackground(ACCENT);
        underline.setMaximumSize(new Dimension(40, 3));
        underline.setPreferredSize(new Dimension(40, 3));
        underline.setAlignmentX(0f);
        underline.setBorder(new EmptyBorder(6, 0, 10, 0));
        branding.add(underline);

        JLabel subtitle = new JLabel("Select a Minecraft instance to inject into");
        subtitle.setForeground(MUTED_TEXT);
        subtitle.setFont(subtitle.getFont().deriveFont(13f));
        subtitle.setAlignmentX(0f);
        branding.add(subtitle);
        header.add(branding, BorderLayout.CENTER);

        JComboBox<String> versionSelector = versionSelector();
        JPanel modePanel = new JPanel();
        modePanel.setLayout(new BoxLayout(modePanel, BoxLayout.Y_AXIS));
        modePanel.setOpaque(false);

        JLabel modeLabel = new JLabel("VERSION MODE");
        modeLabel.setForeground(MUTED_TEXT);
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD, 9.5f));
        modeLabel.setAlignmentX(1f);
        modeLabel.setLabelFor(versionSelector);
        modePanel.add(modeLabel);
        modePanel.add(spacer(5));
        modePanel.add(versionSelector);
        header.add(modePanel, BorderLayout.EAST);

        return header;
    }

    private JComboBox<String> versionSelector() {
        JComboBox<String> selector = new JComboBox<>(VERSION_MODES);
        selector.setSelectedItem(versionMode);
        selector.setBackground(CARD);
        selector.setForeground(TEXT);
        selector.setFocusable(true);
        selector.setMaximumSize(new Dimension(132, 34));
        selector.setPreferredSize(new Dimension(132, 34));
        selector.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
        selector.setToolTipText("Auto detects the instance version; selecting a version overrides the launcher display.");
        selector.getAccessibleContext().setAccessibleName("Minecraft version mode");
        selector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                Component component = super.getListCellRendererComponent(list, value, index, selected, focused);
                component.setBackground(selected ? ACCENT : CARD);
                component.setForeground(TEXT);
                return component;
            }
        });
        selector.addActionListener(event -> {
            Object selected = selector.getSelectedItem();
            versionMode = selected instanceof String value ? value : AUTO_VERSION;
            refresh();
        });
        return selector;
    }

    private void refresh() {
        List<JavaProcess> processes = processDiscovery.listJavaProcesses().stream()
                .filter(JavaProcess::likelyMinecraft)
                .toList();
        listPanel.removeAll();
        if (processes.isEmpty()) {
            listPanel.add(emptyState());
        } else {
            for (JavaProcess process : processes) {
                listPanel.add(row(process));
                listPanel.add(spacer(10));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JComponent emptyState() {
        JPanel empty = new JPanel();
        empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
        empty.setOpaque(false);
        empty.setBorder(new EmptyBorder(48, 8, 8, 8));

        JLabel headline = new JLabel("No Minecraft instances found");
        headline.setForeground(TEXT);
        headline.setFont(headline.getFont().deriveFont(Font.BOLD, 14f));
        headline.setAlignmentX(0.5f);
        empty.add(headline);

        JLabel hint = new JLabel("Launch Minecraft, then it will appear here automatically.");
        hint.setForeground(MUTED_TEXT);
        hint.setFont(hint.getFont().deriveFont(12f));
        hint.setAlignmentX(0.5f);
        hint.setBorder(new EmptyBorder(6, 0, 0, 0));
        empty.add(hint);

        return empty;
    }

    private static JComponent spacer(int height) {
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        spacer.setPreferredSize(new Dimension(1, height));
        return spacer;
    }

    private JComponent row(JavaProcess process) {
        SwingTheme.RoundedPanel card = new SwingTheme.RoundedPanel(CARD_ARC, CARD);
        card.setLayout(new BorderLayout(14, 0));
        card.setBorder(new EmptyBorder(14, 18, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
        card.setPreferredSize(new Dimension(10, CARD_HEIGHT));
        card.setAlignmentX(0f);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Loader loader = Loader.detect(process);
        card.add(new LoaderDot(loader.color), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        String versionLabel = effectiveVersion(process)
                .map(version -> "Minecraft " + version)
                .orElse("Minecraft · Unknown version");
        JLabel nameLabel = new JLabel(versionLabel + "  ·  PID " + process.pid());
        nameLabel.setForeground(TEXT);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        text.add(nameLabel);
        String modeDetail = AUTO_VERSION.equals(versionMode)
                ? truncate(process.displayName(), 46)
                : "Manual mode" + process.minecraftVersion().map(value -> " · detected " + value).orElse("");
        JLabel detailLabel = new JLabel(loader.label + "  ·  " + modeDetail);
        detailLabel.setForeground(MUTED_TEXT);
        detailLabel.setFont(detailLabel.getFont().deriveFont(11.5f));
        detailLabel.setBorder(new EmptyBorder(3, 0, 0, 0));
        text.add(detailLabel);
        card.add(text, BorderLayout.CENTER);

        boolean supported = effectiveVersion(process).map(SUPPORTED_VERSIONS::contains).orElse(false);
        SwingTheme.PillButton inject = new SwingTheme.PillButton(supported ? "Inject" : "Unsupported",
                ACCENT, ACCENT_HOVER);
        inject.setPreferredSize(new Dimension(supported ? 88 : 112, 34));
        inject.setEnabled(supported);
        if (!supported) {
            inject.setToolTipText("Aurora supports Minecraft 1.21.4 and 1.21.11.");
        }
        inject.addActionListener(event -> inject(process));
        JPanel injectHolder = new JPanel();
        injectHolder.setOpaque(false);
        injectHolder.add(inject);
        card.add(injectHolder, BorderLayout.EAST);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                card.setBackgroundColor(CARD_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                card.setBackgroundColor(CARD);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (supported) {
                    inject(process);
                }
            }
        });

        return card;
    }

    private java.util.Optional<String> effectiveVersion(JavaProcess process) {
        return AUTO_VERSION.equals(versionMode)
                ? process.minecraftVersion()
                : java.util.Optional.of(versionMode);
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    private void inject(JavaProcess process) {
        if (injecting) {
            return;
        }
        if (!effectiveVersion(process).map(SUPPORTED_VERSIONS::contains).orElse(false)) {
            statusLabel.setForeground(DANGER);
            statusLabel.setText("Unsupported Minecraft version. Select 1.21.4 or 1.21.11.");
            return;
        }
        injecting = true;
        statusLabel.setForeground(MUTED_TEXT);
        statusLabel.setText("Injecting into PID " + process.pid() + "...");

        new SwingWorker<AttachService.AttachResult, Void>() {
            @Override
            protected AttachService.AttachResult doInBackground() {
                String minecraftVersion = effectiveVersion(process).orElse("");
                return attachService.attach(process.pid(), new AgentArguments(
                        "127.0.0.1", agentHub.port(), token, minecraftVersion));
            }

            @Override
            protected void done() {
                AttachService.AttachResult result;
                try {
                    result = get();
                } catch (Exception exception) {
                    result = AttachService.AttachResult.failure("Unexpected error: " + exception.getMessage());
                }
                if (result.success()) {
                    dispose();
                    new ControlPanelGui(agentHub).setVisible(true);
                } else {
                    injecting = false;
                    statusLabel.setForeground(DANGER);
                    statusLabel.setText(result.message());
                    JOptionPane.showMessageDialog(InstanceLauncherGui.this, result.message(), "Inject failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private enum Loader {
        FABRIC("Fabric", BADGE_FABRIC),
        QUILT("Quilt", BADGE_QUILT),
        FORGE("Forge", BADGE_FORGE),
        VANILLA("Vanilla", BADGE_VANILLA);

        private final String label;
        private final Color color;

        Loader(String label, Color color) {
            this.label = label;
            this.color = color;
        }

        static Loader detect(JavaProcess process) {
            String text = (process.displayName() + " " + process.commandLine()).toLowerCase(Locale.ROOT);
            if (text.contains("fabric")) {
                return FABRIC;
            }
            if (text.contains("quilt")) {
                return QUILT;
            }
            if (text.contains("forge")) {
                return FORGE;
            }
            return VANILLA;
        }
    }

    /** Small filled circle used as a per-row loader indicator. */
    private static final class LoaderDot extends JComponent {
        private static final int SIZE = 10;
        private final Color color;

        private LoaderDot(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(SIZE, CARD_HEIGHT - 28));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = (getHeight() - SIZE) / 2;
            g2.setColor(color);
            g2.fillOval(0, y, SIZE, SIZE);
            g2.dispose();
        }
    }
}
