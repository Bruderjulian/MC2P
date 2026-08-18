package dev.mc2p.proxy.config;

import dev.mc2p.common.config.ConfigSupport;
import dev.mc2p.common.config.RestrictionsConfig;
import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;
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
                final String serverId = ConfigSupport.str(yaml, "serverId", "proxy-01");

                final Map<String, Object> mcp = ConfigSupport.map(yaml.get("mcp"));
                final Map<String, Object> tls = ConfigSupport.map(mcp.get("tls"));
                final McpSection mcpSection = new McpSection(
                                ConfigSupport.str(mcp, "bind", "0.0.0.0"),
                                ConfigSupport.integer(mcp, "port", 8443),
                                ConfigSupport.str(mcp, "endpoint", "/mcp"),
                                new McpSection.TlsSection(
                                                ConfigSupport.str(tls, "mode", "selfsigned"),
                                                ConfigSupport.str(tls, "keystore", "keystore.p12"),
                                                ConfigSupport.str(tls, "password-env", "MC2P_KEYSTORE_PW")),
                                ConfigSupport.integer(mcp, "body-limit-bytes", 65536));

                final Map<String, Object> auth = ConfigSupport.map(yaml.get("auth"));
                final Map<String, Object> rate = ConfigSupport.map(auth.get("rate-limit"));
                final AuthSection authSection = new AuthSection(
                                ConfigSupport.strings(auth, "ip-allowlist"),
                                new TokenBucketRateLimiter.Config(
                                                (double) ConfigSupport.integer(rate, "tokens-per-second", 5),
                                                ConfigSupport.integer(rate, "burst", 20)),
                                ConfigSupport.integer(auth, "activity-window-minutes", 5));

                final Map<String, String> servers = new LinkedHashMap<>();
                for (final Map.Entry<String, Object> e : ConfigSupport.map(yaml.get("servers")).entrySet()) {
                        servers.put(e.getKey(), String.valueOf(e.getValue()));
                }

                final Map<String, Object> rpc = ConfigSupport.map(yaml.get("rpc"));
                final RpcSection rpcSection = new RpcSection(
                                ConfigSupport.str(rpc, "secret-env", "MC2P_PROXY_SECRET"),
                                ConfigSupport.str(rpc, "channel", "mc2p:rpc"),
                                ConfigSupport.integer(rpc, "timeout-ms", 5000),
                                ConfigSupport.integer(rpc, "max-chunks", 8));

                final Map<String, Object> audit = ConfigSupport.map(yaml.get("audit"));
                final AuditSection auditSection = new AuditSection(
                                ConfigSupport.str(audit, "file", "logs/mcp-proxy-audit.log"),
                                ConfigSupport.integer(audit, "max-mb", 50),
                                ConfigSupport.integer(audit, "max-files", 5));

                final RestrictionsConfig global = RestrictionsConfig
                                .load(ConfigSupport.map(yaml.get("global-restrictions")));

                return new ProxyConfig(serverId, mcpSection, authSection, servers, rpcSection, auditSection, global);
        }
}
