package dev.mc2p.common.validate;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Input validation shared by the standalone MCP server and the backend RPC
 * path.
 * Rejection messages are deliberately terse and never echo secrets.
 */
public final class Validators {

    private Validators() {
    }

    /** World key allowlist: no path traversal, no absolute paths, no separators. */
    public static boolean isSafeWorldKey(final String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '.' || c == ' ' || c == '\0' || c == '~') {
                return false;
            }
        }
        return true;
    }

    /** Strict UUID parse; returns null on failure. */
    public static UUID parseUuid(final String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isWithinCoordinate(final int value, final int maxCoord) {
        final long max = (long) Math.max(1, maxCoord);
        return value >= -max && value <= max;
    }

    /**
     * Parses "x,y,z" into an int array; returns null on failure or if out of
     * bounds.
     */
    public static int[] parseCoordinates(final String value, final int maxCoord) {
        if (value == null) {
            return null;
        }
        final String[] parts = value.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            final int x = Integer.parseInt(parts[0].trim());
            final int y = Integer.parseInt(parts[1].trim());
            final int z = Integer.parseInt(parts[2].trim());
            if (!isWithinCoordinate(x, maxCoord)
                    || !isWithinCoordinate(y, maxCoord)
                    || !isWithinCoordinate(z, maxCoord)) {
                return null;
            }
            return new int[] { x, y, z };
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    public static boolean isValidPage(final int page, final int maxEntityLimit) {
        return page >= 0 && page < 100_000;
    }

    public static boolean isValidLimit(final int limit, final int max) {
        return limit >= 1 && limit <= max;
    }

    /** Normalizes a gamemode string; returns null if invalid. */
    public static String normalizeGamemode(final String value) {
        if (value == null) {
            return null;
        }
        final String lower = value.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "0", "survival", "s" -> "SURVIVAL";
            case "1", "creative", "c" -> "CREATIVE";
            case "2", "adventure", "a" -> "ADVENTURE";
            case "3", "spectator", "sp" -> "SPECTATOR";
            default -> null;
        };
    }

    /**
     * Material name must be a bare registry key (namespace:path), alphanumeric +
     * underscore + colon only.
     */
    public static boolean isSafeMaterialName(final String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == ':')) {
                return false;
            }
        }
        return true;
    }

    /** Entity type must be a bare registry key. */
    public static boolean isSafeEntityType(final String value) {
        return isSafeMaterialName(value);
    }

    /** Effect name must be a bare registry key. */
    public static boolean isSafeEffectName(final String value) {
        return isSafeMaterialName(value);
    }

    public static boolean isSafeReason(final String value) {
        return value != null && value.length() <= 256;
    }

    public static boolean isWithin(final int value, final int min, final int max) {
        return value >= min && value <= max;
    }

    /** Blocks world keys not present in the allowlist. */
    public static boolean isAllowedWorld(final String worldKey, final Set<String> allowedWorlds) {
        return allowedWorlds != null && allowedWorlds.contains(worldKey);
    }

    /**
     * Prevents an ops allowlist from being bypassed by enumeration (e.g.
     * "gamemode:...").
     */
    public static boolean isCommandFirstToken(final String command, final String entry) {
        if (command == null || entry == null) {
            return false;
        }
        final String token = firstToken(command);
        return token.equals(entry);
    }

    public static String firstToken(final String command) {
        if (command == null) {
            return "";
        }
        final String trimmed = command.trim();
        final int end = trimmed.indexOf(' ');
        return (end < 0 ? trimmed : trimmed.substring(0, end)).toLowerCase(Locale.ROOT);
    }
}
