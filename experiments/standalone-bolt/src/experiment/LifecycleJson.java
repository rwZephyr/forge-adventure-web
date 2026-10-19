package experiment;

import java.util.*;

/** JDK-only encoding for diagnostic primitives; never serializes arbitrary Forge objects. */
final class LifecycleJson {
    private LifecycleJson() { }
    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof String s) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : s.toCharArray()) switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
            return out.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringJoiner out = new StringJoiner(",", "{", "}");
            for (var e : map.entrySet()) {
                if (!(e.getKey() instanceof String)) throw new IllegalArgumentException("Diagnostic key");
                out.add(encode(e.getKey()) + ":" + encode(e.getValue()));
            }
            return out.toString();
        }
        if (value instanceof Collection<?> values) {
            StringJoiner out = new StringJoiner(",", "[", "]");
            for (Object v : values) out.add(encode(v));
            return out.toString();
        }
        throw new IllegalArgumentException("Not diagnostic data: " + value.getClass().getName());
    }
}
