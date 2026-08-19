package dev.mc2p.common.exceptions;

import java.util.Map;

/** Thrown by a tool handler to report a user-facing rejection or failure. */
public final class ToolException extends RuntimeException {

    public ToolException(final String message) {
        super(message);
    }

    /** Result maps are always JSON-serializable (LInkedHashMap + records). */
    public static Map<String, Object> map(final String key, final Object value) {
        return Map.of(key, value);
    }
}
