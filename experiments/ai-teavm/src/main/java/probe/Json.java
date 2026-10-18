package probe;

import java.util.Map;

/** Small diagnostic serializer; no reflection or third-party JSON runtime. */
public final class Json {
    private Json() { }
    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        StringBuilder out = new StringBuilder();
        if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(','); first = false;
                out.append(encode(entry.getKey().toString())).append(':').append(encode(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            out.append('['); boolean first = true;
            for (Object entry : values) {
                if (!first) out.append(','); first = false; out.append(encode(entry));
            }
            return out.append(']').toString();
        }
        out.append('"');
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 32) {
                        String hex = Integer.toHexString(c);
                        out.append("\\u"); for (int i = hex.length(); i < 4; i++) out.append('0'); out.append(hex);
                    } else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
