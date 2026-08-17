package dev.mc2p.plugin.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;

import dev.mc2p.plugin.facade.ServerFacade;
import dev.mc2p.plugin.http.McpRequestContextExtractor;
import dev.mc2p.plugin.thread.MainThread;

/**
 * Builds the MCP synchronous server (SDK 2.0) over the Streamable HTTP transport: tools
 * registered as {@link SyncToolSpecification}s with SDK-side input validation, resources
 * {@code mc2p://server} and {@code mc2p://status}, and resource-list-change capability so
 * SSE notifications for player join/leave are supported.
 */
public final class McpServerBootstrap {

    private McpServerBootstrap() {
    }

    public static McpSyncServer build(ToolRegistry registry, ServerFacade facade, ToolInvoker invoker,
            HttpServletStreamableServerTransportProvider transport, String version, MainThread mainThread) {

        List<SyncToolSpecification> tools = new ArrayList<>();
        for (ToolSpec spec : registry.all()) {
            McpSchema.Tool tool = McpSchema.Tool.builder(spec.name(), spec.inputSchema())
                    .description(spec.description())
                    .build();
            tools.add(SyncToolSpecification.builder()
                    .tool(tool)
                    .callHandler((exchange, request) -> invoker.invoke(spec.name(), request.arguments(),
                            authFrom(exchange.transportContext())))
                    .build());
        }

        SyncResourceSpecification serverResource = new SyncResourceSpecification(
                McpSchema.Resource.builder("mc2p://server", "MC2P server")
                        .description("Server identity and health")
                        .mimeType("application/json")
                        .build(),
                (exchange, request) -> ReadResourceResult.builder(List.of(
                        McpSchema.TextResourceContents.builder("mc2p://server",
                                dev.mc2p.common.json.Json.toJson(facade.status().toMap())).build()))
                        .build());

        SyncResourceSpecification statusResource = new SyncResourceSpecification(
                McpSchema.Resource.builder("mc2p://status", "MC2P status")
                        .description("Live server status")
                        .mimeType("application/json")
                        .build(),
                (exchange, request) -> ReadResourceResult.builder(List.of(
                        McpSchema.TextResourceContents.builder("mc2p://status",
                                dev.mc2p.common.json.Json.toJson(facade.status().toMap())).build()))
                        .build());

        return McpServer.sync(transport)
                .serverInfo("mc2p-" + facade.serverId(), version)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, true)
                        .logging()
                        .build())
                .tools(tools)
                .resources(List.of(serverResource, statusResource))
                .build();
    }

    private static AuthContext authFrom(io.modelcontextprotocol.common.McpTransportContext context) {
        if (context == null) {
            return AuthContext.unauthenticated();
        }
        Object role = context.get(McpRequestContextExtractor.KEY_ROLE);
        Object tokenId = context.get(McpRequestContextExtractor.KEY_TOKEN_ID);
        Object remoteIp = context.get(McpRequestContextExtractor.KEY_REMOTE_IP);
        return new AuthContext(role instanceof dev.mc2p.common.role.Role r ? r : null,
                tokenId == null ? "" : String.valueOf(tokenId),
                remoteIp == null ? "" : String.valueOf(remoteIp),
                "http");
    }

    /** Convenience: builds the transport provider with MC2P's security wiring. */
    public static HttpServletStreamableServerTransportProvider transport(String endpoint) {
        HttpServletStreamableServerTransportProvider.Builder builder = HttpServletStreamableServerTransportProvider
                .builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint(endpoint)
                .contextExtractor(new McpRequestContextExtractor())
                .securityValidator(new dev.mc2p.plugin.http.DnsRebindingValidator())
                .keepAliveInterval(java.time.Duration.ofSeconds(30));
        return builder.build();
    }
}