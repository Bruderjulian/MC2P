package dev.mc2p.plugin.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builders for JSON Schema 2020-12 input schemas used by MCP tool definitions. */
public final class Schemas {

    private Schemas() {
    }

    public static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    public static Map<String, Object> str(String description) {
        return map("string", description, null);
    }

    public static Map<String, Object> str(String description, List<String> enumValues) {
        return map("string", description, enumValues);
    }

    public static Map<String, Object> integer(String description) {
        return map("integer", description, null);
    }

    public static Map<String, Object> num(String description) {
        return map("number", description, null);
    }

    public static Map<String, Object> bool(String description) {
        return map("boolean", description, null);
    }

    private static Map<String, Object> map(String type, String description, List<String> enumValues) {
        Map<String, Object> m = new LinkedHashMap<>();
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