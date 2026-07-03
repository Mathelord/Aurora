package dev.aurora.injector;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
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

import static dev.aurora.injector.SwingTheme.ACCENT;
import static dev.aurora.injector.SwingTheme.ACCENT_HOVER;
import static dev.aurora.injector.SwingTheme.BACKGROUND;
import static dev.aurora.injector.SwingTheme.CARD;
import static dev.aurora.injector.SwingTheme.CARD_HOVER;
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

    private final ProcessDiscovery processDiscovery;
    private final AttachService attachService;
    private final AgentConnectionHub agentHub;
    private final String token;
    private final JPanel listPanel = new JPanel();
    private final JLabel statusLabel = new JLabel(" ");
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
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(26, 28, 18, 28));

        JLabel wordmark = new JLabel("AURORA");
        wordmark.setForeground(TEXT);
        wordmark.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        wordmark.setAlignmentX(0f);
        header.add(wordmark);

        JPanel underline = new JPanel();
        underline.setBackground(ACCENT);
        underline.setMaximumSize(new Dimension(40, 3));
        underline.setPreferredSize(new Dimension(40, 3));
        underline.setAlignmentX(0f);
        underline.setBorder(new EmptyBorder(6, 0, 10, 0));
        header.add(underline);

        JLabel subtitle = new JLabel("Select a Minecraft instance to inject into");
        subtitle.setForeground(MUTED_TEXT);
        subtitle.setFont(subtitle.getFont().deriveFont(13f));
        subtitle.setAlignmentX(0f);
        header.add(subtitle);

        return header;
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
        JLabel nameLabel = new JLabel("Minecraft  ·  PID " + process.pid());
        nameLabel.setForeground(TEXT);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        text.add(nameLabel);
        JLabel detailLabel = new JLabel(loader.label + "  ·  " + truncate(process.displayName(), 46));
        detailLabel.setForeground(MUTED_TEXT);
        detailLabel.setFont(detailLabel.getFont().deriveFont(11.5f));
        detailLabel.setBorder(new EmptyBorder(3, 0, 0, 0));
        text.add(detailLabel);
        card.add(text, BorderLayout.CENTER);

        SwingTheme.PillButton inject = new SwingTheme.PillButton("Inject", ACCENT, ACCENT_HOVER);
        inject.setPreferredSize(new Dimension(88, 34));
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
                inject(process);
            }
        });

        return card;
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    private void inject(JavaProcess process) {
        if (injecting) {
            return;
        }
        injecting = true;
        statusLabel.setForeground(MUTED_TEXT);
        statusLabel.setText("Injecting into PID " + process.pid() + "...");

        new SwingWorker<AttachService.AttachResult, Void>() {
            @Override
            protected AttachService.AttachResult doInBackground() {
                return attachService.attach(process.pid(), new AgentArguments("127.0.0.1", agentHub.port(), token));
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
