package dev.mc2p.common.tokens;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.util.Tokens;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Stores per-role client tokens as SHA-256 hashes only (never the plaintext), resolves
 * presented Bearer tokens to a role in constant time, and supports rotation/revocation
 * with persistence across restarts.
 *
 * <p>
 * Load order: configured tokens (from env / file / config.yml) are the base; tokens that
 * have been rotated in-game are persisted to a runtime file and take precedence.
 */
public final class TokenManager {

    public record AuthResult(Role role, String tokenId) {}

    public record TokenInfo(Role role, String tokenId, boolean configured) {}

    private final Path runtimeFile;
    private final Map<Role, Entry> entries = new EnumMap<>(Role.class);
    private final Map<Role, Entry> runtimeEntries = new EnumMap<>(Role.class);

    private static final class Entry {
        final String tokenId;
        final byte[] hash;

        Entry(String tokenId, byte[] hash) {
            this.tokenId = tokenId;
            this.hash = hash;
        }
    }

    public TokenManager(Path runtimeFile) {
        this.runtimeFile = runtimeFile;
    }

    /**
     * Replaces the base (configured) tokens with the given plaintext tokens, keeping any
     * rotated runtime tokens.
     */
    public void updateFromConfig(Map<Role, String> configuredTokens) {
        entries.clear();
        configuredHashes.clear();
        if (configuredTokens != null) {
            for (Map.Entry<Role, String> e : configuredTokens.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    addBase(e.getKey(), e.getValue());
                }
            }
        }
        loadRuntime();
        for (Map.Entry<Role, Entry> e : runtimeEntries.entrySet()) {
            entries.put(e.getKey(), e.getValue());
        }
    }

    private void addBase(Role role, String plaintext) {
        byte[] hash = Tokens.sha256(plaintext);
        entries.put(role, new Entry(Tokens.tokenId(hash), hash));
        configuredHashes.put(role, new Entry(Tokens.tokenId(hash), hash));
    }

    /**
     * Authenticates a presented token. Constant-time, never logs the secret.
     */
    public AuthResult authenticate(String presented) {
        if (presented == null || presented.isBlank()) {
            return null;
        }
        byte[] presentedHash = Tokens.sha256(presented);
        for (Map.Entry<Role, Entry> e : entries.entrySet()) {
            if (Tokens.constantTimeEquals(e.getValue().hash, presentedHash)) {
                return new AuthResult(e.getKey(), e.getValue().tokenId);
            }
        }
        return null;
    }

    /**
     * Rotates the token for a role. Returns the new plaintext token exactly once.
     */
    public String rotate(Role role) {
        String token = Tokens.generateToken();
        byte[] hash = Tokens.sha256(token);
        runtimeEntries.put(role, new Entry(Tokens.tokenId(hash), hash));
        entries.put(role, runtimeEntries.get(role));
        persistRuntime();
        return token;
    }

    /**
     * Revokes a rotated token, falling back to the configured token for the role if one
     * exists.
     */
    public boolean revoke(Role role) {
        Entry removed = runtimeEntries.remove(role);
        if (removed != null) {
            persistRuntime();
            if (configuredHashes.containsKey(role)) {
                entries.put(role, configuredHashes.get(role));
            } else {
                entries.remove(role);
            }
        }
        return removed != null;
    }

    private final Map<Role, Entry> configuredHashes = new EnumMap<>(Role.class);

    /**
     * Returns a snapshot of the currently active token ids (for /mc2p status).
     */
    public Map<Role, TokenInfo> snapshot() {
        Map<Role, TokenInfo> result = new EnumMap<>(Role.class);
        for (Map.Entry<Role, Entry> e : entries.entrySet()) {
            boolean rotated = runtimeEntries.containsKey(e.getKey());
            result.put(e.getKey(), new TokenInfo(e.getKey(), e.getValue().tokenId, !rotated));
        }
        return result;
    }

    private void persistRuntime() {
        if (runtimeFile == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder("# mc2p rotated tokens - do not share this file\n");
            for (Map.Entry<Role, Entry> e : runtimeEntries.entrySet()) {
                sb.append(e.getKey().name().toLowerCase())
                        .append(": ")
                        .append(java.util.HexFormat.of().formatHex(e.getValue().hash))
                        .append('\n');
            }
            java.nio.file.Files.writeString(runtimeFile, sb.toString());
            try {
                java.nio.file.Files.setPosixFilePermissions(
                        runtimeFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // non-POSIX filesystem
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist rotated tokens to " + runtimeFile, e);
        }
    }

    private void loadRuntime() {
        runtimeEntries.clear();
        if (runtimeFile == null || !java.nio.file.Files.isRegularFile(runtimeFile)) {
            return;
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(runtimeFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf(':');
                if (idx <= 0) {
                    continue;
                }
                Role role = Role.fromString(trimmed.substring(0, idx).trim());
                String hex = trimmed.substring(idx + 1).trim();
                if (role == null || hex.isEmpty()) {
                    continue;
                }
                byte[] hash = java.util.HexFormat.of().parseHex(hex);
                runtimeEntries.put(role, new Entry(Tokens.tokenId(hash), hash));
            }
        } catch (Exception e) {
            // Corrupt runtime file must never take the server down; keep only config tokens.
            runtimeEntries.clear();
        }
    }
}
