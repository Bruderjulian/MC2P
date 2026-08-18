package dev.mc2p.proxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.role.Role;
import java.util.List;
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
        assertEquals("env:MC2P_TOKEN_READER", namedToken(config, "reader").source());
        assertEquals(Role.READER, namedToken(config, "reader").role());
        assertEquals(5.0, config.auth().rateLimit().tokensPerSecond());
        assertEquals(20, config.auth().rateLimit().burst());
        assertEquals(5, config.auth().activityWindowMinutes());
        assertEquals("mc2p:rpc", config.rpc().channel());
        assertEquals(5000, config.rpc().timeoutMs());
        assertEquals(8, config.rpc().maxChunks());
        assertTrue(config.servers().isEmpty());
        assertEquals("logs/mcp-proxy-audit.log", config.audit().file());
    }

    @Test
    void loadsNamedTokenList() {
        Map<String, Object> yaml = Map.of(
                "auth",
                Map.of(
                        "tokens",
                        List.of(
                                Map.of("name", "alice", "role", "ops", "token", "env:MC2P_TOKEN_ALICE"),
                                Map.of("name", "ci-bot", "role", "reader", "token", "file:secrets/ci"))));

        ProxyConfig config = ProxyConfig.load(yaml);
        assertEquals(2, config.auth().tokens().size());
        assertEquals("alice", config.auth().tokens().get(0).name());
        assertEquals(Role.OPS, config.auth().tokens().get(0).role());
        assertEquals("env:MC2P_TOKEN_ALICE", config.auth().tokens().get(0).source());
        assertEquals("ci-bot", config.auth().tokens().get(1).name());
        assertEquals(Role.READER, config.auth().tokens().get(1).role());
    }

    @Test
    void loadsLegacyRoleMap() {
        Map<String, Object> yaml = Map.of("auth", Map.of("tokens", Map.of("reader", "env:MC2P_TOKEN_READER")));

        ProxyConfig config = ProxyConfig.load(yaml);
        assertEquals(1, config.auth().tokens().size());
        assertEquals("reader", config.auth().tokens().get(0).name());
        assertEquals(Role.READER, config.auth().tokens().get(0).role());
        assertEquals("env:MC2P_TOKEN_READER", config.auth().tokens().get(0).source());
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

    @Test
    void skipsInvalidTokens() {
        Map<String, Object> yaml = Map.of(
                "auth",
                Map.of(
                        "tokens",
                        List.of(
                                Map.of("name", "", "role", "ops", "token", "env:X"),
                                Map.of("name", "n1", "role", "not-a-role", "token", "env:X"),
                                Map.of("name", "n2", "role", "reader", "token", " "),
                                Map.of("name", "valid", "role", "admin", "token", "env:V"))));

        ProxyConfig config = ProxyConfig.load(yaml);
        assertEquals(1, config.auth().tokens().size());
        assertEquals("valid", config.auth().tokens().get(0).name());
        assertEquals(Role.ADMIN, config.auth().tokens().get(0).role());
    }

    @Test
    void skipsUnknownRoleKeysInLegacyMap() {
        Map<String, Object> yaml =
                Map.of("auth", Map.of("tokens", Map.of("reader", "env:R", "not-a-role", "env:X")));

        ProxyConfig config = ProxyConfig.load(yaml);
        assertEquals(1, config.auth().tokens().size());
        assertEquals("reader", config.auth().tokens().get(0).name());
    }

    private static ProxyConfig.AuthSection.NamedToken namedToken(ProxyConfig config, String name) {
        return config.auth().tokens().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
