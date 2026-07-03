package dev.aurora.injector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public record AgentArguments(String host, int port, String token) {
    public String encode() {
        StringJoiner joiner = new StringJoiner(";");
        joiner.add("host=" + escape(host));
        joiner.add("port=" + port);
        joiner.add("token=" + escape(token));
        return joiner.toString();
    }

    public static AgentArguments parse(String raw) {
        Map<String, String> values = parseMap(raw);
        String host = values.getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(values.getOrDefault("port", "0"));
        String token = values.getOrDefault("token", "");
        return new AgentArguments(host, port, token);
    }

    public static Map<String, String> parseMap(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String part : raw.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            values.put(part.substring(0, equals), unescape(part.substring(equals + 1)));
        }
        return values;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\=");
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                builder.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
