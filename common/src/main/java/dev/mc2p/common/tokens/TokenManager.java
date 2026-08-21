package dev.mc2p.common.tokens;

import dev.mc2p.common.config.ConfigSupport;
import dev.mc2p.common.config.RestrictionsConfig;
import dev.mc2p.common.validate.Args;
import dev.mc2p.common.validate.Utils;

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
 * Stores named client tokens as SHA-256 hashes only (never the plaintext),
 * resolves presented Bearer tokens to a name + per-token
 * {@link RestrictionsConfig}, and supports creating and revoking tokens with
 * persistence across restarts.
 *
 * <p>
 * The store is a YAML list ({@code tokens.yml}):
 *
 * <pre>
 * - name: test
 *   token: env:MC2P_TOKEN_TEST   # env:VAR | file:path | plaintext, or sha256:&lt;hex&gt; for generated tokens
 *   disabled: false
 *   restrictions:                # optional per-token restrictions
 *     tools:
 *       enabled: true
 *       allowlist: [block_get]
 * </pre>
 *
 * The {@code token} value is a secret source spec exactly like
 * {@code auth.tokens[].token} used to be;
 * generated tokens are persisted as {@code sha256:&lt;hex&gt;} so the plaintext
 * never touches disk. A legacy runtime file ({@code name: role hex} lines plus
 * an optional {@code disabled:} list) is still loaded.
 */
public final class TokenManager {

  public static final class Token {
    private final String name;
    private final String raw;
    private final byte[] hash;
    private final String tokenId;
    private final RestrictionsConfig restrictions;
    private boolean disabled;

    Token(final String name, String tokenId, final String raw, final byte[] hash, final RestrictionsConfig restrictions,
        final boolean disabled) {
      this.name = name;
      this.hash = hash;
      this.raw = raw == null ? HASH_PREFIX + HexFormat.of().formatHex(hash) : raw;
      this.tokenId = tokenId;
      this.restrictions = restrictions == null ? RestrictionsConfig.DISABLED : restrictions;
      this.disabled = disabled;
    }

    Token(final String name, final String tokenId, final RestrictionsConfig restrictions,
        final boolean disabled) {
      this.name = name;
      this.hash = Utils.sha256(tokenId);
      this.raw = HASH_PREFIX + HexFormat.of().formatHex(this.hash);
      this.tokenId = tokenId;
      this.restrictions = restrictions == null ? RestrictionsConfig.DISABLED : restrictions;
      this.disabled = disabled;
    }

    public String name() {
      return name;
    }

    public String raw() {
      return raw;
    }

    public byte[] hash() {
      return hash;
    }

    public String tokenId() {
      return tokenId;
    }

    public RestrictionsConfig restrictions() {
      return restrictions;
    }

    public boolean disabled() {
      return disabled;
    }

  }

