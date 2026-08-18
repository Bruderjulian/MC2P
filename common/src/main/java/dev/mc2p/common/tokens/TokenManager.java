package dev.mc2p.common.tokens;

import dev.mc2p.common.config.ConfigSupport;
import dev.mc2p.common.config.RestrictionsConfig;
import dev.mc2p.common.util.Tokens;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * Stores named client tokens as SHA-256 hashes only (never the plaintext), resolves
 * presented Bearer tokens to a name + per-token {@link RestrictionsConfig}, and supports
 * creating and revoking tokens with persistence across restarts.
 *
 * <p>
 * The store is a YAML list ({@code tokens.yml}):
 *
 * <pre>
 * - name: julian
 *   token: env:MC2P_TOKEN_JULIAN   # env:VAR | file:path | plaintext, or sha256:&lt;hex&gt; for generated tokens
 *   restrictions:                  # optional per-token restrictions
 *     tools:
 *       enabled: true
 *       allowlist: [block_get]
 *   disabled: false
 * </pre>
 *
 * The {@code token} value is a secret source spec exactly like {@code auth.tokens[].token}
 * used to be; generated tokens are persisted as {@code sha256:&lt;hex&gt;} so the plaintext
 * never touches disk. A legacy runtime file ({@code name: role hex} lines plus an optional
 * {@code disabled:} list) is still loaded.
 */
