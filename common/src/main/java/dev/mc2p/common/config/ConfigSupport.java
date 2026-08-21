package dev.mc2p.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * YAML config loading and secret-source resolution ({@code env:VAR},
 * {@code file:path},
 * plaintext).
 */
public final class ConfigSupport {

    private ConfigSupport() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadYaml(final Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try (InputStream in = Files.newInputStream(file)) {
            final Object parsed = new Yaml().load(in);
            if (parsed == null) {
                return new LinkedHashMap<>();
            }
            if (!(parsed instanceof Map)) {
                throw new IOException("config root must be a mapping");
            }
            return (Map<String, Object>) parsed;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadYaml(final InputStream in) throws IOException {
        final Object parsed = new Yaml().load(in);
        if (parsed == null) {
            return new LinkedHashMap<>();
        }
        if (!(parsed instanceof Map)) {
            throw new IOException("config root must be a mapping");
        }
        return (Map<String, Object>) parsed;
    }

    public static <T> T loadYaml(final Path file, Class<T> cls, final T def) throws IOException {
        if (!Files.isRegularFile(file)) {
            return def;
        }
        try (InputStream in = Files.newInputStream(file)) {
            final Object parsed = new Yaml().load(in);
            if (parsed == null) {
                return def;
            }
            // check if parsed is an instance of cls
            if (!cls.isInstance(parsed)) {
                throw new IOException("config root must be assignable to " + cls.getName());
            }
            return cls.cast(parsed);
        }
    }

    /** Serializes a config map back to YAML text. */
    public static String dumpYaml(final Map<String, Object> config) {
        return new Yaml().dump(config);
    }

    public static Map<String, Object> map(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final Map<String, Object> result = new LinkedHashMap<>();
            for (final Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    public static String str(final Map<String, Object> map, final String key, final String fallback) {
        final Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public static int integer(final Map<String, Object> map, final String key, final int fallback) {
        final Object value = map.get(key);
        if (value instanceof final Number n) {
            return n.intValue();
        }
        if (value instanceof final String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (final NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean bool(final Map<String, Object> map, final String key, final boolean fallback) {
        final Object value = map.get(key);
        if (value instanceof final Boolean b) {
            return b;
        }
        if (value instanceof final String s) {
            return switch (s.trim().toLowerCase()) {
                case "true", "yes", "on" -> true;
                case "false", "no", "off" -> false;
                default -> fallback;
            };
        }
        return fallback;
    }

    public static List<String> strings(final Map<String, Object> map, final String key) {
        final Object value = map.get(key);
        if (value instanceof final List<?> list) {
            final List<String> result = new ArrayList<>();
            for (final Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
}
