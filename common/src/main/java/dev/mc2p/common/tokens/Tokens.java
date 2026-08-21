package dev.mc2p.common.tokens;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token generation and constant-time comparison helpers.
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256-bit
    private static final String HASH_PREFIX = "sha256:";

    private Tokens() {
    }

    /**
     * Generates a fresh 256-bit random token, URL-safe base64 encoded.
     */
    public static String generateToken() {
        final byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 of the token bytes (UTF-8 of the token string).
     */
    public static byte[] sha256(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (final java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * A short, non-secret identifier derived from the token hash, used in audit
     * entries
     * so the secret itself is never logged.
     */
    public static String tokenId(final byte[] sha256) {
        return HexFormat.of().formatHex(sha256, 0, 4);
    }

    /**
     * Constant-time comparison of two byte arrays.
     */
    public static boolean constantTimeEquals(final byte[] a, final byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    /**
     * Constant-time comparison of two strings.
     */
    public static boolean constantTimeEquals(final String a, final String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public record Secret(String tokenId, byte[] hash, String raw) {

    }

    /**
     * Resolves a token/secret source spec.
     *
     * @param spec    {@code env:VAR}, {@code file:path} (0600), or plaintext
     * @param baseDir directory relative {@code file:} paths are resolved against
     * @return the resolved secret, or null if the source is missing
     */
    public static Secret resolveSecret(String str, final Path baseDir) {
        if (str == null || str.isBlank()) {
            return null;
        }
        str = str.trim();
        if (str.startsWith("env:")) {
            final String var = str.substring(4).trim();
            final String value = System.getenv(var);
            if (value == null || value.isBlank()) {
                return null;
            }
            return new Secret(value, sha256(value), str);
        }
        if (str.startsWith(HASH_PREFIX)) {
            try {
                final byte[] hex = HexFormat.of().parseHex(str.substring(HASH_PREFIX.length()));
                return new Secret(tokenId(hex), hex, str);
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }
        if (str.startsWith("file:")) {
            final String pathSpec = str.substring(5).trim();
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
                return new Secret(value, sha256(value), str);
            } catch (final IOException e) {
                return null;
            }
        }
        // Plaintext: allowed but warned by the caller.
        return new Secret(str, sha256(str), str);
    }
}
