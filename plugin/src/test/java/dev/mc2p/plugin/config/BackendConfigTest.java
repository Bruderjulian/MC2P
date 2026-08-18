package dev.mc2p.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BackendConfigTest {

    @Test
    void defaultsMatchSpec() {
        BackendConfig config = BackendConfig.defaults();
        assertEquals("auto", config.mode());
        assertEquals("main", config.serverId());
        assertEquals(8443, config.mcp().port());
        assertEquals("/mcp", config.mcp().endpoint());
        assertEquals("selfsigned", config.mcp().tls().mode());
        assertEquals("MC2P_PROXY_SECRET", config.proxy().secretEnv());
        assertEquals(5.0, config.auth().rateLimit().tokensPerSecond());
        assertEquals(20, config.auth().rateLimit().burst());
        assertEquals(5, config.auth().activityWindowMinutes());
        assertEquals(30000000, config.limits().maxCoordinate());
        assertEquals(12, config.limits().maxConcurrentRequests());
        assertEquals(-1, config.limits().maxCommandLength());
        assertEquals("auto", config.restartStrategy());
        assertEquals("logs/mcp-audit.log", config.audit().file());
        assertFalse(config.globalRestrictions().enabled());
        assertFalse(config.serverRestrictions().enabled());
        assertFalse(config.effectiveRestrictions().enabled());
    }

    @Test
    void loadsOverrides() {
        Map<String, Object> yaml = Map.of(
                "mode", "backend",
                "serverId", "survival-2",
                "auth", Map.of("activity-window-minutes", 15),
                "restart", Map.of("strategy", "host-restart"),
                "limits", Map.of("max-concurrent-requests", 4),
                "mcp", Map.of("bind", "127.0.0.1", "port", 9443),
                "audit", Map.of("file", "custom-audit.log", "max-mb", 10, "max-files", 3));

        BackendConfig config = BackendConfig.load(yaml);
        assertEquals("backend", config.mode());
        assertEquals("survival-2", config.serverId());
        assertEquals("host-restart", config.restartStrategy());
        assertEquals(15, config.auth().activityWindowMinutes());
        assertEquals(4, config.limits().maxConcurrentRequests());
        assertEquals(9443, config.mcp().port());
        assertEquals("custom-audit.log", config.audit().file());
    }

    @Test
    void loadsGlobalAndServerRestrictions() {
        Map<String, Object> yaml = Map.of(
                "global-restrictions",
                Map.of("enabled", true, "tools", Map.of("enabled", true, "denylist", List.of("server_stop"))),
                "server-restrictions",
                Map.of("enabled", true, "tools", Map.of("enabled", true, "allowlist", List.of("player_info"))));

        BackendConfig config = BackendConfig.load(yaml);
        assertTrue(config.globalRestrictions().enabled());
        assertTrue(config.globalRestrictions().tools().denylist().contains("server_stop"));
        assertTrue(config.serverRestrictions().tools().allowlist().contains("player_info"));
    }

    @Test
    void effectiveRestrictionsMergeIsMostRestrictive() {
        Map<String, Object> yaml = Map.of(
                "global-restrictions",
                Map.of(
                        "enabled", true,
                        "tools", Map.of("enabled", true, "allowlist", List.of("player_info", "block_get")),
                        "commands", Map.of("enabled", true, "denylist", List.of("op"))),
                "server-restrictions",
                Map.of("enabled", true, "tools", Map.of("enabled", true, "allowlist", List.of("player_info"))));

        BackendConfig config = BackendConfig.load(yaml);
        var effective = config.effectiveRestrictions();
        assertTrue(effective.isToolAllowed("player_info"));
        assertFalse(effective.isToolAllowed("block_get"));
        assertFalse(effective.isCommandAllowed("op"));
    }
}
