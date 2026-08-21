package dev.mc2p.common.validate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Args {

  private Args() {
  }

  public static String string(final Map<String, Object> args, final String key, final String fallback) {
    final Object value = args.get(key);
    return value == null ? fallback : String.valueOf(value);
  }

  public static String requiredString(final Map<String, Object> args, final String key)
      throws IllegalArgumentException {
    final String value = string(args, key, null);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("missing required argument '" + key + "'");
    }
    return value.trim();
  }

  public static List<String> strings(final Map<String, Object> map, final String key) {
    return strings(map, key, List.of());
  }

  public static List<String> strings(final Map<String, Object> map, final String key, final List<String> fallback) {
    final Object value = map.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof final List<?> list) {
      final List<String> result = new ArrayList<>();
      for (final Object item : list) {
        if (item != null) {
          result.add(String.valueOf(item));
        }
      }
      return result;
    }
    return fallback;
  }

  public static boolean bool(final Map<String, Object> args, final String key, final boolean fallback) {
    final Object value = args.get(key);
    if (value == null) {
      return fallback;
    } else if (value instanceof final Boolean b) {
      return b;
    } else if (value instanceof final String s) {
      return switch (s.trim().toLowerCase()) {
        case "true", "yes", "on" -> true;
        case "false", "no", "off" -> false;
        default -> fallback;
      };
    } else if (value instanceof final Number n) {
      return n.intValue() != 0;
    }
    return fallback;
  }

  public static int integer(final Map<String, Object> args, final String key, final int fallback)
      throws IllegalArgumentException {
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
        throw new IllegalArgumentException("argument '" + key + "' must be an integer");
      }
    }
    throw new IllegalArgumentException("argument '" + key + "' must be an integer");
  }

  public static UUID requiredUUID(final Map<String, Object> args, final String key) throws IllegalArgumentException {
    try {
      return UUID.fromString(requiredString(args, key));
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException("argument '" + key + "' must be a valid UUID");
    }
  }

  public static UUID optionalUuid(final Map<String, Object> args, final String key) throws IllegalArgumentException {
    final String value = string(args, key, null);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value.trim());
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  public static Map<String, Object> map(final Object value) {
    return map(value, Map.of());
  }

  public static Map<String, Object> map(final Object value, final Map<String, Object> fallback) {
    if (value == null) {
      return fallback;
    }
    if (value instanceof final Map<?, ?> map) {
      final Map<String, Object> result = new LinkedHashMap<>();
      for (final Map.Entry<?, ?> e : map.entrySet()) {
        result.put(String.valueOf(e.getKey()), e.getValue());
      }
      return result;
    }
    return fallback;
  }
}
