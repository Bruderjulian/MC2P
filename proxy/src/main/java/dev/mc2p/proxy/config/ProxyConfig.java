package dev.mc2p.proxy.config;

import dev.mc2p.common.config.ConfigSupport;
import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;
import dev.mc2p.common.role.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typed proxy plugin configuration (Section 5.2 of the spec). */
public record ProxyConfig(
        String serverId,
        McpSection mcp,
        AuthSection auth,
        Map<String, String> servers,
        RpcSection rpc,
        AuditSection audit) {

    public record McpSection(String bind, int port, String endpoint, TlsSection tls, int bodyLimitBytes) {

        public record TlsSection(String mode, String keystore, String passwordEnv) {}
    }

    public record AuthSection(
            List<NamedToken> tokens,
            List<String> ipAllowlist,
            TokenBucketRateLimiter.Config rateLimit,
            int activityWindowMinutes) {

        /** A configured token: the admin-assigned name, its role, and the secret source. */
        public record NamedToken(String name, Role role, String source) {}
    }

    public record RpcSection(String secretEnv, String channel, long timeoutMs, int maxChunks) {}

    public record AuditSection(String file, int maxMb, int maxFiles) {}

    public static ProxyConfig defaults() {
        return load(Map.of());
    }

    public static ProxyConfig load(Map<String, Object> yaml) {
        String serverId = ConfigSupport.str(yaml, "serverId", "proxy-01");

        Map<String, Object> mcp = ConfigSupport.map(yaml.get("mcp"));
        Map<String, Object> tls = ConfigSupport.map(mcp.get("tls"));
        McpSection mcpSection = new McpSection(
                ConfigSupport.str(mcp, "bind", "0.0.0.0"),
                ConfigSupport.integer(mcp, "port", 8443),
                ConfigSupport.str(mcp, "endpoint", "/mcp"),
                new McpSection.TlsSection(
                        ConfigSupport.str(tls, "mode", "selfsigned"),
                        ConfigSupport.str(tls, "keystore", "keystore.p12"),
                        ConfigSupport.str(tls, "password-env", "MC2P_KEYSTORE_PW")),
                ConfigSupport.integer(mcp, "body-limit-bytes", 65536));

        Map<String, Object> auth = ConfigSupport.map(yaml.get("auth"));
        Map<String, Object> rate = ConfigSupport.map(auth.get("rate-limit"));
        AuthSection authSection = new AuthSection(
                parseTokens(auth.get("tokens")),
                ConfigSupport.strings(auth, "ip-allowlist"),
                new TokenBucketRateLimiter.Config(
                        (double) ConfigSupport.integer(rate, "tokens-per-second", 5),
                        ConfigSupport.integer(rate, "burst", 20)),
                ConfigSupport.integer(auth, "activity-window-minutes", 5));

        Map<String, String> servers = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e :
                ConfigSupport.map(yaml.get("servers")).entrySet()) {
            servers.put(e.getKey(), String.valueOf(e.getValue()));
        }

        Map<String, Object> rpc = ConfigSupport.map(yaml.get("rpc"));
        RpcSection rpcSection = new RpcSection(
                ConfigSupport.str(rpc, "secret-env", "MC2P_PROXY_SECRET"),
                ConfigSupport.str(rpc, "channel", "mc2p:rpc"),
                ConfigSupport.integer(rpc, "timeout-ms", 5000),
                ConfigSupport.integer(rpc, "max-chunks", 8));

        Map<String, Object> audit = ConfigSupport.map(yaml.get("audit"));
        AuditSection auditSection = new AuditSection(
                ConfigSupport.str(audit, "file", "logs/mcp-proxy-audit.log"),
                ConfigSupport.integer(audit, "max-mb", 50),
                ConfigSupport.integer(audit, "max-files", 5));

        return new ProxyConfig(serverId, mcpSection, authSection, servers, rpcSection, auditSection);
    }

    /**
     * Parses {@code auth.tokens}: a list of named tokens ({@code name}/{@code role}/
     * {@code token}), a legacy role→source map, or defaults (reader/ops/admin from env)
     * when the key is absent.
     */
    private static List<AuthSection.NamedToken> parseTokens(Object raw) {
        if (raw instanceof List<?> list) {
            List<AuthSection.NamedToken> result = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> token = ConfigSupport.map(item);
                String name = ConfigSupport.str(token, "name", "");
                Role role = Role.fromString(ConfigSupport.str(token, "role", ""));
                String source = ConfigSupport.str(token, "token", "");
                if (name.isBlank() || role == null || source.isBlank()) {
                    continue;
                }
                result.add(new AuthSection.NamedToken(name, role, source));
            }
            return result;
        }
        Map<String, Object> tokens = ConfigSupport.map(raw);
        if (tokens.isEmpty()) {
            return List.of(
                    new AuthSection.NamedToken("reader", Role.READER, "env:MC2P_TOKEN_READER"),
                    new AuthSection.NamedToken("ops", Role.OPS, "env:MC2P_TOKEN_OPS"),
                    new AuthSection.NamedToken("admin", Role.ADMIN, "env:MC2P_TOKEN_ADMIN"));
        }
        List<AuthSection.NamedToken> result = new ArrayList<>();
        for (Map.Entry<String, Object> e : tokens.entrySet()) {
            Role role = Role.fromString(e.getKey());
            if (role == null) {
                continue;
            }
            result.add(new AuthSection.NamedToken(e.getKey(), role, String.valueOf(e.getValue())));
        }
        return result;
    }
}
