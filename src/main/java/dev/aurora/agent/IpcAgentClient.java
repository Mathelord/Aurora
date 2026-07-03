package dev.aurora.agent;

import dev.aurora.injector.AgentArguments;
import dev.aurora.injector.IpcMessage;
import dev.aurora.util.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class IpcAgentClient implements AutoCloseable {
    private final AgentArguments arguments;
    private final Consumer<IpcMessage> inboundHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("aurora-agent-ipc").factory());
    private volatile PrintWriter writer;
    private volatile Socket socket;

    public IpcAgentClient(AgentArguments arguments, Consumer<IpcMessage> inboundHandler) {
        this.arguments = arguments;
        this.inboundHandler = inboundHandler;
    }

    public boolean connect() {
        if (arguments.port() <= 0 || arguments.token().isBlank()) {
            return false;
        }
        try {
            socket = new Socket(arguments.host(), arguments.port());
            writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            send(IpcMessage.Type.HELLO, Json.object(Map.of(
                    "token", arguments.token(),
                    "version", AuroraAgent.VERSION,
                    "vm", System.getProperty("java.vm.name", "unknown")
            )));
            executor.submit(this::readLoop);
            return true;
        } catch (IOException exception) {
            System.err.println("[Aurora] Could not connect to injector IPC: " + exception.getMessage());
            return false;
        }
    }

    public void send(IpcMessage.Type type, String payload) {
        PrintWriter activeWriter = writer;
        if (activeWriter == null) {
            return;
        }
        activeWriter.println(new IpcMessage(type, payload).encode());
    }

    public void log(String level, String message) {
        send(IpcMessage.Type.LOG, Json.object(Map.of("level", level, "message", message)));
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                inboundHandler.accept(IpcMessage.decode(line));
            }
        } catch (IOException exception) {
            System.err.println("[Aurora] Injector IPC closed: " + exception.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
        if (socket != null) {
            socket.close();
        }
    }
}
