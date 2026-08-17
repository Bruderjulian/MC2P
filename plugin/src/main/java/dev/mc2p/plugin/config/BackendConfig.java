package dev.mc2p.plugin.config;

import java.util.List;
import java.util.Map;

import dev.mc2p.common.config.ConfigSupport;
import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;

/** Typed backend plugin configuration with spec defaults. */
public record BackendConfig(
        String mode,
        String serverId,
        McpSection mcp,
        ProxySection proxy,
        AuthSection auth,
        CommandSection commands,
        LimitsSection limits,
        String restartStrategy,
        FeaturesSection features,
        AuditSection audit) {

    public record McpSection(String bind, int port, String endpoint, TlsSection tls, int bodyLimitBytes) {

        public record TlsSection(String mode, String keystore, String passwordEnv) {
        }
    }

    public record ProxySection(String secretEnv, String rpcChannel, int timeoutMs) {
    }

    public record AuthSection(Map<String, String> tokens, List<String> ipAllowlist, TokenBucketRateLimiter.Config rateLimit) {
    }

    public record CommandSection(List<String> opsAllowlist, List<String> adminAllowlist, List<String> deny) {
    }

    public record LimitsSection(int maxCoordinate, int maxRegionBlocks, int maxEntityLimit, int maxCommandLength) {
    }

    public record FeaturesSection(boolean blockEdit, boolean stats) {
    }

    public record AuditSection(String file, int maxMb, int maxFiles) {
    }

    public static final String DEFAULT_MODE = "auto";

    public static BackendConfig defaults() {
        return load(Map.of());
    }

    public static BackendConfig load(Map<String, Object> yaml) {
        String mode = ConfigSupport.str(yaml, "mode", DEFAULT_MODE);
        String serverId = ConfigSupport.str(yaml, "serverId", "main-01");

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

        Map<String, Object> proxy = ConfigSupport.map(yaml.get("proxy"));
        ProxySection proxySection = new ProxySection(
                ConfigSupport.str(proxy, "secret-env", "MC2P_PROXY_SECRET"),
                ConfigSupport.str(proxy, "rpc-channel", "mc2p:rpc"),
                ConfigSupport.integer(proxy, "timeout-ms", 5000));

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

        Map<String, Object> commands = ConfigSupport.map(yaml.get("commands"));
        CommandSection commandSection = new CommandSection(
                ConfigSupport.strings(commands, "ops-allowlist").isEmpty()
                        ? List.of("gamemode", "tp", "teleport", "weather", "time", "effect", "clear")
                        : ConfigSupport.strings(commands, "ops-allowlist"),
                ConfigSupport.strings(commands, "admin-allowlist").isEmpty()
                        ? List.of("*")
                        : ConfigSupport.strings(commands, "admin-allowlist"),
                ConfigSupport.strings(commands, "deny").isEmpty()
                        ? List.of("stop", "restart", "save-off", "save-all", "kick-all", "op")
                        : ConfigSupport.strings(commands, "deny"));

        Map<String, Object> limits = ConfigSupport.map(yaml.get("limits"));
        LimitsSection limitsSection = new LimitsSection(
                ConfigSupport.integer(limits, "max-coordinate", 30000000),
                ConfigSupport.integer(limits, "max-region-blocks", 10000),
                ConfigSupport.integer(limits, "max-entity-limit", 100),
                ConfigSupport.integer(limits, "max-command-length", 512));

        Map<String, Object> restart = ConfigSupport.map(yaml.get("restart"));
        String restartStrategy = ConfigSupport.str(restart, "strategy", "auto");

        Map<String, Object> features = ConfigSupport.map(yaml.get("features"));
        FeaturesSection featuresSection = new FeaturesSection(
                ConfigSupport.bool(features, "blockEdit", false),
                ConfigSupport.bool(features, "stats", true));

        Map<String, Object> audit = ConfigSupport.map(yaml.get("audit"));
        AuditSection auditSection = new AuditSection(
                ConfigSupport.str(audit, "file", "logs/mcp-audit.log"),
                ConfigSupport.integer(audit, "max-mb", 50),
                ConfigSupport.integer(audit, "max-files", 5));

        return new BackendConfig(mode, serverId, mcpSection, proxySection, authSection, commandSection, limitsSection,
                restartStrategy, featuresSection, auditSection);
    }
}