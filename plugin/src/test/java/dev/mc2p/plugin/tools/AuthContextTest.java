package dev.mc2p.plugin.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.mc2p.common.role.Role;
import org.junit.jupiter.api.Test;

class AuthContextTest {

    @Test
    void ofPreservesAllFields() {
        AuthContext context = AuthContext.of(Role.ADMIN, "alice", "t1", "127.0.0.1", "http");
        assertEquals(Role.ADMIN, context.role());
        assertEquals("alice", context.name());
        assertEquals("t1", context.tokenId());
        assertEquals("127.0.0.1", context.remoteIp());
        assertEquals("http", context.source());
    }

    @Test
    void unauthenticatedIsEmpty() {
        AuthContext context = AuthContext.unauthenticated();
        assertNull(context.role());
        assertEquals("", context.name());
        assertEquals("", context.tokenId());
        assertEquals("", context.remoteIp());
        assertEquals("", context.source());
    }
}