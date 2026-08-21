package dev.mc2p.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
}
