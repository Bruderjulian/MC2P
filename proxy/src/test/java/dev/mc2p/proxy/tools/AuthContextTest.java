package dev.mc2p.proxy.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AuthContextTest {

    @Test
    void unauthenticatedIsEmpty() {
        AuthContext context = AuthContext.unauthenticated();
        assertNull(context.restrictions());
        assertEquals("", context.name());
        assertEquals("", context.tokenId());
        assertEquals("", context.remoteIp());
        assertEquals("none", context.source());
    }
}
