package dev.aurora.injector;

import javax.swing.SwingUtilities;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class AuroraLauncher {
    private AuroraLauncher() {
    }

    public static void main(String[] args) throws Exception {
        SwingTheme.setAccent(GuiPreferences.accentColor());
        LogBuffer logs = new LogBuffer(300);
        String token = randomToken();
        AgentConnectionHub agentHub = new AgentConnectionHub(token, logs);
        agentHub.start();

        SwingUtilities.invokeLater(() ->
                new InstanceLauncherGui(new ProcessDiscovery(), new AttachService(), agentHub, token).setVisible(true));
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
