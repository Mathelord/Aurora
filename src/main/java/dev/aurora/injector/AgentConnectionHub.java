package dev.aurora.injector;

import dev.aurora.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentConnectionHub implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentConnectionHub.class);

    private final String token;
    private final LogBuffer logs;
    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool(Thread.ofVirtual().name("aurora-ipc-", 0).factory());
    private final AtomicReference<AgentConnection> connection = new AtomicReference<>();
    private volatile String statusJson = Json.object(Map.of("attached", false, "message", "No agent connected"));
    private volatile String modulesJson = "[]";
    private volatile String eventSampleJson = "{}";

    public AgentConnectionHub(String token, LogBuffer logs) throws IOException {
        this.token = token;
        this.logs = logs;
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
    }

    public void start() {
        executor.submit(this::acceptLoop);
        logs.add("INFO", "IPC server listening on 127.0.0.1:" + port());
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public String statusJson() {
        return statusJson;
    }

    public String modulesJson() {
        return modulesJson;
    }

    public String eventSampleJson() {
        return eventSampleJson;
    }

    public boolean sendModuleUpdate(String moduleId, Boolean enabled, Map<String, Double> settings) {
        return sendModuleUpdate(moduleId, enabled, null, settings);
    }

    public boolean sendModuleUpdate(String moduleId, Boolean enabled, Integer keybind, Map<String, Double> settings) {
        AgentConnection active = connection.get();
        if (active == null) {
            return false;
        }
        active.send(new IpcMessage(IpcMessage.Type.MODULE_UPDATE,
                new ModuleUpdate(moduleId, enabled, keybind, settings).toJson()));
        return true;
    }

    public boolean sendUninject() {
        AgentConnection active = connection.get();
        if (active == null) {
            return false;
        }
        active.send(new IpcMessage(IpcMessage.Type.DETACH, "{}"));
        statusJson = Json.object(Map.of("attached", true, "message", "Uninjecting Aurora"));
        return true;
    }

    public boolean sendGlobalSettings(boolean silentAimCrosshairIndicator) {
        AgentConnection active = connection.get();
        if (active == null) return false;
        active.send(new IpcMessage(IpcMessage.Type.GLOBAL_SETTINGS,
                Json.object(Map.of("silentAimCrosshairIndicator", silentAimCrosshairIndicator))));
        return true;
    }

    /** Pushes the friends list to the agent so combat modules and ESP can honor it. */
    public boolean sendFriends(List<String> friends) {
        AgentConnection active = connection.get();
        if (active == null) return false;
        active.send(new IpcMessage(IpcMessage.Type.FRIENDS, Json.array(friends)));
        return true;
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                executor.submit(() -> handle(socket));
            } catch (IOException exception) {
                if (!serverSocket.isClosed()) {
                    LOGGER.warn("IPC accept failed", exception);
                }
            }
        }
    }

    private void handle(Socket socket) {
        AgentConnection agentConnection = null;
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            IpcMessage hello = IpcMessage.decode(reader.readLine());
            if (hello.type() != IpcMessage.Type.HELLO || !token.equals(Json.stringField(hello.payload(), "token"))) {
                logs.add("WARN", "Rejected unauthenticated agent IPC connection");
                return;
            }
            agentConnection = new AgentConnection(writer);
            Optional.ofNullable(connection.getAndSet(agentConnection)).ifPresent(AgentConnection::close);
            statusJson = Json.object(Map.of("attached", true, "message", "Agent connected", "hello", hello.payload()));
            logs.add("INFO", "Agent connected");
            agentConnection.send(new IpcMessage(IpcMessage.Type.GLOBAL_SETTINGS,
                    Json.object(Map.of("silentAimCrosshairIndicator", GuiPreferences.silentAimCrosshairIndicator()))));
            agentConnection.send(new IpcMessage(IpcMessage.Type.FRIENDS,
                    Json.array(GuiPreferences.friends())));

            String line;
            while ((line = reader.readLine()) != null && agentConnection.open) {
                onMessage(IpcMessage.decode(line));
            }
        } catch (Exception exception) {
            logs.add("WARN", "Agent IPC disconnected: " + exception.getMessage());
        } finally {
            if (agentConnection != null && connection.compareAndSet(agentConnection, null)) {
                statusJson = Json.object(Map.of("attached", false, "message", "Agent disconnected"));
                modulesJson = "[]";
                eventSampleJson = "{}";
            }
        }
    }

    private void onMessage(IpcMessage message) {
        switch (message.type()) {
            case STATUS -> statusJson = message.payload();
            case MODULE_LIST -> modulesJson = message.payload();
            case EVENT_SAMPLE -> eventSampleJson = message.payload();
            case LOG -> logs.add(Json.stringField(message.payload(), "level") == null ? "INFO" : Json.stringField(message.payload(), "level"),
                    Json.stringField(message.payload(), "message") == null ? message.payload() : Json.stringField(message.payload(), "message"));
            case HELLO, MODULE_UPDATE, GLOBAL_SETTINGS, FRIENDS, DETACH -> {
            }
        }
    }

    @Override
    public void close() throws IOException {
        Optional.ofNullable(connection.get()).ifPresent(AgentConnection::close);
        serverSocket.close();
        executor.shutdownNow();
    }

    private static final class AgentConnection {
        private final PrintWriter writer;
        private volatile boolean open = true;

        private AgentConnection(PrintWriter writer) {
            this.writer = writer;
        }

        private synchronized void send(IpcMessage message) {
            if (!open) {
                return;
            }
            writer.println(message.encode());
        }

        private void close() {
            open = false;
        }
    }
}
