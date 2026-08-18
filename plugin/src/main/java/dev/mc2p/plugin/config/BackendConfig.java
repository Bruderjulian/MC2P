package dev.mc2p.plugin.config;

import dev.mc2p.common.config.ConfigSupport;
import dev.mc2p.common.config.RestrictionsConfig;
import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;
import java.util.List;
import java.util.Map;

/**
 * Typed backend plugin configuration. Two file layouts share this parser:
 *
 * <ul>
 * <li>{@code config.yml} (standalone) — no {@code mode}; restrictions under
 * {@code global-restrictions}.</li>
 * <li>{@code backend.yml} (backend-only) — {@code mode} and {@code serverId};
 * restrictions under {@code server-restrictions}.</li>
 * </ul>
 *
 * Only one of the two files exists on disk at a time; switching modes swaps
 * them and
 * migrates the restrictions block into the other file.
 */
public record BackendConfig(
                String mode,
                String serverId,
                McpSection mcp,
                ProxySection proxy,
                AuthSection auth,
                LimitsSection limits,
                String restartStrategy,
                AuditSection audit,
                RestrictionsConfig globalRestrictions,
                RestrictionsConfig serverRestrictions) {

        public record McpSection(String bind, int port, String endpoint, TlsSection tls, int bodyLimitBytes) {

                public record TlsSection(String mode, String keystore, String passwordEnv) {
                }
        }

        public record ProxySection(String secretEnv, String rpcChannel, int timeoutMs) {
        }

        public record AuthSection(
                        List<String> ipAllowlist, TokenBucketRateLimiter.Config rateLimit, int activityWindowMinutes) {
        }

        public record LimitsSection(
                        int maxConcurrentRequests,
                        int maxCoordinate,
                        int maxRegionBlocks,
                        int maxEntityLimit,
                        int maxCommandLength) {
        }

        public record AuditSection(String file, int maxMb, int maxFiles) {
        }

        public static final String DEFAULT_MODE = "auto";

        public static BackendConfig defaults() {
                return load(Map.of());
        }

        public static BackendConfig load(final Map<String, Object> yaml) {
                final String mode = ConfigSupport.str(yaml, "mode", DEFAULT_MODE);
                final String serverId = ConfigSupport.str(yaml, "serverId", "main");

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

                final Map<String, Object> proxy = ConfigSupport.map(yaml.get("proxy"));
                final ProxySection proxySection = new ProxySection(
                                ConfigSupport.str(proxy, "secret", "MC2P_PROXY_SECRET"),
                                ConfigSupport.str(proxy, "rpc-channel", "mc2p:rpc"),
                                ConfigSupport.integer(proxy, "timeout-ms", 5000));

                final Map<String, Object> auth = ConfigSupport.map(yaml.get("auth"));
                final Map<String, Object> rate = ConfigSupport.map(auth.get("rate-limit"));
                final AuthSection authSection = new AuthSection(
                                ConfigSupport.strings(auth, "ip-allowlist"),
                                new TokenBucketRateLimiter.Config(
                                                (double) ConfigSupport.integer(rate, "tokens-per-second", 5),
                                                ConfigSupport.integer(rate, "burst", 20)),
                                ConfigSupport.integer(auth, "activity-window-minutes", 5));

                final Map<String, Object> limits = ConfigSupport.map(yaml.get("limits"));
                final LimitsSection limitsSection = new LimitsSection(
                                ConfigSupport.integer(limits, "max-concurrent-requests", 12),
                                ConfigSupport.integer(limits, "max-coordinate", 30000000),
                                ConfigSupport.integer(limits, "max-region-blocks", 32768),
                                ConfigSupport.integer(limits, "max-entity-limit", 128),
                                ConfigSupport.integer(limits, "max-command-length", -1));

                final Map<String, Object> restart = ConfigSupport.map(yaml.get("restart"));
                final String restartStrategy = ConfigSupport.str(restart, "strategy", "auto");

                final Map<String, Object> audit = ConfigSupport.map(yaml.get("audit"));
                final AuditSection auditSection = new AuditSection(
                                ConfigSupport.str(audit, "file", "logs/mcp-audit.log"),
                                ConfigSupport.integer(audit, "max-mb", 50),
                                ConfigSupport.integer(audit, "max-files", 5));

                final RestrictionsConfig global = RestrictionsConfig
                                .load(ConfigSupport.map(yaml.get("global-restrictions")));
                final RestrictionsConfig server = RestrictionsConfig
                                .load(ConfigSupport.map(yaml.get("server-restrictions")));

                return new BackendConfig(
                                mode,
                                serverId,
                                mcpSection,
                                proxySection,
                                authSection,
                                limitsSection,
                                restartStrategy,
                                auditSection,
                                global,
                                server);
        }

        /** The restrictions that apply to requests handled directly by this server. */
        public RestrictionsConfig effectiveRestrictions() {
                return globalRestrictions.merge(serverRestrictions);
        }
}
