package dev.mc2p.common.validate;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Input validation shared by the standalone MCP server and the backend RPC path.
 * Rejection messages are deliberately terse and never echo secrets.
 */
public final class Validators {

    private Validators() {}

    /** World key allowlist: no path traversal, no absolute paths, no separators. */
    public static boolean isSafeWorldKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '.' || c == ' ' || c == '\0' || c == '~') {
                return false;
            }
        }
        return true;
    }

    /** Strict UUID parse; returns null on failure. */
    public static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isWithinCoordinate(int value, int maxCoord) {
        long max = (long) Math.max(1, maxCoord);
        return value >= -max && value <= max;
    }

    /** Parses "x,y,z" into an int array; returns null on failure or if out of bounds. */
    public static int[] parseCoordinates(String value, int maxCoord) {
        if (value == null) {
            return null;
        }
        String[] parts = value.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            if (!isWithinCoordinate(x, maxCoord)
                    || !isWithinCoordinate(y, maxCoord)
                    || !isWithinCoordinate(z, maxCoord)) {
                return null;
            }
            return new int[] {x, y, z};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isValidPage(int page, int maxEntityLimit) {
        return page >= 0 && page < 100_000;
    }

    public static boolean isValidLimit(int limit, int max) {
        return limit >= 1 && limit <= max;
    }

    /** Normalizes a gamemode string; returns null if invalid. */
    public static String normalizeGamemode(String value) {
        if (value == null) {
            return null;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "0", "survival", "s" -> "SURVIVAL";
            case "1", "creative", "c" -> "CREATIVE";
            case "2", "adventure", "a" -> "ADVENTURE";
            case "3", "spectator", "sp" -> "SPECTATOR";
            default -> null;
        };
    }

    /** Material name must be a bare registry key (namespace:path), alphanumeric + underscore + colon only. */
    public static boolean isSafeMaterialName(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == ':')) {
                return false;
            }
        }
        return true;
    }

    /** Entity type must be a bare registry key. */
    public static boolean isSafeEntityType(String value) {
        return isSafeMaterialName(value);
    }

    /** Effect name must be a bare registry key. */
    public static boolean isSafeEffectName(String value) {
        return isSafeMaterialName(value);
    }

    public static boolean isSafeReason(String value) {
        return value != null && value.length() <= 256;
    }

    public static boolean isWithin(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /** Blocks world keys not present in the allowlist. */
    public static boolean isAllowedWorld(String worldKey, Set<String> allowedWorlds) {
        return allowedWorlds != null && allowedWorlds.contains(worldKey);
    }

    /** Prevents an ops allowlist from being bypassed by enumeration (e.g. "gamemode:..."). */
    public static boolean isCommandFirstToken(String command, String entry) {
        if (command == null || entry == null) {
            return false;
        }
        String token = firstToken(command);
        return token.equals(entry);
    }

    public static String firstToken(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        int end = trimmed.indexOf(' ');
        return (end < 0 ? trimmed : trimmed.substring(0, end)).toLowerCase(Locale.ROOT);
    }
}
