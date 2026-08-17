package dev.mc2p.proxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ProxyConfigTest {

    @Test
    void defaultsMatchSpec() {
        ProxyConfig config = ProxyConfig.defaults();
        assertEquals("proxy-01", config.serverId());
        assertEquals(8443, config.mcp().port());
        assertEquals("/mcp", config.mcp().endpoint());
        assertEquals("selfsigned", config.mcp().tls().mode());
        assertEquals("env:MC2P_TOKEN_READER", config.auth().tokens().get("reader"));
        assertEquals(5.0, config.auth().rateLimit().tokensPerSecond());
        assertEquals(20, config.auth().rateLimit().burst());
        assertEquals("mc2p:rpc", config.rpc().channel());
        assertEquals(5000, config.rpc().timeoutMs());
        assertEquals(8, config.rpc().maxChunks());
        assertTrue(config.servers().isEmpty());
        assertEquals("logs/mcp-proxy-audit.log", config.audit().file());
    }

    @Test
    void loadsServersMapAndOverrides() {
        Map<String, Object> yaml = Map.of(
                "serverId", "edge-01",
                "servers", Map.of("lobby", "main-01", "survival", "survival-02"),
                "rpc", Map.of("timeout-ms", 8000),
                "mcp", Map.of("port", 9000, "tls", Map.of("mode", "keystore")));

        ProxyConfig config = ProxyConfig.load(yaml);
        assertEquals("edge-01", config.serverId());
        assertEquals(9000, config.mcp().port());
        assertEquals("keystore", config.mcp().tls().mode());
        assertEquals("main-01", config.servers().get("lobby"));
        assertEquals("survival-02", config.servers().get("survival"));
        assertEquals(8000, config.rpc().timeoutMs());
    }
}
