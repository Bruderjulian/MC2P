package dev.mc2p.common.json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builders for JSON Schema 2020-12 input schemas used by MCP tool definitions. */
public final class Schemas {

    private Schemas() {}

    public static Map<String, Object> object(final Map<String, Object> properties, final List<String> required) {
        final Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    public static Map<String, Object> str(final String description) {
        return map("string", description, null);
    }

    public static Map<String, Object> str(final String description, final List<String> enumValues) {
        return map("string", description, enumValues);
    }

    public static Map<String, Object> integer(final String description) {
        return map("integer", description, null);
    }

    public static Map<String, Object> num(final String description) {
        return map("number", description, null);
    }

    public static Map<String, Object> bool(final String description) {
        return map("boolean", description, null);
    }

    private static Map<String, Object> map(final String type, final String description, final List<String> enumValues) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        if (description != null) {
            m.put("description", description);
        }
        if (enumValues != null) {
            m.put("enum", enumValues);
        }
        return m;
    }

    public static Map<String, Object> confirmSchema() {
        return bool("Acknowledgment that this destructive action is intended. Must be true.");
    }
}
