package dev.aurora.injector;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record IpcMessage(Type type, String payload) {
    public enum Type {
        HELLO,
        STATUS,
        MODULE_LIST,
        MODULE_UPDATE,
        GLOBAL_SETTINGS,
        FRIENDS,
        EVENT_SAMPLE,
        LOG,
        DETACH
    }

    public String encode() {
        String encodedPayload = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return type.name() + "\t" + encodedPayload;
    }

    public static IpcMessage decode(String line) {
        int separator = line.indexOf('\t');
        if (separator < 0) {
            throw new IllegalArgumentException("invalid IPC frame");
        }
        Type type = Type.valueOf(line.substring(0, separator));
        String payload = new String(Base64.getDecoder().decode(line.substring(separator + 1)), StandardCharsets.UTF_8);
        return new IpcMessage(type, payload);
    }
}
