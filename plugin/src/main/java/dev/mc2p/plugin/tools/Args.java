package dev.mc2p.plugin.tools;

import dev.mc2p.common.exceptions.ToolException;
import dev.mc2p.common.validate.Validators;
import java.util.Map;
import java.util.UUID;

/** Robust argument extraction from MCP tool argument maps (Jackson-decoded). */
public final class Args {

    private Args() {}

    public static String string(final Map<String, Object> args, final String key) {
        final Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static String requiredString(final Map<String, Object> args, final String key) throws ToolException {
        final String value = string(args, key);
        if (value == null || value.isBlank()) {
            throw new ToolException("missing required argument '" + key + "'");
        }
        return value.trim();
    }

    public static boolean bool(final Map<String, Object> args, final String key) {
        final Object value = args.get(key);
        if (value == null) {
            return false;
        } else if (value instanceof final Boolean b) {
            return b;
        } else if (value instanceof final String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    public static int integer(final Map<String, Object> args, final String key, final int fallback) throws ToolException {
        final Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof final Number n) {
            return n.intValue();
        }
        if (value instanceof final String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (final NumberFormatException e) {
                throw new ToolException("argument '" + key + "' must be an integer");
            }
        }
        throw new ToolException("argument '" + key + "' must be an integer");
    }

    public static UUID uuid(final Map<String, Object> args, final String key) throws ToolException {
        final UUID uuid = Validators.parseUuid(requiredString(args, key));
        if (uuid == null) {
            throw new ToolException("argument '" + key + "' must be a valid UUID");
        }
        return uuid;
    }

    public static UUID optionalUuid(final Map<String, Object> args, final String key) throws ToolException {
        final String value = string(args, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        final UUID uuid = Validators.parseUuid(value);
        if (uuid == null) {
            throw new ToolException("argument '" + key + "' must be a valid UUID");
        }
        return uuid;
    }
}
