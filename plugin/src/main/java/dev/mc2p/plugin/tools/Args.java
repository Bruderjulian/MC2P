package dev.mc2p.plugin.tools;

import dev.mc2p.common.validate.Validators;
import java.util.Map;
import java.util.UUID;

/** Robust argument extraction from MCP tool argument maps (Jackson-decoded). */
public final class Args {

    private Args() {}

    public static String string(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static String requiredString(Map<String, Object> args, String key) throws ToolException {
        String value = string(args, key);
        if (value == null || value.isBlank()) {
            throw new ToolException("missing required argument '" + key + "'");
        }
        return value.trim();
    }

    public static boolean bool(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return false;
        } else if (value instanceof Boolean b) {
            return b;
        } else if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    public static int integer(Map<String, Object> args, String key, int fallback) throws ToolException {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw new ToolException("argument '" + key + "' must be an integer");
            }
        }
        throw new ToolException("argument '" + key + "' must be an integer");
    }

    public static UUID uuid(Map<String, Object> args, String key) throws ToolException {
        UUID uuid = Validators.parseUuid(requiredString(args, key));
        if (uuid == null) {
            throw new ToolException("argument '" + key + "' must be a valid UUID");
        }
        return uuid;
    }

    public static UUID optionalUuid(Map<String, Object> args, String key) throws ToolException {
        String value = string(args, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        UUID uuid = Validators.parseUuid(value);
        if (uuid == null) {
            throw new ToolException("argument '" + key + "' must be a valid UUID");
        }
        return uuid;
    }
}
