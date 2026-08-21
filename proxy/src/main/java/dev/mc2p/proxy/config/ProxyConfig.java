package dev.mc2p.proxy.config;

import dev.mc2p.common.config.RestrictionsConfig;
import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;
import dev.mc2p.common.validate.Args;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed proxy plugin configuration (Velocity). */
public record ProxyConfig(
                String serverId,
                McpSection mcp,
                AuthSection auth,
                Map<String, String> servers,
                RpcSection rpc,
                AuditSection audit,
                RestrictionsConfig globalRestrictions) {

        public record McpSection(String bind, int port, String endpoint, TlsSection tls, int bodyLimitBytes) {

                public record TlsSection(String mode, String keystore, String passwordEnv) {
                }
        }

        public record AuthSection(
                        List<String> ipAllowlist, TokenBucketRateLimiter.Config rateLimit, int activityWindowMinutes) {
        }

        public record RpcSection(String secretEnv, String channel, long timeoutMs, int maxChunks) {
        }

        public record AuditSection(String file, int maxMb, int maxFiles) {
        }

        public static ProxyConfig defaults() {
                return load(Map.of());
        }

        public static ProxyConfig load(final Map<String, Object> yaml) {
                final String serverId = Args.string(yaml, "serverId", "proxy-01");

                final Map<String, Object> mcp = Args.map(yaml.get("mcp"));
                final Map<String, Object> tls = Args.map(mcp.get("tls"));
                final McpSection mcpSection = new McpSection(
                                Args.string(mcp, "bind", "0.0.0.0"),
                                Args.integer(mcp, "port", 8443),
                                Args.string(mcp, "endpoint", "/mcp"),
                                new McpSection.TlsSection(
                                                Args.string(tls, "mode", "selfsigned"),
                                                Args.string(tls, "keystore", "keystore.p12"),
                                                Args.string(tls, "password-env", "MC2P_KEYSTORE_PW")),
                                Args.integer(mcp, "body-limit-bytes", 65536));

                final Map<String, Object> auth = Args.map(yaml.get("auth"));
                final Map<String, Object> rate = Args.map(auth.get("rate-limit"));
                final AuthSection authSection = new AuthSection(
                                Args.strings(auth, "ip-allowlist"),
                                new TokenBucketRateLimiter.Config(
                                                (double) Args.integer(rate, "tokens-per-second", 5),
                                                Args.integer(rate, "burst", 20)),
                                Args.integer(auth, "activity-window-minutes", 5));

                final Map<String, String> servers = new LinkedHashMap<>();
                for (final Map.Entry<String, Object> e : Args.map(yaml.get("servers")).entrySet()) {
                        servers.put(e.getKey(), String.valueOf(e.getValue()));
                }

                final Map<String, Object> rpc = Args.map(yaml.get("rpc"));
                final RpcSection rpcSection = new RpcSection(
                                Args.string(rpc, "secret-env", "MC2P_PROXY_SECRET"),
                                Args.string(rpc, "channel", "mc2p:rpc"),
                                Args.integer(rpc, "timeout-ms", 5000),
                                Args.integer(rpc, "max-chunks", 8));

                final Map<String, Object> audit = Args.map(yaml.get("audit"));
                final AuditSection auditSection = new AuditSection(
                                Args.string(audit, "file", "logs/mcp-proxy-audit.log"),
                                Args.integer(audit, "max-mb", 50),
                                Args.integer(audit, "max-files", 5));

                final RestrictionsConfig global = RestrictionsConfig
                                .load(Args.map(yaml.get("global-restrictions")));

                return new ProxyConfig(serverId, mcpSection, authSection, servers, rpcSection, auditSection, global);
        }
}
