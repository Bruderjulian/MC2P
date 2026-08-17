package dev.mc2p.plugin.tools;

import dev.mc2p.common.role.Role;

/** Request identity resolved by the transport layer (HTTP bearer token or RPC envelope). */
public record AuthContext(Role role, String name, String tokenId, String remoteIp, String source) {

    public static AuthContext of(Role role, String name, String tokenId, String remoteIp, String source) {
        return new AuthContext(role, name, tokenId, remoteIp, source);
    }

    public static AuthContext unauthenticated() {
        return new AuthContext(null, "", "", "", "");
    }
}
