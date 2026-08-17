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
 * YAML config loading and secret-source resolution ({@code env:VAR}, {@code file:path},
 * plaintext).
 */
public final class ConfigSupport {

    public record Secret(String value, String source, boolean fromEnvironment) {}

    private ConfigSupport() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadYaml(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try (InputStream in = Files.newInputStream(file)) {
            Object parsed = new Yaml().load(in);
            if (parsed == null) {
                return new LinkedHashMap<>();
            }
            if (!(parsed instanceof Map)) {
                throw new IOException("config root must be a mapping");
            }
            return (Map<String, Object>) parsed;
        }
    }

    /**
     * Resolves a token/secret source spec.
     *
     * @param spec    {@code env:VAR}, {@code file:path} (0600), or plaintext
     * @param baseDir directory relative {@code file:} paths are resolved against
     * @return the resolved secret, or null if the source is missing
     */
    public static Secret resolveSecret(String spec, Path baseDir) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String trimmed = spec.trim();
        if (trimmed.startsWith("env:")) {
            String var = trimmed.substring(4).trim();
            String value = System.getenv(var);
            if (value == null || value.isBlank()) {
                return null;
            }
            return new Secret(value, "env:" + var, true);
        }
        if (trimmed.startsWith("file:")) {
            String pathSpec = trimmed.substring(5).trim();
            Path path = Path.of(pathSpec);
            if (!path.isAbsolute()) {
                path = baseDir.resolve(pathSpec);
            }
            if (!Files.isRegularFile(path)) {
                return null;
            }
            try {
                String value = Files.readString(path).trim();
                if (value.isEmpty()) {
                    return null;
                }
                return new Secret(value, "file:" + path, false);
            } catch (IOException e) {
                return null;
            }
        }
        // Plaintext: allowed but warned by the caller.
        return new Secret(trimmed, "config", false);
    }

    public static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    public static String str(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return switch (s.trim().toLowerCase()) {
                case "true", "yes", "on" -> true;
                case "false", "no", "off" -> false;
                default -> fallback;
            };
        }
        return fallback;
    }

    public static List<String> strings(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
}
