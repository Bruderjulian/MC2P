package dev.mc2p.proxy.config;

import java.util.List;
import java.util.Map;

import dev.mc2p.common.config.ConfigSupport;
import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;

/** Typed proxy plugin configuration (Section 5.2 of the spec). */
public record ProxyConfig(
        String serverId,
        McpSection mcp,
        AuthSection auth,
        Map<String, String> servers,
        RpcSection rpc,
        AuditSection audit) {

    public record McpSection(String bind, int port, String endpoint, TlsSection tls, int bodyLimitBytes) {

        public record TlsSection(String mode, String keystore, String passwordEnv) {
        }
    }

    public record AuthSection(Map<String, String> tokens, List<String> ipAllowlist, TokenBucketRateLimiter.Config rateLimit) {
    }

    public record RpcSection(String secretEnv, String channel, long timeoutMs, int maxChunks) {
    }

    public record AuditSection(String file, int maxMb, int maxFiles) {
    }

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
        Map<String, Object> tokens = ConfigSupport.map(auth.get("tokens"));
        Map<String, Object> rate = ConfigSupport.map(auth.get("rate-limit"));
        AuthSection authSection = new AuthSection(
                Map.of(
                        "reader", ConfigSupport.str(tokens, "reader", "env:MC2P_TOKEN_READER"),
                        "ops", ConfigSupport.str(tokens, "ops", "env:MC2P_TOKEN_OPS"),
                        "admin", ConfigSupport.str(tokens, "admin", "env:MC2P_TOKEN_ADMIN")),
                ConfigSupport.strings(auth, "ip-allowlist"),
                new TokenBucketRateLimiter.Config(
                        (double) ConfigSupport.integer(rate, "tokens-per-second", 5),
                        ConfigSupport.integer(rate, "burst", 20)));

        Map<String, String> servers = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : ConfigSupport.map(yaml.get("servers")).entrySet()) {
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
}