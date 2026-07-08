package dev.aurora.agent;

import dev.aurora.injector.AgentArguments;
import dev.aurora.injector.IpcMessage;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AuroraAgent {
    public static final String VERSION = "0.1.0";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicReference<RuntimeController> CONTROLLER = new AtomicReference<>();

    private AuroraAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        install(agentArgs, instrumentation);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        install(agentArgs, instrumentation);
    }

    private static void install(String rawArgs, Instrumentation instrumentation) {
        if (!INSTALLED.compareAndSet(false, true)) {
            System.err.println("[Aurora] Agent already installed.");
            return;
        }

        AgentArguments arguments = AgentArguments.parse(rawArgs);
        IpcAgentClient ipc = new IpcAgentClient(arguments, message -> {
            RuntimeController controller = CONTROLLER.get();
            if (controller != null) {
                controller.handleIpc(message);
            }
        });
        boolean connected = ipc.connect();
        if (!connected) {
            System.err.println("[Aurora] Running without injector IPC. Use the localhost UI or provide agent args to control modules.");
        }
        RuntimeController controller = RuntimeController.install(instrumentation, ipc, arguments.minecraftVersion());
        CONTROLLER.set(controller);
        ipc.log("INFO", "Aurora agent installed for Minecraft "
                + (arguments.minecraftVersion().isBlank() ? "auto/unknown" : arguments.minecraftVersion()));
        ipc.send(IpcMessage.Type.STATUS, "{\"attached\":true,\"message\":\"Aurora agent installed\"}");
    }

    static void onUninstalled(RuntimeController controller) {
        CONTROLLER.compareAndSet(controller, null);
        INSTALLED.set(false);
    }
}
