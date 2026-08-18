package dev.mc2p.plugin.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.config.RestrictionsConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthContextTest {

    @Test
    void ofPreservesAllFields() {
        RestrictionsConfig restrictions =
                RestrictionsConfig.load(Map.of("tools", Map.of("enabled", true, "allowlist", List.of("player_info"))));
        AuthContext context = AuthContext.of(restrictions, "alice", "t1", "127.0.0.1", "http");
        assertTrue(context.restrictions().isToolAllowed("player_info"));
        assertEquals("alice", context.name());
        assertEquals("t1", context.tokenId());
        assertEquals("127.0.0.1", context.remoteIp());
        assertEquals("http", context.source());
    }

    @Test
    void unauthenticatedIsEmpty() {
        AuthContext context = AuthContext.unauthenticated();
        assertNull(context.restrictions());
        assertEquals("", context.name());
        assertEquals("", context.tokenId());
        assertEquals("", context.remoteIp());
        assertEquals("", context.source());
    }
}
