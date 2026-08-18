package dev.mc2p.proxy;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.proxy.server.ServerRegisteredEvent;
import com.velocitypowered.api.event.proxy.server.ServerUnregisteredEvent;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIVelocityConfig;
import dev.mc2p.common.activity.ClientActivityTracker;
import dev.mc2p.common.audit.AuditLogger;
import dev.mc2p.common.config.ConfigSupport;
import dev.mc2p.common.http.HttpEndpointConfig;
import dev.mc2p.common.setup.SetupSupport;
import dev.mc2p.common.tokens.TokenManager;
import dev.mc2p.common.util.Tokens;
import dev.mc2p.proxy.config.ProxyConfig;
import dev.mc2p.proxy.http.HealthzServlet;
import dev.mc2p.proxy.http.McpHttpServer;
import dev.mc2p.proxy.rpc.BackendClient;
import dev.mc2p.proxy.rpc.RpcListener;
import dev.mc2p.proxy.tools.RelayTools;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * The MC2P proxy plugin (Velocity). Hosts the single public MCP endpoint and routes tool
 * calls to backend Paper servers over {@code mc2p:rpc}. Loaded via
 * {@code velocity-plugin.json} with constructor injection.
 */
public final class McpProxyPlugin {

    /** Trust window granted by a {@code hello} handshake; proxy re-sends hello before each call. */
    private static final long HELLO_WINDOW_NANOS = TimeUnit.MINUTES.toNanos(5);

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final String version;
    private final long startedAt = System.currentTimeMillis();

    private ProxyConfig config;
    private TokenManager tokens;
    private ClientActivityTracker activity;
    private AuditLogger audit;
    private BackendClient backendClient;
    private ChannelIdentifier channel;
    private RpcListener rpcListener;
    private McpHttpServer httpServer;
    private McpSyncServer mcpServer;

