package dev.mc2p.plugin.tools;

import dev.mc2p.common.config.RestrictionsConfig;

/** Request identity resolved by the transport layer (HTTP bearer token or RPC envelope). */
public record AuthContext(
        RestrictionsConfig restrictions, String name, String tokenId, String remoteIp, String source) {

    public static AuthContext of(
            RestrictionsConfig restrictions, String name, String tokenId, String remoteIp, String source) {
        return new AuthContext(restrictions, name, tokenId, remoteIp, source);
    }

    public static AuthContext unauthenticated() {
        return new AuthContext(null, "", "", "", "");
    }
}
