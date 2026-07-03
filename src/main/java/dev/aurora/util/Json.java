package dev.aurora.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class Json {
    private Json() {
    }

    /** Parses arbitrary JSON into {@code Map<String,Object>}, {@code List<Object>}, {@code String},
     * {@code Double}, {@code Boolean}, or {@code null}. Used where a flat field lookup (the
     * {@code *Field} helpers below) isn't enough, e.g. reading back a nested module/settings list. */
    public static Object parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        return value;
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                char next = peek();
                index++;
                if (next == '}') {
                    return result;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at index " + (index - 1));
                }
            }
        }

        private List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                char next = peek();
                index++;
                if (next == ']') {
                    return result;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at index " + (index - 1));
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (true) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c != '\\') {
                    builder.append(c);
                    continue;
                }
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        builder.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid escape at index " + index);
                }
            }
        }

        private Boolean parseBoolean() {
            if (text.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid literal at index " + index);
        }

        private Object parseNull() {
            if (text.startsWith("null", index)) {
                index += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid literal at index " + index);
        }

        private Double parseNumber() {
            int start = index;
            while (index < text.length() && isNumberChar(text.charAt(index))) {
                index++;
            }
            if (index == start) {
                throw new IllegalArgumentException("Invalid value at index " + index);
            }
            return Double.parseDouble(text.substring(start, index));
        }

        private static boolean isNumberChar(char c) {
            return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E';
        }

        private void expect(char expected) {
            if (index >= text.length() || text.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at index " + index);
            }
            index++;
        }

        private char peek() {
            if (index >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return text.charAt(index);
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }
    }

    public static String object(Map<String, ?> values) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        values.forEach((key, value) -> joiner.add(quote(key) + ":" + value(value)));
        return joiner.toString();
    }

    public static String array(Collection<?> values) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (Object value : values) {
            joiner.add(value(value));
        }
        return joiner.toString();
    }

    public static String quote(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    public static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Raw raw) {
            return raw.json();
        }
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((key, val) -> typed.put(String.valueOf(key), val));
            return object(typed);
        }
        if (value instanceof Collection<?> collection) {
            return array(collection);
        }
        return quote(String.valueOf(value));
    }

    public record Raw(String json) {
    }

    public static boolean booleanField(String json, String field, boolean defaultValue) {
        if (!hasField(json, field)) {
            return defaultValue;
        }
        String value = scalarField(json, field);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public static Double numberField(String json, String field) {
        String value = scalarField(json, field);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Map<String, Double> numberFields(String json) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (json == null) {
            return values;
        }
        int index = 0;
        while (index < json.length()) {
            int keyStart = json.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = keyEnd(json, keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            String key = unescape(json.substring(keyStart + 1, keyEnd));
            int colon = skipWhitespace(json, keyEnd + 1);
            if (colon >= json.length() || json.charAt(colon) != ':') {
                index = keyEnd + 1;
                continue;
            }
            int valueStart = skipWhitespace(json, colon + 1);
            int valueEnd = numericValueEnd(json, valueStart);
            if (valueEnd > valueStart) {
                try {
                    values.put(key, Double.parseDouble(json.substring(valueStart, valueEnd)));
                } catch (NumberFormatException ignored) {
                }
            }
            index = Math.max(valueEnd, keyEnd + 1);
        }
        return values;
    }

    public static String stringField(String json, String field) {
        String key = quote(field) + ":";
        int start = json.indexOf(key);
        if (start < 0) {
            return null;
        }
        int quoteStart = json.indexOf('"', start + key.length());
        if (quoteStart < 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                builder.append(switch (c) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> c;
                });
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return builder.toString();
            } else {
                builder.append(c);
            }
        }
        return null;
    }

    public static boolean hasField(String json, String field) {
        return json != null && json.contains(quote(field) + ":");
    }

    private static int keyEnd(String json, int start) {
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                builder.append(switch (c) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> c;
                });
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static int skipWhitespace(String json, int start) {
        int index = start;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int numericValueEnd(String json, int start) {
        int index = start;
        while (index < json.length()) {
            char c = json.charAt(index);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    private static String scalarField(String json, String field) {
        String key = quote(field) + ":";
        int start = json.indexOf(key);
        if (start < 0) {
            return null;
        }
        int valueStart = start + key.length();
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char c = json.charAt(valueEnd);
            if (c == ',' || c == '}' || Character.isWhitespace(c)) {
                break;
            }
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd);
    }
}
