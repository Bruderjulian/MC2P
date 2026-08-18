package dev.mc2p.common.tokens;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.util.Tokens;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stores named client tokens as SHA-256 hashes only (never the plaintext), resolves
 * presented Bearer tokens to a name + role in constant time, and supports creating and
 * revoking tokens with persistence across restarts.
 *
 * <p>
 * A token is identified by the name the admin assigns when generating the key, so each
 * key maps to a name and a role. Multiple tokens per role are allowed; names must be
 * unique. Load order: configured tokens (from env / file / config.yml) are the base;
 * tokens created in-game are persisted to a runtime file and take precedence over a
 * configured token with the same name.
 */
public final class TokenManager {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,40}");

    public record AuthResult(String name, Role role, String tokenId) {}

    public record TokenInfo(String name, Role role, String tokenId, boolean configured, boolean disabled) {}

    /** A configured token before its secret is resolved: name, role, and secret source. */
    public record ConfigToken(Role role, String secret) {}

    private final Path runtimeFile;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Entry> configuredEntries = new LinkedHashMap<>();
    private final Map<String, Entry> runtimeEntries = new LinkedHashMap<>();
    private final Set<String> disabledNames = new LinkedHashSet<>();

    private static final class Entry {
        final String name;
        final Role role;
        final byte[] hash;
        final String tokenId;

        Entry(String name, Role role, byte[] hash) {
            this.name = name;
            this.role = role;
            this.hash = hash;
            this.tokenId = Tokens.tokenId(hash);
        }
    }

    public TokenManager(Path runtimeFile) {
        this.runtimeFile = runtimeFile;
    }

    /** True if the name is a valid token name (letters, digits, {@code -} and {@code _}, max 40). */
    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /** Default token name for a role when one is auto-generated. */
    public static String defaultName(Role role) {
        return role.name().toLowerCase();
    }

    /**
     * Replaces the base (configured) named tokens with the given ones, keeping any runtime
     * tokens (which take precedence per name).
     */
    public void updateFromConfig(Map<String, ConfigToken> configured) {
        configuredEntries.clear();
        if (configured != null) {
            for (Map.Entry<String, ConfigToken> e : configured.entrySet()) {
                String name = e.getKey();
                ConfigToken ct = e.getValue();
                if (name == null
                        || name.isBlank()
                        || ct == null
                        || ct.role() == null
                        || ct.secret() == null
                        || ct.secret().isBlank()) {
                    continue;
                }
                Entry entry = new Entry(name, ct.role(), Tokens.sha256(ct.secret()));
                configuredEntries.put(name, entry);
            }
        }
        loadRuntime();
        rebuild();
    }

    /**
     * Authenticates a presented token. Constant-time, never logs the secret.
     */
    public AuthResult authenticate(String presented) {
        if (presented == null || presented.isBlank()) {
            return null;
        }
        byte[] presentedHash = Tokens.sha256(presented);
        for (Entry e : entries.values()) {
            if (Tokens.constantTimeEquals(e.hash, presentedHash)) {
                return new AuthResult(e.name, e.role, e.tokenId);
            }
        }
        return null;
    }

    /**
     * Creates (or replaces) the runtime token for {@code name}. Returns the new plaintext
     * token exactly once.
     */
    public String create(String name, Role role) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid token name: " + name);
        }
        if (role == null) {
            throw new IllegalArgumentException("token role must not be null");
        }
        String token = Tokens.generateToken();
        Entry entry = new Entry(name, role, Tokens.sha256(token));
        runtimeEntries.put(name, entry);
        disabledNames.remove(name);
        entries.put(name, entry);
        persistRuntime();
        return token;
    }

    /**
     * Revokes a runtime token. A configured token with the same name, if any, becomes
     * active again.
     */
    public boolean revoke(String name) {
        Entry removed = runtimeEntries.remove(name);
        if (removed != null) {
            persistRuntime();
            rebuild();
        }
        return removed != null;
    }

    /**
     * Disables the active token with the given name (configured or runtime). Disabled
     * tokens can no longer authenticate but keep their entry and status.
     */
    public boolean disable(String name) {
        if (!entries.containsKey(name)) {
            return false;
        }
        disabledNames.add(name);
        rebuild();
        persistRuntime();
        return true;
    }

    /** Re-enables a previously disabled token by name. */
    public boolean enable(String name) {
        if (!disabledNames.remove(name)) {
            return false;
        }
        rebuild();
        persistRuntime();
        return true;
    }

    /** True if any active token carries the given role. */
    public boolean hasRole(Role role) {
        for (Entry e : entries.values()) {
            if (e.role == role) {
                return true;
            }
        }
        return false;
    }

    /** Returns a snapshot of the currently active tokens keyed by name (for status/audit). */
    public Map<String, TokenInfo> snapshot() {
        Map<String, TokenInfo> result = new LinkedHashMap<>();
        for (Entry e : configuredEntries.values()) {
            result.put(e.name, new TokenInfo(e.name, e.role, e.tokenId, true, disabledNames.contains(e.name)));
        }
        for (Entry e : runtimeEntries.values()) {
            result.put(e.name, new TokenInfo(e.name, e.role, e.tokenId, false, disabledNames.contains(e.name)));
        }
        return result;
    }

    /** Recomputes the active {@code entries} from the source maps, skipping disabled names. */
    private void rebuild() {
        entries.clear();
        for (Entry e : configuredEntries.values()) {
            if (!disabledNames.contains(e.name)) {
                entries.put(e.name, e);
            }
        }
        for (Entry e : runtimeEntries.values()) {
            if (!disabledNames.contains(e.name)) {
                entries.put(e.name, e);
            }
        }
        disabledNames.removeIf(n -> !configuredEntries.containsKey(n) && !runtimeEntries.containsKey(n));
    }

    private void persistRuntime() {
        if (runtimeFile == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder("# mc2p tokens - do not share this file\n");
            for (Map.Entry<String, Entry> e : runtimeEntries.entrySet()) {
                sb.append(e.getKey())
                        .append(": ")
                        .append(e.getValue().role.name().toLowerCase())
                        .append(' ')
                        .append(java.util.HexFormat.of().formatHex(e.getValue().hash))
                        .append('\n');
            }
            if (!disabledNames.isEmpty()) {
                sb.append("disabled: ").append(String.join(",", disabledNames)).append('\n');
            }
            java.nio.file.Files.writeString(runtimeFile, sb.toString());
            try {
                java.nio.file.Files.setPosixFilePermissions(
                        runtimeFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // non-POSIX filesystem
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist tokens to " + runtimeFile, e);
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
                if (trimmed.startsWith("disabled:")) {
                    String names = trimmed.substring("disabled:".length()).trim();
                    for (String n : names.split(",")) {
                        String name = n.trim();
                        if (isValidName(name)) {
                            disabledNames.add(name);
                        }
                    }
                    continue;
                }
                int idx = trimmed.indexOf(':');
                if (idx <= 0) {
                    continue;
                }
                String name = trimmed.substring(0, idx).trim();
                if (!isValidName(name)) {
                    continue;
                }
                String[] parts = trimmed.substring(idx + 1).trim().split("\\s+");
                if (parts.length == 0 || parts[0].isEmpty()) {
                    continue;
                }
                Role role;
                String hex;
                Role explicitRole = Role.fromString(parts[0]);
                if (explicitRole != null && parts.length >= 2) {
                    role = explicitRole;
                    hex = parts[1];
                } else {
                    // Legacy format (role: hex) where the token name equals the role name.
                    role = Role.fromString(name);
                    hex = parts[0];
                }
                if (role == null || hex == null || hex.isEmpty()) {
                    continue;
                }
                byte[] hash = java.util.HexFormat.of().parseHex(hex);
                runtimeEntries.put(name, new Entry(name, role, hash));
            }
        } catch (Exception e) {
            // Corrupt runtime file must never take the server down; keep only config tokens.
            runtimeEntries.clear();
            disabledNames.clear();
        }
    }
}
