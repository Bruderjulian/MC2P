package dev.mc2p.proxy.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DnsRebindingValidatorTest {

    private final DnsRebindingValidator validator = new DnsRebindingValidator();

    @Test
    void noOriginAllowed() {
        assertDoesNotThrow(() -> validator.validateHeaders(Map.of("Host", List.of("localhost"))));
    }

    @Test
    void blankOriginAllowed() {
        assertDoesNotThrow(() -> validator.validateHeaders(Map.of("Origin", List.of(" "), "Host", List.of("localhost"))));
    }

    @Test
    void missingHostRejected() {
        assertThrows(
                ServerTransportSecurityException.class,
                () -> validator.validateHeaders(Map.of("Origin", List.of("http://localhost"))));
    }

    @Test
    void blankHostRejected() {
        assertThrows(
                ServerTransportSecurityException.class,
                () -> validator.validateHeaders(
                        Map.of("Origin", List.of("http://localhost"), "Host", List.of(" "))));
    }

    @Test
    void originWithoutHostnameRejected() {
        assertThrows(
                ServerTransportSecurityException.class,
                () -> validator.validateHeaders(Map.of("Origin", List.of("http://"), "Host", List.of("localhost"))));
    }

    @Test
    void malformedOriginRejected() {
        assertThrows(
                ServerTransportSecurityException.class,
                () -> validator.validateHeaders(Map.of("Origin", List.of("http://["), "Host", List.of("localhost"))));
    }

    @Test
    void crossOriginRejected() {
        assertThrows(
                ServerTransportSecurityException.class,
                () -> validator.validateHeaders(
                        Map.of("Origin", List.of("http://evil.com"), "Host", List.of("localhost"))));
    }

    @Test
    void sameOriginWithDifferentCaseAndPortAllowed() {
        assertDoesNotThrow(() -> validator.validateHeaders(Map.of(
                "Origin", List.of("http://Example.COM:8080"),
                "Host", List.of("example.com:8080"))));
    }

    @Test
    void headersAreMatchedCaseInsensitively() {
        assertDoesNotThrow(() -> validator.validateHeaders(Map.of(
                "origin", List.of("http://localhost"),
                "host", List.of("localhost"))));
    }
}