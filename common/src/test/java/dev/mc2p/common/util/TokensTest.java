package dev.mc2p.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void generateTokenIsUrlSafeBase64Of32Bytes() {
        String token = Tokens.generateToken();
        assertEquals(43, token.length());
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertEquals(32, decoded.length);
    }

    @Test
    void generateTokenIsRandom() {
        Set<String> tokens =
                IntStream.range(0, 100).mapToObj(i -> Tokens.generateToken()).collect(Collectors.toSet());
        assertEquals(100, tokens.size());
    }

    @Test
    void sha256IsDeterministicAndCorrect() {
        String expected = HexFormat.of().formatHex(sha256Reference("hello"));
        assertEquals(expected, HexFormat.of().formatHex(Tokens.sha256("hello")));
        assertEquals(32, Tokens.sha256("hello").length);
    }

    private static byte[] sha256Reference(String input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void tokenIdIsFirstFourHashBytes() {
        byte[] hash = Tokens.sha256("hello");
        assertEquals(HexFormat.of().formatHex(hash, 0, 4), Tokens.tokenId(hash));
        assertEquals(8, Tokens.tokenId(hash).length());
    }

    @Test
    void constantTimeEqualsByteArrays() {
        byte[] a = {1, 2, 3};
        byte[] b = {1, 2, 3};
        byte[] c = {1, 2, 4};
        assertTrue(Tokens.constantTimeEquals(a, b));
        assertFalse(Tokens.constantTimeEquals(a, c));
    }

    @Test
    void constantTimeEqualsStrings() {
        assertTrue(Tokens.constantTimeEquals("secret", "secret"));
        assertFalse(Tokens.constantTimeEquals("secret", "secret2"));
        assertFalse(Tokens.constantTimeEquals(null, "secret"));
        assertFalse(Tokens.constantTimeEquals("secret", null));
        assertFalse(Tokens.constantTimeEquals((String) null, (String) null));
    }

    @Test
    void helpersNeverReturnNull() {
        assertNotNull(Tokens.generateToken());
        assertNotNull(Tokens.sha256(""));
        assertNotNull(Tokens.tokenId(Tokens.sha256("")));
    }
}
