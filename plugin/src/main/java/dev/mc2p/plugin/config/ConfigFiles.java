package dev.mc2p.plugin.config;

import dev.mc2p.common.config.ConfigSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the standalone ({@code config.yml}) / backend ({@code backend.yml}) config pair.
 *
 * <p>
 * Only one of the two files exists on disk at a time; switching modes writes the other
 * file from the bundled template, migrates the restrictions block into it, renames the
 * previous file to {@code *.old.yml}, and clears {@code tokens.yml} (tokens are not
 * portable across topologies).
 */
public final class ConfigFiles {

    private static final Logger log = LoggerFactory.getLogger(ConfigFiles.class);

    public static final String STANDALONE_FILE = "config.yml";
    public static final String BACKEND_FILE = "backend.yml";

    private ConfigFiles() {}

    /** The currently active config file: backend.yml wins if present, else config.yml. */
    public static Path activeConfigFile(Path dataDir) {
        Path backend = dataDir.resolve(BACKEND_FILE);
        return Files.isRegularFile(backend) ? backend : dataDir.resolve(STANDALONE_FILE);
    }

    /**
     * Writes the default {@code config.yml} (standalone) template on a fresh install. The
     * backend template is only materialized when the admin switches to backend mode.
     */
    public static void ensureInitialConfig(Plugin plugin, Path dataDir) {
        Path active = activeConfigFile(dataDir);
        if (Files.isRegularFile(active)) {
            return;
        }
        try {
            Files.createDirectories(dataDir);
            try (InputStream in = plugin.getResource(STANDALONE_FILE)) {
                if (in == null) {
                    throw new IllegalStateException("bundled template config.yml is missing from the jar");
                }
                Files.copy(in, dataDir.resolve(STANDALONE_FILE));
            }
            log.info("MC2P: wrote default config.yml (standalone topology). Run /mc2p setup to configure access.");
        } catch (IOException e) {
            throw new IllegalStateException("cannot write default config.yml", e);
        }
    }

    /**
     * Switches to the given topology: writes the matching config file (from the bundled
     * template, carrying over the active file's restrictions block), renames the current
     * file to {@code *.old.yml}, and clears the token store.
     *
     * @return the now-active config file path
     */
    public static Path switchTo(Plugin plugin, Path dataDir, String mode) throws IOException {
        Path active = activeConfigFile(dataDir);
        Path desired = desiredFile(dataDir, mode);

        Files.createDirectories(dataDir);
        if (active.equals(desired)) {
            return active;
        }

        String activeKey = restrictionsKey(active.getFileName().toString());
        String desiredKey = restrictionsKey(desired.getFileName().toString());

        Map<String, Object> base = new LinkedHashMap<>();
        try (InputStream in = plugin.getResource(desired.getFileName().toString())) {
            if (in == null) {
                throw new IOException("bundled template " + desired.getFileName() + " is missing from the jar");
            }
            base.putAll(ConfigSupport.loadYaml(in));
        }
        Map<String, Object> activeYaml = ConfigSupport.loadYaml(active);
        Object restrictions = activeYaml.get(activeKey);
        if (restrictions != null) {
            base.put(desiredKey, restrictions);
        }

        Path oldFile = Path.of(desired.getFileName() + ".old.yml");
        Path desiredOld = dataDir.resolve(oldFile.getFileName().toString());
        Files.deleteIfExists(desiredOld);
        Files.writeString(desired, ConfigSupport.dumpYaml(base));

        Path activeOld = dataDir.resolve(active.getFileName() + ".old.yml");
        Files.deleteIfExists(activeOld);
        Files.move(active, activeOld, StandardCopyOption.REPLACE_EXISTING);

        Files.deleteIfExists(dataDir.resolve("tokens.yml"));
        log.info(
                "MC2P: switched to {} topology: wrote {}, renamed {} to {}.old.yml, cleared tokens.yml.",
                mode,
                desired.getFileName(),
                active.getFileName(),
                active.getFileName());
        return desired;
    }

    private static Path desiredFile(Path dataDir, String mode) {
        return "backend".equals(mode) ? dataDir.resolve(BACKEND_FILE) : dataDir.resolve(STANDALONE_FILE);
    }

    private static String restrictionsKey(String fileName) {
        return BACKEND_FILE.equals(fileName) ? "server-restrictions" : "global-restrictions";
    }
}
