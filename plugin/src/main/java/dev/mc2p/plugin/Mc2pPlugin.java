package dev.mc2p.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mc2p.common.audit.AuditLogger;
import dev.mc2p.common.config.ConfigSupport;
import dev.mc2p.common.http.HttpEndpointConfig;
import dev.mc2p.common.role.Role;
import dev.mc2p.common.tokens.TokenManager;
import dev.mc2p.plugin.config.BackendConfig;
import dev.mc2p.plugin.facade.PaperServerFacade;
import dev.mc2p.plugin.http.HealthzServlet;
import dev.mc2p.plugin.http.McpHttpServer;
import dev.mc2p.plugin.rpc.BackendRpcServer;
import dev.mc2p.plugin.thread.MainThread;
import dev.mc2p.plugin.tools.McpServerBootstrap;
import dev.mc2p.plugin.tools.ReadTools;
import dev.mc2p.plugin.tools.ToolInvoker;
import dev.mc2p.plugin.tools.ToolRegistry;
import dev.mc2p.plugin.tools.WriteTools;

/**
 * The MC2P backend Paper plugin. Serves as a standalone MCP server (single TLS port) or,
 * behind a proxy, as a zero-port RPC backend over {@code mc2p:rpc}.
 */
public final class Mc2pPlugin extends JavaPlugin {

    private static final Logger log = LoggerFactory.getLogger(Mc2pPlugin.class);