    public McpProxyPlugin(
            ProxyServer server,
            Logger logger,
            PluginDescription description,
            @com.velocitypowered.api.plugin.annotation.DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.version = description.getVersion().orElse("unknown");
        this.dataDirectory = dataDirectory;
        CommandAPI.onLoad(new CommandAPIVelocityConfig(server, this));
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            init();
            new Mc2pCommand(this).register();
            CommandAPI.onEnable();
        } catch (RuntimeException e) {
            logger.error("MC2P failed to start: {}", e.getMessage(), e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        teardown();
        CommandAPI.unregister("mc2p");
        CommandAPI.onDisable();
    }

    @Subscribe
    public void onServerRegistered(ServerRegisteredEvent event) {
        registerBackend(event.registeredServer());
    }

    @Subscribe
    public void onServerUnregistered(ServerUnregisteredEvent event) {
        backendClient.unregisterServer(
                event.unregisteredServer().getServerInfo().getName());
    }

    /** Applies (or re-applies) the configuration; fully tears down and rebuilds the runtime. */
    public synchronized void init() {
        teardown();
        config = ProxyConfig.load(loadConfig());

        tokens = new TokenManager(dataDirectory.resolve("tokens.yml"), dataDirectory);
        tokens.load();
        activity = new ClientActivityTracker(
                java.time.Duration.ofMinutes(config.auth().activityWindowMinutes()));

        audit = new AuditLogger(
                dataDirectory.resolve(config.audit().file()),
                config.audit().maxMb(),
                config.audit().maxFiles());

        String proxySecret = resolveProxySecret(config);
        if (proxySecret == null) {
            logger.warn(
                    "MC2P proxy secret ({}) is not set - generating one now (shown once); "
                            + "set it on every backend as the same env var or in plugins/MC2P/proxy-secret.",
                    config.rpc().secretEnv());
            proxySecret = ensureProxySecret(config);
            logger.info("  MC2P_PROXY_SECRET: {}", proxySecret);
        }

        String[] channelParts = config.rpc().channel().split(":", 2);
        channel = channelParts.length == 2
                ? MinecraftChannelIdentifier.create(channelParts[0], channelParts[1])
                : MinecraftChannelIdentifier.create("mc2p", config.rpc().channel());
        backendClient = new BackendClient(
                channel,
                proxySecret,
                config.rpc().timeoutMs(),
                HELLO_WINDOW_NANOS,
                config.rpc().maxChunks(),
                this::notifyClients);

        server.getChannelRegistrar().register(channel);
        rpcListener = new RpcListener(backendClient, channel);
        server.getEventManager().register(this, rpcListener);
        server.getEventManager().register(this, this);
        for (RegisteredServer registered : server.getAllServers()) {
            registerBackend(registered);
        }

        HttpServletStreamableServerTransportProvider transport =
                McpProxyBootstrap.transport(config.mcp().endpoint());
        mcpServer =
                McpProxyBootstrap.build(backendClient, server, audit, config.serverId(), version, startedAt, transport);

        HttpEndpointConfig http = new HttpEndpointConfig(
                config.mcp().bind(),
                config.mcp().port(),
                config.mcp().endpoint(),
                config.mcp().bodyLimitBytes(),
                config.mcp().tls().mode(),
                config.mcp().tls().keystore(),
                config.mcp().tls().passwordEnv());
        httpServer = new McpHttpServer(
                http,
                tokens,
                config.globalRestrictions(),
                config.auth().ipAllowlist(),
                config.auth().rateLimit(),
                dataDirectory,
                config.serverId(),
                activity);
        httpServer.registerServlet(transport, config.mcp().endpoint());
        httpServer.registerServlet(new HealthzServlet(config.serverId(), version), "/healthz");
        httpServer.start();

        logger.info(
                "MC2P proxy enabled (serverId={}, backends={}, tools={})",
                config.serverId(),
                backendClient.knownServerIds().size(),
                RelayTools.count());
    }

    private void registerBackend(RegisteredServer registered) {
        String name = registered.getServerInfo().getName();
        String serverId = config.servers().getOrDefault(name, name);
        backendClient.registerServer(serverId, registered);
        logger.info("MC2P registered backend {} -> {}", name, serverId);
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
        if (rpcListener != null) {
            server.getEventManager().unregisterListener(this, rpcListener);
            rpcListener = null;
        }
        server.getEventManager().unregisterListener(this, this);
        backendClient = null;
    }

    private String resolveProxySecret(ProxyConfig config) {
        String env = config.rpc().secretEnv();
        if (env != null && !env.isBlank()) {
            String value = System.getenv(env);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return SetupSupport.readSecretFile(dataDirectory, SetupSupport.PROXY_SECRET_FILE);
    }

    /** Ensures a proxy secret exists (env var or the proxy-secret file), generating and persisting one if not. */
    private String ensureProxySecret(ProxyConfig config) {
        String secret = resolveProxySecret(config);
        if (secret != null) {
            return secret;
        }
        String generated = Tokens.generateToken();
        try {
            SetupSupport.writeSecretFile(dataDirectory, SetupSupport.PROXY_SECRET_FILE, generated);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to persist the proxy secret", e);
        }
        return generated;
    }

    private Map<String, Object> loadConfig() {
        Path file = dataDirectory.resolve("config.yml");
        try {
            Map<String, Object> parsed = ConfigSupport.loadYaml(file);
            if (parsed.isEmpty()) {
                logger.warn("MC2P: config.yml is missing or empty; using defaults. Run /mc2p status to verify.");
            }
            return parsed;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read config.yml", e);
        }
    }

    /** Forwards a backend RPC push to connected MCP clients (off the proxy thread). */
    private void notifyClients() {
        McpSyncServer mcp = this.mcpServer;
        if (mcp == null) {
            return;
        }
        Thread notifier = new Thread(
                () -> {
                    try {
                        mcp.notifyResourcesListChanged();
                    } catch (RuntimeException e) {
                        logger.warn("MC2P: failed to notify resource list change: {}", e.getMessage());
                    }
                },
                "mc2p-proxy-notify");
        notifier.setDaemon(true);
        notifier.start();
    }

    // ---- accessors for Mc2pCommand ----

    public ProxyConfig config() {
        return config;
    }

    public TokenManager tokens() {
        return tokens;
    }

    public ClientActivityTracker activity() {
        return activity;
    }

    public AuditLogger audit() {
        return audit;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public String serverId() {
        return config == null ? "?" : config.serverId();
    }

    /**
     * Creates a default-named token if the store has no active tokens and returns the
     * freshly generated plaintext (shown exactly once).
     */
    public Map<String, String> ensureTokens() {
        Map<String, String> generated = new java.util.LinkedHashMap<>();
        if (!tokens.snapshot().isEmpty()) {
            return generated;
        }
        generated.put("default", tokens.create("default"));
        return generated;
    }

    /** The active proxy secret (env var or the proxy-secret file), or null when unset. */
    public String proxySecret() {
        return config == null ? null : resolveProxySecret(config);
    }

    /** Ensures a proxy secret exists (env var or proxy-secret file), generating one if needed. */
    public String ensureProxySecret() {
        return ensureProxySecret(config);
    }

    /** Re-registers every known backend with the RPC client (idempotent). */
    public void activateBackends() {
        if (backendClient == null) {
            return;
        }
        for (RegisteredServer registered : server.getAllServers()) {
            registerBackend(registered);
        }
    }

    public int toolCount() {
        return RelayTools.count();
    }

    public java.util.List<String> backendServerIds() {
        return backendClient == null ? java.util.List.of() : backendClient.knownServerIds();
    }

    public void reload() {
        init();
    }
}
