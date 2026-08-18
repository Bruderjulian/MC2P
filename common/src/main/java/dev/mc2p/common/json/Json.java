package dev.mc2p.common.json;

import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared JSON helper backed by Jackson 3 ({@code tools.jackson}), the same
 * library family
 * the MCP SDK 2.0 uses.
 */
public final class Json {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    public static String toJson(final Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to serialize value", e);
        }
    }

    public static byte[] toJsonBytes(final Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to serialize value", e);
        }
    }

    public static Map<String, Object> parse(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON", e);
        }
    }

    public static Map<String, Object> parse(final byte[] json) {
        return parse(new String(json, java.nio.charset.StandardCharsets.UTF_8));
    }
}
