package dev.mc2p.proxy.tools;

import dev.mc2p.common.role.Role;

/** Caller identity for proxy tool handlers, derived from the MCP transport context. */
public record AuthContext(Role role, String tokenId, String remoteIp, String source) {

    public static AuthContext unauthenticated() {
        return new AuthContext(null, "", "", "none");
    }
}
