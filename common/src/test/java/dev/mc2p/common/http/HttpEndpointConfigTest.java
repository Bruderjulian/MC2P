package dev.mc2p.common.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HttpEndpointConfigTest {

    @Test
    void recordCarriesTransportShape() {
        HttpEndpointConfig config =
                new HttpEndpointConfig("0.0.0.0", 8443, "/mcp", 1_000_000, "selfsigned", "keystore.p12", "MC2P_PASS");
        assertEquals("0.0.0.0", config.bind());
        assertEquals(8443, config.port());
        assertEquals("/mcp", config.endpoint());
        assertEquals(1_000_000, config.bodyLimitBytes());
        assertEquals("selfsigned", config.tlsMode());
        assertEquals("keystore.p12", config.keystore());
        assertEquals("MC2P_PASS", config.passwordEnv());
        assertEquals(new HttpEndpointConfig("0.0.0.0", 8443, "/mcp", 1_000_000, "selfsigned", "keystore.p12", "MC2P_PASS"), config);
    }
}