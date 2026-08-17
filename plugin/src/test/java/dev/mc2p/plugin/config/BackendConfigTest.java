package dev.mc2p.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.role.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BackendConfigTest {

    @Test
    void defaultsMatchSpec() {
        BackendConfig config = BackendConfig.defaults();
        assertEquals("auto", config.mode());
        assertEquals("main-01", config.serverId());
        assertEquals(8443, config.mcp().port());
        assertEquals("/mcp", config.mcp().endpoint());
        assertEquals("selfsigned", config.mcp().tls().mode());
        assertEquals("env:MC2P_TOKEN_READER", namedToken(config, "reader").source());
        assertEquals(Role.READER, namedToken(config, "reader").role());
        assertEquals(5.0, config.auth().rateLimit().tokensPerSecond());
        assertEquals(20, config.auth().rateLimit().burst());
        assertEquals(5, config.auth().activityWindowMinutes());
        assertEquals(
                List.of("gamemode", "tp", "teleport", "weather", "time", "effect", "clear"),
                config.commands().opsAllowlist());
        assertEquals(List.of("*"), config.commands().adminAllowlist());
        assertEquals(
                List.of("stop", "restart", "save-off", "save-all", "kick-all", "op"),
                config.commands().deny());
        assertEquals(30000000, config.limits().maxCoordinate());
        assertEquals("auto", config.restartStrategy());
        assertFalse(config.features().blockEdit());
        assertTrue(config.features().stats());
    }

    @Test
    void loadsOverrides() {
        Map<String, Object> yaml = Map.of(
                "mode", "backend",
                "serverId", "survival-2",
                "auth", Map.of("activity-window-minutes", 15),
                "features", Map.of("blockEdit", true, "stats", false),
                "restart", Map.of("strategy", "host-restart"),
                "commands",
                        Map.of(
                                "ops-allowlist", List.of("gamemode"),
                                "admin-allowlist", List.of("*"),
                                "deny", List.of("stop", "op")));

        BackendConfig config = BackendConfig.load(yaml);
        assertEquals("backend", config.mode());
        assertEquals("survival-2", config.serverId());
        assertEquals("host-restart", config.restartStrategy());
        assertEquals(15, config.auth().activityWindowMinutes());
        assertTrue(config.features().blockEdit());
        assertFalse(config.features().stats());
        assertEquals(List.of("gamemode"), config.commands().opsAllowlist());
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

        BackendConfig config = BackendConfig.load(yaml);
        assertEquals(2, config.auth().tokens().size());
        assertEquals("alice", config.auth().tokens().get(0).name());
        assertEquals(Role.OPS, config.auth().tokens().get(0).role());
        assertEquals("env:MC2P_TOKEN_ALICE", config.auth().tokens().get(0).source());
        assertEquals("ci-bot", config.auth().tokens().get(1).name());
        assertEquals(Role.READER, config.auth().tokens().get(1).role());
    }

    private static BackendConfig.AuthSection.NamedToken namedToken(BackendConfig config, String name) {
        return config.auth().tokens().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