    private BackendConfig config;
    private TokenManager tokens;
    private AuditLogger audit;
    private MainThread mainThread;
    private PaperServerFacade facade;
    private ToolRegistry registry;
    private ToolInvoker invoker;
    private McpHttpServer httpServer;
    private McpSyncServer mcpServer;
    private BackendRpcServer rpcServer;
    private PlayerTracker playerTracker;
    private String mode;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            applyConfig();
        } catch (RuntimeException e) {
            log.error("MC2P failed to start: {}", e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("mc2p").setExecutor(new Mc2pCommand(this));
        getServer().getPluginManager().registerEvents(new PlayerTracker(this), this);
        log.info("MC2P enabled in {} mode (serverId={})", mode, config.serverId());
    }

    @Override
    public void onDisable() {
        teardown();
        log.info("MC2P disabled");
    }

    /** Applies (or re-applies) the configuration; fully tears down and rebuilds the runtime. */
    public void applyConfig() {
        teardown();
        Path dataDir = getDataFolder().toPath();
        config = BackendConfig.load(loadConfigYaml());
        mode = resolveMode(config);

        if ("standalone".equals(mode) && !getServer().getOnlineMode()) {
            log.error("MC2P refuses to start in standalone mode: online-mode=false allows name spoofing. "
                    + "Enable online-mode=true, or run behind an authenticating proxy (mode: backend).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        tokens = new TokenManager(dataDir.resolve("tokens.yml"));
        tokens.updateFromConfig(resolveTokens(config));

        audit = new AuditLogger(dataDir.resolve(config.audit().file()), config.audit().maxMb(),
                config.audit().maxFiles());

        mainThread = new MainThread(this, config.proxy().timeoutMs());
        facade = new PaperServerFacade(this, mainThread, config.serverId(), config.restartStrategy());

        registry = new ToolRegistry();
        ReadTools.register(registry, facade, config);
        WriteTools.register(registry, facade, config);
        invoker = new ToolInvoker(registry, audit, config.serverId());

        if ("backend".equals(mode)) {
            startBackendMode(dataDir);
        } else {
            startStandalone(dataDir);
        }
    }

    private void startStandalone(Path dataDir) {
        BackendConfig.McpSection mcp = config.mcp();
        BackendConfig.McpSection.TlsSection tls = mcp.tls();
        HttpServletStreamableServerTransportProvider transport = McpServerBootstrap.transport(mcp.endpoint());
        mcpServer = McpServerBootstrap.build(registry, facade, invoker, transport, getPluginMeta().getVersion(),
                mainThread);

        HttpEndpointConfig http = new HttpEndpointConfig(mcp.bind(), mcp.port(), mcp.endpoint(), mcp.bodyLimitBytes(),
                tls.mode(), tls.keystore(), tls.passwordEnv());
        httpServer = new McpHttpServer(http, tokens, config.auth().ipAllowlist(), config.auth().rateLimit(), dataDir,
                config.serverId());
        httpServer.registerServlet(transport, mcp.endpoint());
        httpServer.registerServlet(new HealthzServlet(config.serverId(), getPluginMeta().getVersion(), mode,
                config.restartStrategy()), "/healthz");
        httpServer.start();
    }

    private void startBackendMode(Path dataDir) {
        String secret = ConfigSupport.resolveSecret("env:" + config.proxy().secretEnv(), dataDir) == null ? null
                : ConfigSupport.resolveSecret("env:" + config.proxy().secretEnv(), dataDir).value();
        if (secret == null || secret.isBlank()) {
            log.warn("MC2P backend mode: proxy secret ({}) is not set - the proxy will not be able to authenticate",
                    config.proxy().secretEnv());
        }
        rpcServer = new BackendRpcServer(this, invoker, config.serverId(), config.proxy().rpcChannel(), secret,
                config.proxy().timeoutMs());
        getServer().getMessenger().registerIncomingPluginChannel(this, config.proxy().rpcChannel(), rpcServer);
        getServer().getMessenger().registerOutgoingPluginChannel(this, config.proxy().rpcChannel());
        log.info("MC2P backend registered on plugin channel {}", config.proxy().rpcChannel());
    }

    private void teardown() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        if (mcpServer != null) {
            mcpServer.closeGracefully();
            mcpServer = null;
        }
        if (rpcServer != null) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, config.proxy().rpcChannel());
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, config.proxy().rpcChannel());
            rpcServer = null;
        }
    }

    /** Determines the effective mode: standalone | backend (auto: backend behind a known proxy). */
    private String resolveMode(BackendConfig config) {
        String configured = config.mode();
        if (!"auto".equals(configured)) {
            return configured;
        }
        boolean behindProxy = isBehindBungee() || proxySecretPresent();
        return behindProxy ? "backend" : "standalone";
    }

    private boolean proxySecretPresent() {
        String env = config.proxy().secretEnv();
        if (env == null || env.isBlank()) {
            return false;
        }
        String value = System.getenv(env);
        return value != null && !value.isBlank();
    }

    private boolean isBehindBungee() {
        try {
            Path spigotYml = getDataFolder().getParentFile().toPath().resolve("spigot.yml");
            if (java.nio.file.Files.isRegularFile(spigotYml)) {
                YamlConfiguration spigot = YamlConfiguration.loadConfiguration(spigotYml.toFile());
                return spigot.getBoolean("settings.bungeecord", false);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Resolves the configured token sources (env / file / plaintext). */
    private Map<Role, String> resolveTokens(BackendConfig config) {
        Map<Role, String> result = new EnumMap<>(Role.class);
        for (Map.Entry<String, String> e : config.auth().tokens().entrySet()) {
            Role role = Role.fromString(e.getKey());
            if (role == null) {
                continue;
            }
            ConfigSupport.Secret secret = ConfigSupport.resolveSecret(e.getValue(), getDataFolder().toPath());
            if (secret == null) {
                log.warn("MC2P: no token configured for role '{}' (source {})", role, e.getValue());
                continue;
            }
            if (!secret.fromEnvironment() && "config".equals(secret.source())) {
                log.warn("MC2P: token for role '{}' is stored in config.yml as plaintext. "
                        + "Use env:VAR or file:path instead.", role);
            }
            result.put(role, secret.value());
        }
        return result;
    }

    private Map<String, Object> loadConfigYaml() {
        Path configFile = getDataFolder().toPath().resolve("config.yml");
        try {
            Map<String, Object> parsed = ConfigSupport.loadYaml(configFile);
            if (parsed.isEmpty()) {
                log.warn("MC2P: config.yml is missing or empty; using defaults. Run /mc2p status to verify.");
            }
            return parsed;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read config.yml", e);
        }
    }

    // ---- accessors for Mc2pCommand ----

    public String effectiveMode() {
        return mode;
    }

    public BackendConfig config() {
        return config;
    }

    public TokenManager tokens() {
        return tokens;
    }

    public AuditLogger audit() {
        return audit;
    }

    public ToolRegistry registry() {
        return registry;
    }

    public String serverId() {
        return config == null ? "?" : config.serverId();
    }

    public boolean isRestarting() {
        return false;
    }

    /**
     * Sends the resource-list-changed notification to connected agents (off the main
     * thread). In backend mode the notification is relayed to the proxy as an RPC push.
     */
    public void notifyPlayersChanged() {
        if (rpcServer != null) {
            try {
                rpcServer.notifyEvent("players", Map.of());
            } catch (RuntimeException e) {
                log.warn("MC2P: failed to push player change to proxy: {}", e.getMessage());
            }
            return;
        }
        McpSyncServer server = this.mcpServer;
        if (server == null) {
            return;
        }
        Thread notifier = new Thread(() -> {
            try {
                server.notifyResourcesListChanged();
            } catch (RuntimeException e) {
                log.warn("MC2P: failed to notify resource list change: {}", e.getMessage());
            }
        }, "mc2p-notify");
        notifier.setDaemon(true);
        notifier.start();
    }
}