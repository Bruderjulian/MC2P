package dev.mc2p.common.rpc;

import dev.mc2p.common.config.RestrictionsConfig;

/** Request identity resolved by the transport layer (HTTP bearer token or RPC envelope). */
public record AuthContext(
        RestrictionsConfig restrictions, String name, String tokenId, String remoteIp, String source) {

    public static AuthContext of(
            final RestrictionsConfig restrictions, final String name, final String tokenId, final String remoteIp, final String source) {
        return new AuthContext(restrictions, name, tokenId, remoteIp, source);
    }

    public static AuthContext unauthenticated() {
        return new AuthContext(null, "", "", "", "");
    }
}