public final class TokenManager {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,40}");
    private static final String HASH_PREFIX = "sha256:";

    /** Result of authenticating a presented token. */
    public record AuthResult(String name, RestrictionsConfig restrictions, String tokenId) {}

    /** Snapshot entry for status/list commands; {@code disabled} tokens do not authenticate. */
    public record TokenInfo(String name, RestrictionsConfig restrictions, String tokenId, boolean disabled) {}

    private final Path tokenFile;
    private final Path baseDir;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private static final class Entry {
        final String name;
        final String spec;
        final byte[] hash;
        final String tokenId;
        final RestrictionsConfig restrictions;
        boolean disabled;

        Entry(String name, String spec, byte[] hash, RestrictionsConfig restrictions, boolean disabled) {
            this.name = name;
            this.spec = spec;
            this.hash = hash;
            this.tokenId = Tokens.tokenId(hash);
            this.restrictions = restrictions == null ? RestrictionsConfig.DISABLED : restrictions;
            this.disabled = disabled;
        }
    }

    /**
     * @param tokenFile the {@code tokens.yml} path (may not exist yet); null disables persistence
     * @param baseDir directory relative {@code file:} token sources are resolved against
     */
    public TokenManager(Path tokenFile, Path baseDir) {
        this.tokenFile = tokenFile;
        this.baseDir = baseDir;
    }

    /** True if the name is a valid token name (letters, digits, {@code -} and {@code _}, max 40). */
    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /** Loads the token store from {@code tokens.yml}; a corrupt file yields an empty store. */
    public void load() {
        entries.clear();
        if (tokenFile == null || !Files.isRegularFile(tokenFile)) {
            return;
        }
        try {
            Object parsed = new Yaml().load(Files.newInputStream(tokenFile));
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    addEntry(ConfigSupport.map(item));
                }
            } else if (parsed instanceof Map<?, ?> map) {
                loadLegacy(ConfigSupport.map(map));
            }
        } catch (Exception e) {
            entries.clear();
        }
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
            if (!e.disabled && Tokens.constantTimeEquals(e.hash, presentedHash)) {
                return new AuthResult(e.name, e.restrictions, e.tokenId);
            }
        }
        return null;
    }

    /**
     * Creates (or replaces) the token for {@code name} without per-token restrictions and
     * returns the new plaintext exactly once.
     */
    public String create(String name) {
        return create(name, RestrictionsConfig.DISABLED);
    }

    /**
     * Creates (or replaces) the token for {@code name} with the given per-token
     * restrictions and returns the new plaintext exactly once.
     */
    public String create(String name, RestrictionsConfig restrictions) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid token name: " + name);
        }
        String token = Tokens.generateToken();
        byte[] hash = Tokens.sha256(token);
        Entry entry = new Entry(name, HASH_PREFIX + HexFormat.of().formatHex(hash), hash, restrictions, false);
        entries.put(name, entry);
        persist();
        return token;
    }

    /** Removes the token with the given name entirely. */
    public boolean revoke(String name) {
        Entry removed = entries.remove(name);
        if (removed != null) {
            persist();
        }
        return removed != null;
    }

    /** Disables the token with the given name; it stops authenticating but stays listed. */
    public boolean disable(String name) {
        Entry e = entries.get(name);
        if (e == null || e.disabled) {
            return false;
        }
        e.disabled = true;
        persist();
        return true;
    }

    /** Re-enables a previously disabled token by name. */
    public boolean enable(String name) {
        Entry e = entries.get(name);
        if (e == null || !e.disabled) {
            return false;
        }
        e.disabled = false;
        persist();
        return true;
    }

    /** Removes every token from the store. Used when the standalone/backend mode changes. */
    public void clear() {
        entries.clear();
        persist();
    }

    /** Returns a snapshot of the current tokens keyed by name (for status/list). */
    public Map<String, TokenInfo> snapshot() {
        Map<String, TokenInfo> result = new LinkedHashMap<>();
        for (Entry e : entries.values()) {
            result.put(e.name, new TokenInfo(e.name, e.restrictions, e.tokenId, e.disabled));
        }
        return result;
    }

    private void addEntry(Map<String, Object> row) {
        String name = ConfigSupport.str(row, "name", "");
        if (!isValidName(name)) {
            return;
        }
        String spec = ConfigSupport.str(row, "token", "");
        if (spec.isBlank()) {
            return;
        }
        byte[] hash = resolveHash(spec);
        if (hash == null) {
            return;
        }
        RestrictionsConfig restrictions = RestrictionsConfig.load(ConfigSupport.map(row.get("restrictions")));
        boolean disabled = ConfigSupport.bool(row, "disabled", false);
        entries.put(name, new Entry(name, spec, hash, restrictions, disabled));
    }

    private byte[] resolveHash(String spec) {
        String trimmed = spec.trim();
        if (trimmed.startsWith(HASH_PREFIX)) {
            String hex = trimmed.substring(HASH_PREFIX.length()).trim();
            try {
                return HexFormat.of().parseHex(hex);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        ConfigSupport.Secret secret = ConfigSupport.resolveSecret(trimmed, baseDir);
        return secret == null ? null : Tokens.sha256(secret.value());
    }

    /** Legacy runtime file: {@code name: [role] hex} lines plus an optional {@code disabled:} list. */
    private void loadLegacy(Map<String, Object> map) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String name = e.getKey();
            if ("disabled".equals(name) || !isValidName(name)) {
                continue;
            }
            String value = String.valueOf(e.getValue()).trim();
            String hex = value;
            int sp = value.lastIndexOf(' ');
            if (sp >= 0) {
                String last = value.substring(sp + 1).trim();
                if (last.matches("[0-9a-fA-F]+")) {
                    hex = last;
                }
            }
            try {
                byte[] hash = HexFormat.of().parseHex(hex);
                entries.put(name, new Entry(name, HASH_PREFIX + hex, hash, RestrictionsConfig.DISABLED, false));
            } catch (IllegalArgumentException ignored) {
                // corrupt entry
            }
        }
        Object disabled = map.get("disabled");
        if (disabled != null) {
            for (String n : String.valueOf(disabled).split(",")) {
                Entry entry = entries.get(n.trim());
                if (entry != null) {
                    entry.disabled = true;
                }
            }
        }
    }

    private void persist() {
        if (tokenFile == null) {
            return;
        }
        try {
            if (entries.isEmpty()) {
                Files.deleteIfExists(tokenFile);
                return;
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Entry e : entries.values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", e.name);
                row.put("token", e.spec);
                if (!e.restrictions.equals(RestrictionsConfig.DISABLED)) {
                    row.put("restrictions", e.restrictions.toMap());
                }
                if (e.disabled) {
                    row.put("disabled", true);
                }
                rows.add(row);
            }
            StringBuilder sb = new StringBuilder("# mc2p tokens - do not share this file\n");
            sb.append(new Yaml().dump(rows));
            Files.writeString(tokenFile, sb.toString());
            try {
                Files.setPosixFilePermissions(
                        tokenFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // non-POSIX filesystem
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist tokens to " + tokenFile, e);
        }
    }
}
