package dev.mc2p.common.setup;

import dev.mc2p.common.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared in-plugin setup helpers: the static agent-side {@code mcpServers.json} template
 * and 0600 secret files for the proxy secret fallback.
 */
public final class SetupSupport {

    /** File name of the proxy secret fallback inside each plugin's data directory. */
    public static final String PROXY_SECRET_FILE = "proxy-secret";

    private SetupSupport() {}

    /**
     * Reads a secret from {@code dataDir/name}, or returns null when the file is missing
     * or blank.
     */
    public static String readSecretFile(Path dataDir, String name) {
        Path file = dataDir.resolve(name);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String value = Files.readString(file).trim();
            return value.isEmpty() ? null : value;
        } catch (IOException e) {
            return null;
        }
    }

    /** Writes {@code dataDir/name} with the secret and 0600 permissions. */
    public static void writeSecretFile(Path dataDir, String name, String secret) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve(name);
        Files.writeString(file, secret);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX filesystem
        }
    }

    /**
     * The static MCP client config; only {@code <HOST>}, the port, and {@code <TOKEN>}
     * vary. The admin replaces the placeholders with their public host and the token of
     * the permissions they grant the agent.
     */
    public static String clientConfigTemplate(int port) {
        Map<String, Object> mcpServers = new LinkedHashMap<>();
        mcpServers.put(
                "mcpServers",
                Map.of(
                        "mc2p",
                        Map.of(
                                "type",
                                "streamable-http",
                                "url",
                                "https://<HOST>:" + port + "/mcp",
                                "headers",
                                Map.of("Authorization", "Bearer <TOKEN>"))));
        return Json.toJson(mcpServers);
    }
}