  private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,40}");
  private static final String HASH_PREFIX = "sha256:";

  /**
   * True if the name is a valid token name (letters, digits, {@code -} and
   * {@code _}, max 40).
   */
  public static boolean isValidName(final String name) {
    return name != null && NAME_PATTERN.matcher(name).matches();
  }

  private final Path tokenFile;
  private final Path baseDir;
  private final Map<String, Token> tokens = new LinkedHashMap<>();

  /**
   * @param tokenFile the {@code tokens.yml} path (may not exist yet); null
   *                  disables persistence
   * @param baseDir   directory relative {@code file:} token sources are resolved
   *                  against
   */
  public TokenManager(final Path tokenFile, final Path baseDir) {
    this.tokenFile = tokenFile;
    this.baseDir = baseDir;
  }

  /**
   * Loads the token store from {@code tokens.yml}; a corrupt file yields an empty
   * store.
   */
  public void load() {
    tokens.clear();
    if (tokenFile == null || !Files.isRegularFile(tokenFile)) {
      return;
    }
    try {
      final List<?> parsed = ConfigSupport.loadYaml(tokenFile, List.class, null);
      if (parsed == null) {
        return;
      }
      for (final Object item : parsed) {
        addEntry(Args.map(item));
      }
    } catch (final Exception e) {
      tokens.clear();
    }
  }

  /**
   * Authenticates a presented token. Constant-time, never logs the secret.
   */
  public Token authenticate(final String presented) {
    if (presented == null || presented.isBlank()) {
      return null;
    }
    final byte[] presentedHash = Utils.sha256(presented);
    for (final Token token : tokens.values()) {
      if (!token.disabled && Utils.constantTimeEquals(token.hash, presentedHash)) {
        return token;
      }
    }
    return null;
  }

  /**
   * Creates (or replaces) the token for {@code name} without per-token
   * restrictions and
   * returns the new plaintext exactly once.
   */
  public Token create(final String name) {
    return create(name, RestrictionsConfig.DISABLED);
  }

  /**
   * Creates (or replaces) the token for {@code name} with the given per-token
   * restrictions and returns the new plaintext exactly once.
   */
  public Token create(final String name, final RestrictionsConfig restrictions) {
    if (!isValidName(name)) {
      throw new IllegalArgumentException("invalid token name: " + name);
    }
    final Token token = new Token(name, Utils.generateToken(), restrictions, false);
    tokens.put(name, token);
    persist();
    return token;
  }

  /** Removes the token with the given name entirely. */
  public boolean revoke(final String name) {
    final Token removed = tokens.remove(name);
    if (removed != null) {
      persist();
    }
    return removed != null;
  }

  /**
   * Disables the token with the given name; it stops authenticating but stays
   * listed.
   */
  public boolean disable(final String name) {
    final Token token = tokens.get(name);
    if (token == null || token.disabled) {
      return false;
    }
    token.disabled = true;
    persist();
    return true;
  }

  /** Re-enables a previously disabled token by name. */
  public boolean enable(final String name) {
    final Token token = tokens.get(name);
    if (token == null || !token.disabled) {
      return false;
    }
    token.disabled = false;
    persist();
    return true;
  }

  /**
   * Removes every token from the store. Used when the standalone/backend mode
   * changes.
   */
  public void clear() {
    tokens.clear();
    persist();
  }

  /** Returns a snapshot of the current tokens keyed by name (for status/list). */
  public Map<String, Token> snapshot() {
    return Map.copyOf(tokens);
  }

  public List<Token> snapshotTokens() {
    return List.copyOf(tokens.values());
  }

  private void addEntry(final Map<String, Object> row) {
    final String name = Args.string(row, "name", "");
    if (!isValidName(name)) {
      return;
    }
    final ConfigSupport.Secret secret = ConfigSupport.resolveSecret(Args.string(row, "token", ""), baseDir);
    if (secret == null) {
      return;
    }
    final RestrictionsConfig restrictions = RestrictionsConfig.load(Args.map(row.get("restrictions")));
    final boolean disabled = Args.bool(row, "disabled", false);
    tokens.put(name, new Token(name, secret.tokenId(), secret.raw(), secret.hash(), restrictions, disabled));
  }

  private void persist() {
    if (tokenFile == null) {
      return;
    }
    try {
      if (tokens.isEmpty()) {
        Files.deleteIfExists(tokenFile);
        return;
      }
      final List<Map<String, Object>> rows = new ArrayList<>();
      for (final Token token : tokens.values()) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", token.name);
        row.put("token", token.raw);
        if (!token.restrictions.equals(RestrictionsConfig.DISABLED)) {
          row.put("restrictions", token.restrictions.toMap());
        }
        if (token.disabled) {
          row.put("disabled", true);
        }
        rows.add(row);
      }
      final StringBuilder sb = new StringBuilder("# mc2p tokens - do not share this file\n");
      sb.append(new Yaml().dump(rows));
      Files.writeString(tokenFile, sb.toString());
      try {
        Files.setPosixFilePermissions(
            tokenFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      } catch (final UnsupportedOperationException ignored) {
        // non-POSIX filesystem
      }
    } catch (final Exception ex) {
      throw new IllegalStateException("Failed to persist tokens to " + tokenFile, ex);
    }
  }
}
