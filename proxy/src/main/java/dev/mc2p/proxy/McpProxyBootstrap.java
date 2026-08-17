package dev.mc2p.proxy;

import com.velocitypowered.api.proxy.ProxyServer;
import dev.mc2p.common.audit.AuditLogger;
import dev.mc2p.common.json.Json;
import dev.mc2p.proxy.http.DnsRebindingValidator;
import dev.mc2p.proxy.http.McpRequestContextExtractor;
import dev.mc2p.proxy.rpc.BackendClient;
import dev.mc2p.proxy.tools.RelayTools;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Builds the proxy MCP synchronous server (SDK 2.0) over Streamable HTTP: the relayed
 * backend tools, proxy-level tools, and resources {@code mc2p://status} and
 * {@code mc2p://servers}, with resource-list-change so SSE notifications from backend
 * RPC pushes are forwarded to connected agents.
 */
public final class McpProxyBootstrap {

    private McpProxyBootstrap() {}

    public static McpSyncServer build(
            BackendClient client,
            ProxyServer proxy,
            AuditLogger audit,
            String proxyServerId,
            String version,
            long startedAtMillis,
            HttpServletStreamableServerTransportProvider transport) {

        List<SyncToolSpecification> tools = RelayTools.build(client, audit, proxyServerId, proxy);

        SyncResourceSpecification status = new SyncResourceSpecification(
                McpSchema.Resource.builder("mc2p://status", "Proxy status")
                        .description("Proxy identity, health and fleet summary")
                        .mimeType("application/json")
                        .build(),
                (exchange, request) -> ReadResourceResult.builder(List.of(McpSchema.TextResourceContents.builder(
                                        "mc2p://status",
                                        Json.toJson(Map.of(
                                                "serverId",
                                                proxyServerId,
                                                "role",
                                                "proxy",
                                                "plugin",
                                                version,
                                                "backends",
                                                client.knownServerIds().size(),
                                                "uptimeSeconds",
                                                (System.currentTimeMillis() - startedAtMillis) / 1000)))
                                .build()))
                        .build());

        SyncResourceSpecification servers = new SyncResourceSpecification(
                McpSchema.Resource.builder("mc2p://servers", "Connected backends")
                        .description("Backend serverIds reachable over mc2p:rpc")
                        .mimeType("application/json")
                        .build(),
                (exchange, request) -> ReadResourceResult.builder(List.of(McpSchema.TextResourceContents.builder(
                                        "mc2p://servers", Json.toJson(Map.of("servers", client.knownServerIds())))
                                .build()))
                        .build());

        return McpServer.sync(transport)
                .serverInfo("mc2p-" + proxyServerId, version)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, true)
                        .logging()
                        .build())
                .tools(tools)
                .resources(List.of(status, servers))
                .build();
    }

    /** Convenience: builds the transport provider with MC2P's security wiring. */
    public static HttpServletStreamableServerTransportProvider transport(String endpoint) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint(endpoint)
                .contextExtractor(new McpRequestContextExtractor())
                .securityValidator(new DnsRebindingValidator())
                .keepAliveInterval(Duration.ofSeconds(30))
                .build();
    }
}
