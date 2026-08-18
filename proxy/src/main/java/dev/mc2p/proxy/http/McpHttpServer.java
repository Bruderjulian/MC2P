package dev.mc2p.proxy.http;

import dev.mc2p.common.activity.ClientActivityTracker;
import dev.mc2p.common.config.RestrictionsConfig;
import dev.mc2p.common.http.HttpEndpointConfig;
import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;
import dev.mc2p.common.tokens.TokenManager;
import jakarta.servlet.DispatcherType;
import java.nio.file.Path;
import java.util.EnumSet;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the single TLS HTTP port for the proxy topology: the MCP endpoint (auth-gated) and
 * the unauthenticated health endpoint. TLS modes: {@code selfsigned} (default),
 * {@code keystore}, {@code none-behind-proxy}, {@code none} (loud warning).
 */
public final class McpHttpServer {

    private static final Logger log = LoggerFactory.getLogger(McpHttpServer.class);

    private final Server server;
    private final ServletContextHandler context;
    private final String endpoint;

    public McpHttpServer(
            HttpEndpointConfig http,
            TokenManager tokens,
            RestrictionsConfig serverRestrictions,
            java.util.List<String> ipAllowlist,
            TokenBucketRateLimiter.Config rateLimit,
            Path dataDir,
            String serverId,
            ClientActivityTracker activity) {
        this.endpoint = http.endpoint();

        this.server = new Server();
        this.context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        this.context.setContextPath("/");

        ServerConnector connector = buildConnector(http, dataDir, serverId);
        this.server.addConnector(connector);
        this.server.setHandler(context);

        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(rateLimit);
        AuthFilter authFilter =
                new AuthFilter(tokens, serverRestrictions, ipAllowlist, rateLimiter, http.bodyLimitBytes(), activity);
        FilterHolder filterHolder = new FilterHolder(authFilter);
        context.addFilter(filterHolder, endpoint, EnumSet.of(DispatcherType.REQUEST));
    }

    private ServerConnector buildConnector(HttpEndpointConfig http, Path dataDir, String serverId) {
        String tlsMode = http.tlsMode();
        String keystorePath = http.keystore();
        String keystorePasswordEnv = http.passwordEnv();
        boolean ssl =
                switch (tlsMode) {
                    case "selfsigned", "keystore" -> true;
                    case "none-behind-proxy" -> {
                        log.warn(
                                "TLS mode 'none-behind-proxy': assuming the host panel terminates TLS in front of this port");
                        yield false;
                    }
                    default -> {
                        log.warn("!!!!!!!!!! TLS DISABLED (tls.mode=none). The MCP endpoint is served in plaintext. "
                                + "Use selfsigned or a host proxy in production. !!!!!!!!!!");
                        yield false;
                    }
                };

        if (!ssl) {
            ServerConnector plain = new ServerConnector(server);
            plain.setHost(http.bind());
            plain.setPort(http.port());
            return plain;
        }

        SslContextFactory.Server sslFactory = new SslContextFactory.Server();
        String password;
        if ("keystore".equals(tlsMode)) {
            Path keystore = Path.of(keystorePath);
            if (!keystore.isAbsolute()) {
                keystore = dataDir.resolve(keystore);
            }
            sslFactory.setKeyStorePath(keystore.toString());
            password = keystorePasswordEnv == null ? null : System.getenv(keystorePasswordEnv);
            if (password == null || password.isBlank()) {
                throw new IllegalStateException(
                        "tls.mode=keystore requires the password env var " + keystorePasswordEnv);
            }
            sslFactory.setKeyStorePassword(password);
            sslFactory.setKeyStoreType("PKCS12");
        } else {
            Path keystore = Path.of(keystorePath);
            if (!keystore.isAbsolute()) {
                keystore = dataDir.resolve(keystore);
            }
            password = SelfSignedCert.ensureKeystore(keystore, keystorePasswordEnv, serverId);
            sslFactory.setKeyStorePath(keystore.toString());
            sslFactory.setKeyStorePassword(password);
            sslFactory.setKeyStoreType("PKCS12");
        }
        sslFactory.setIncludeProtocols("TLSv1.2", "TLSv1.3");

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(http.port());
        HttpConnectionFactory httpConnection = new HttpConnectionFactory(httpConfig);
        SslConnectionFactory sslConnection = new SslConnectionFactory(sslFactory, "http/1.1");
        ServerConnector connector = new ServerConnector(server, sslConnection, httpConnection);
        connector.setHost(http.bind());
        connector.setPort(http.port());
        return connector;
    }

    public void registerServlet(jakarta.servlet.Servlet servlet, String pathSpec) {
        context.addServlet(new ServletHolder(servlet), pathSpec);
    }

    public void start() {
        try {
            server.start();
            log.info(
                    "MC2P proxy MCP endpoint listening on {}:{} (TLS enabled per config)",
                    mcpBind(),
                    server.getURI().getPort());
        } catch (Exception e) {
            throw new IllegalStateException("failed to start HTTP server", e);
        }
    }

    private String mcpBind() {
        var connectors = server.getConnectors();
        return connectors.length > 0 && connectors[0] instanceof ServerConnector sc ? sc.getHost() : "0.0.0.0";
    }

    public void stop() {
        try {
            server.stop();
        } catch (Exception e) {
            log.warn("error stopping HTTP server", e);
        }
    }
}
