package dev.mc2p.common.util;

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
}
