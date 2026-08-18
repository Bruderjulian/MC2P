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

    public record Secret(String value, String source, boolean fromEnvironment) {
    }

    private ConfigSupport() {
    }

    public static Map<String, Object> loadYaml(final Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try (InputStream in = Files.newInputStream(file)) {
            return loadYaml(in);
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

    /** Serializes a config map back to YAML text. */
    public static String dumpYaml(final Map<String, Object> config) {
        return new Yaml().dump(config);
    }

    /**
     * Resolves a token/secret source spec.
     *
     * @param spec    {@code env:VAR}, {@code file:path} (0600), or plaintext
     * @param baseDir directory relative {@code file:} paths are resolved against
     * @return the resolved secret, or null if the source is missing
     */
    public static Secret resolveSecret(final String spec, final Path baseDir) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        final String trimmed = spec.trim();
        if (trimmed.startsWith("env:")) {
            final String var = trimmed.substring(4).trim();
            final String value = System.getenv(var);
            if (value == null || value.isBlank()) {
                return null;
            }
            return new Secret(value, "env:" + var, true);
        }
        if (trimmed.startsWith("file:")) {
            final String pathSpec = trimmed.substring(5).trim();
            Path path = Path.of(pathSpec);
            if (!path.isAbsolute()) {
                path = baseDir.resolve(pathSpec);
            }
            if (!Files.isRegularFile(path)) {
                return null;
            }
            try {
                final String value = Files.readString(path).trim();
                if (value.isEmpty()) {
                    return null;
                }
                return new Secret(value, "file:" + path, false);
            } catch (final IOException e) {
                return null;
            }
        }
        // Plaintext: allowed but warned by the caller.
        return new Secret(trimmed, "config", false);
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
