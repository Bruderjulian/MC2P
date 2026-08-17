package dev.mc2p.proxy.tools;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import dev.mc2p.common.audit.AuditLogger;
import dev.mc2p.common.json.Json;
import dev.mc2p.common.role.Role;
import dev.mc2p.common.validate.Validators;
import dev.mc2p.proxy.http.McpRequestContextExtractor;
import dev.mc2p.proxy.rpc.BackendClient;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The proxy tool set. Backend tools are re-exposed with a {@code server} parameter and
 * routed over {@code mc2p:rpc}:
 *
 * <ul>
 * <li>player-targeted tools auto-resolve the backend from the player's current server
 * (Velocity connection tracking) when {@code server} is omitted;</li>
 * <li>read tools accept {@code "*"} to broadcast to all connected backends, aggregated
 * into {@code {"servers": {...}, "errors": {...}}};</li>
 * <li>destructive tools are audited at the proxy and fail closed before anything is
 * relayed;</li>
 * <li>{@code fleet_status} and {@code player_locate} are proxy-level tools.</li>
 * </ul>
 *
 * Authorization is enforced twice: the role of the authenticated MCP client is checked
 * here and again by the backend tool layer, which receives the role in the RPC envelope.
 */
public final class RelayTools {

    /** Registration metadata for one relayed or proxy-level tool. */
    public record ToolDef(
            String name,
            String backendMethod,
            Role requiredRole,
            boolean destructive,
            boolean playerTool,
            boolean broadcastable,
            boolean serverRequired,
            String description,
            Map<String, Object> baseSchema,
            List<String> requiredParams) {}

    private RelayTools() {}

    public static List<SyncToolSpecification> build(
            BackendClient client, AuditLogger audit, String proxyServerId, ProxyServer proxy) {
        List<SyncToolSpecification> specs = new ArrayList<>();
        for (ToolDef def : CATALOG) {
            specs.add(spec(def, client, audit, proxyServerId, proxy));
        }
        return specs;
    }

    /** Number of tools exposed by the proxy MCP server (for /mc2p status). */
    public static int count() {
        return CATALOG.size();
    }

    private static SyncToolSpecification spec(
            ToolDef def, BackendClient client, AuditLogger audit, String proxyServerId, ProxyServer proxy) {
        McpSchema.Tool tool = McpSchema.Tool.builder(def.name(), buildSchema(def))
                .description(def.description())
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> invoke(
                        def,
                        request.arguments(),
                        authFrom(exchange.transportContext()),
                        client,
                        audit,
                        proxyServerId,
                        proxy))
                .build();
    }

    private static CallToolResult invoke(
            ToolDef def,
            Map<String, Object> args,
            AuthContext auth,
            BackendClient client,
            AuditLogger audit,
            String proxyServerId,
            ProxyServer proxy) {
        Map<String, Object> params = args == null ? Map.of() : args;

        if (auth.role() == null) {
            return ToolResult.error("unauthenticated");
        }
        if (!auth.role().can(def.requiredRole())) {
            return ToolResult.error(
                    "tool '" + def.name() + "' requires role " + def.requiredRole() + " (client: " + auth.role() + ")");
        }
        if (def.destructive() && !Boolean.TRUE.equals(params.get("confirm"))) {
            return ToolResult.error("tool '" + def.name() + "' is destructive and requires confirm: true");
        }
        if (def.destructive()) {
            // Fail closed: the relay must not proceed if the audit entry cannot be written.
            try {
                audit.log(
                        auth.role(),
                        auth.tokenId(),
                        proxyServerId,
                        def.name(),
                        "relay",
                        Json.toJson(redactSecrets(params)));
            } catch (RuntimeException e) {
                return ToolResult.error("audit write failed; relay refused: " + e.getMessage());
            }
        }

        if ("player_locate".equals(def.name())) {
            return playerLocate(proxy, client, params);
        }

        Route route = resolveTargets(def, client, proxy, params);
        if (route.error() != null) {
            return ToolResult.error(route.error());
        }
        List<String> targets = route.targets();

        Map<String, Object> relayParams = new LinkedHashMap<>(params);
        relayParams.remove("server");

        if (targets.size() == 1) {
            Optional<Map<String, Object>> response =
                    client.call(targets.get(0), def.backendMethod(), auth.role().toString(), relayParams);
            if (response.isEmpty()) {
                return ToolResult.error("backend " + targets.get(0) + " unreachable or timed out");
            }
            Map<String, Object> message = response.get();
            if (Boolean.TRUE.equals(message.get("ok"))) {
                return ToolResult.success(message.get("result"));
            }
            return ToolResult.error(String.valueOf(message.getOrDefault("error", "backend error")));
        }

        Map<String, Object> servers = new LinkedHashMap<>();
        Map<String, Object> errors = new LinkedHashMap<>();
        for (String serverId : targets) {
            Optional<Map<String, Object>> response =
                    client.call(serverId, def.backendMethod(), auth.role().toString(), relayParams);
            if (response.isEmpty()) {
                errors.put(serverId, "unreachable or timed out");
                continue;
            }
            Map<String, Object> message = response.get();
            if (Boolean.TRUE.equals(message.get("ok"))) {
                servers.put(serverId, message.get("result"));
            } else {
                errors.put(serverId, String.valueOf(message.getOrDefault("error", "backend error")));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("servers", servers);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return ToolResult.success(result);
    }

    private record Route(List<String> targets, String error) {}

    /** @return resolved target serverIds, or a route carrying the rejection reason. */
    private static Route resolveTargets(
            ToolDef def, BackendClient client, ProxyServer proxy, Map<String, Object> args) {
        String server = string(args.get("server"));
        if (server != null && !server.isBlank()) {
            if ("*".equals(server)) {
                if (!def.broadcastable()) {
                    return new Route(List.of(), "tool '" + def.name() + "' does not accept server=\"*\"");
                }
                List<String> all = client.knownServerIds();
                if (all.isEmpty()) {
                    return new Route(List.of(), "no backends connected");
                }
                return new Route(all, null);
            }
            if (!client.knownServerIds().contains(server)) {
                return new Route(List.of(), "unknown server: " + server);
            }
            return new Route(List.of(server), null);
        }
        if (def.playerTool()) {
            UUID uuid = Validators.parseUuid(string(args.get("uuid")));
            if (uuid == null) {
                return new Route(List.of(), "missing required argument 'uuid'");
            }
            Optional<String> resolved = resolvePlayerServer(proxy, client, uuid);
            if (resolved.isEmpty()) {
                return new Route(List.of(), "player is not online on a connected backend");
            }
            return new Route(List.of(resolved.get()), null);
        }
        if (def.broadcastable()) {
            List<String> all = client.knownServerIds();
            if (all.isEmpty()) {
                return new Route(List.of(), "no backends connected");
            }
            return new Route(all, null);
        }
        return new Route(List.of(), "missing required argument 'server'");
    }

    private static Optional<String> resolvePlayerServer(ProxyServer proxy, BackendClient client, UUID uuid) {
        Optional<Player> player = proxy.getPlayer(uuid);
        if (player.isEmpty()) {
            return Optional.empty();
        }
        Optional<ServerConnection> connection = player.get().getCurrentServer();
        if (connection.isEmpty()) {
            return Optional.empty();
        }
        return client.serverIdForVelocityName(connection.get().getServerInfo().getName());
    }

    private static CallToolResult playerLocate(ProxyServer proxy, BackendClient client, Map<String, Object> args) {
        UUID uuid = Validators.parseUuid(string(args.get("uuid")));
        if (uuid == null) {
            return ToolResult.error("missing required argument 'uuid'");
        }
        Optional<Player> found = proxy.getPlayer(uuid);
        if (found.isEmpty()) {
            return ToolResult.success(Map.of("uuid", uuid.toString(), "online", false));
        }
        Player player = found.get();
        Optional<ServerConnection> connection = player.getCurrentServer();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", uuid.toString());
        result.put("online", true);
        result.put("username", player.getUsername());
        if (connection.isEmpty()) {
            result.put("server", "none");
        } else {
            String name = connection.get().getServerInfo().getName();
            result.put("server", name);
            result.put("serverId", client.serverIdForVelocityName(name).orElse(name));
        }
        return ToolResult.success(result);
    }

    private static AuthContext authFrom(McpTransportContext context) {
        if (context == null) {
            return AuthContext.unauthenticated();
        }
        Object role = context.get(McpRequestContextExtractor.KEY_ROLE);
        Object tokenId = context.get(McpRequestContextExtractor.KEY_TOKEN_ID);
        Object remoteIp = context.get(McpRequestContextExtractor.KEY_REMOTE_IP);
        return new AuthContext(
                role instanceof Role r ? r : null,
                tokenId == null ? "" : String.valueOf(tokenId),
                remoteIp == null ? "" : String.valueOf(remoteIp),
                "http");
    }

    private static Map<String, Object> buildSchema(ToolDef def) {
        Map<String, Object> properties = new LinkedHashMap<>(def.baseSchema());
        properties.put("server", Schemas.str(serverDescription(def)));
        List<String> required = new ArrayList<>(def.requiredParams());
        if (def.serverRequired()) {
            required.add("server");
        }
        return Schemas.object(properties, required);
    }

    private static String serverDescription(ToolDef def) {
        if (def.playerTool()) {
            return "Target backend serverId; omit to auto-resolve from the player's current server";
        }
        if (def.broadcastable()) {
            return "Target backend serverId, or \"*\" to query all servers";
        }
        return "Target backend serverId";
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> redactSecrets(Map<String, Object> args) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        if (copy.containsKey("command") && copy.get("command") instanceof String cmd) {
            int end = cmd.indexOf(' ');
            copy.put("command", end < 0 ? cmd : cmd.substring(0, end) + " …");
        }
        return copy;
    }

    private static ToolDef relay(
            String name,
            Role role,
            boolean destructive,
            boolean playerTool,
            boolean broadcastable,
            boolean serverRequired,
            String description,
            Map<String, Object> properties,
            List<String> required) {
        return new ToolDef(
                name,
                name,
                role,
                destructive,
                playerTool,
                broadcastable,
                serverRequired,
                description,
                Schemas.object(properties, required),
                required);
    }

    private static ToolDef fleetStatus() {
        return new ToolDef(
                "fleet_status",
                "server_status",
                Role.READER,
                false,
                false,
                true,
                false,
                "Queries health status from all connected backends and aggregates the results.",
                Schemas.object(Map.of(), List.of()),
                List.of());
    }

    private static ToolDef playerLocateDef() {
        return new ToolDef(
                "player_locate",
                "player_locate",
                Role.READER,
                false,
                false,
                false,
                false,
                "Locates an online player and reports their current backend server.",
                Schemas.object(Map.of("uuid", Schemas.str("Player UUID")), List.of("uuid")),
                List.of("uuid"));
    }

    private static final List<ToolDef> CATALOG = List.of(
            // ---- read tools ----
            relay(
                    "server_status",
                    Role.READER,
                    false,
                    false,
                    true,
                    false,
                    "Server health: TPS, tick, uptime, online players, worlds, plugins, heap, restart strategy.",
                    Map.of(),
                    List.of()),
            relay(
                    "world_list",
                    Role.READER,
                    false,
                    false,
                    true,
                    true,
                    "Lists worlds with dimension, spawn and loaded-chunk counts.",
                    Map.of(),
                    List.of()),
            relay(
                    "plugin_list",
                    Role.READER,
                    false,
                    false,
                    true,
                    true,
                    "Lists loaded plugins with version and enabled state.",
                    Map.of(),
                    List.of()),
            relay(
                    "player_list",
                    Role.READER,
                    false,
                    false,
                    true,
                    true,
                    "Lists online players with uuid, name, ping, gamemode, health, food, level and location.",
                    Map.of(),
                    List.of()),
            relay(
                    "player_info",
                    Role.READER,
                    false,
                    true,
                    true,
                    false,
                    "Detailed info for one player by UUID: effects, dimension, operator status.",
                    Map.of("uuid", Schemas.str("Player UUID")),
                    List.of("uuid")),
            relay(
                    "player_stats",
                    Role.READER,
                    false,
                    true,
                    true,
                    false,
                    "Bukkit statistics snapshot for one player by UUID.",
                    Map.of("uuid", Schemas.str("Player UUID")),
                    List.of("uuid")),
            relay(
                    "block_get",
                    Role.READER,
                    false,
                    false,
                    true,
                    true,
                    "Reads a single block: material, block data, biome, light levels, chunk loaded.",
                    Map.of(
                            "world", Schemas.str("World key (name)"),
                            "x", Schemas.integer("X coordinate"),
                            "y", Schemas.integer("Y coordinate"),
                            "z", Schemas.integer("Z coordinate")),
                    List.of("world", "x", "y", "z")),
            relay(
                    "region_get",
                    Role.READER,
                    false,
                    false,
                    true,
                    true,
                    "Bounded block dump for a region.",
                    Map.of(
                            "world", Schemas.str("World key (name)"),
                            "x1", Schemas.integer("Min X"),
                            "y1", Schemas.integer("Min Y"),
                            "z1", Schemas.integer("Min Z"),
                            "x2", Schemas.integer("Max X"),
                            "y2", Schemas.integer("Max Y"),
                            "z2", Schemas.integer("Max Z")),
                    List.of("world", "x1", "y1", "z1", "x2", "y2", "z2")),
            relay(
                    "entity_list",
                    Role.READER,
                    false,
                    false,
                    true,
                    true,
                    "Lists entities in a world, optionally filtered by type, paginated.",
                    Map.of(
                            "world", Schemas.str("World key (name)"),
                            "type", Schemas.str("Optional entity type filter"),
                            "limit", Schemas.integer("Max results (<=100)"),
                            "page", Schemas.integer("Page number (0-based)")),
                    List.of("world")),
            relay(
                    "entity_info",
                    Role.READER,
                    false,
                    false,
                    true,
                    true,
                    "Detailed entity info by UUID: type, position, health, name, vehicle and passengers.",
                    Map.of("uuid", Schemas.str("Entity UUID")),
                    List.of("uuid")),

            // ---- player-targeted ops ----
            relay(
                    "player_message",
                    Role.OPS,
                    false,
                    true,
                    false,
                    false,
                    "Sends a chat message to a player as the console. Formatting codes are stripped unless allowFormatting is true.",
                    Map.of(
                            "uuid", Schemas.str("Player UUID"),
                            "text", Schemas.str("Message text (<=256 chars)"),
                            "allowFormatting", Schemas.bool("Allow Minecraft formatting codes")),
                    List.of("uuid", "text")),
            relay(
                    "player_kick",
                    Role.OPS,
                    true,
                    true,
                    false,
                    false,
                    "Kicks a player with an optional reason.",
                    Map.of(
                            "uuid", Schemas.str("Player UUID"),
                            "reason", Schemas.str("Optional reason"),
                            "confirm", Schemas.confirmSchema()),
                    List.of("uuid", "confirm")),
            relay(
                    "player_teleport",
                    Role.OPS,
                    false,
                    true,
                    false,
                    false,
                    "Teleports a player to coordinates in a world, or to another player.",
                    Map.of(
                            "uuid", Schemas.str("Player UUID"),
                            "targetUuid", Schemas.str("Teleport to this player instead of coordinates"),
                            "world", Schemas.str("World key (with x,y,z)"),
                            "x", Schemas.integer("X coordinate"),
                            "y", Schemas.integer("Y coordinate"),
                            "z", Schemas.integer("Z coordinate")),
                    List.of("uuid")),
            relay(
                    "player_gamemode",
                    Role.OPS,
                    false,
                    true,
                    false,
                    false,
                    "Sets a player's gamemode.",
                    Map.of(
                            "uuid", Schemas.str("Player UUID"),
                            "gamemode", Schemas.str("survival|creative|adventure|spectator")),
                    List.of("uuid", "gamemode")),
            relay(
                    "player_effect",
                    Role.OPS,
                    false,
                    true,
                    false,
                    false,
                    "Applies a potion effect to a player.",
                    Map.of(
                            "uuid", Schemas.str("Player UUID"),
                            "effect", Schemas.str("Effect name (registry key, e.g. speed)"),
                            "durationSeconds", Schemas.integer("Duration in seconds"),
                            "amplifier", Schemas.integer("Amplifier level (0-based)")),
                    List.of("uuid", "effect", "durationSeconds")),

            // ---- admin ----
            relay(
                    "player_ban",
                    Role.ADMIN,
                    true,
                    true,
                    false,
                    false,
                    "Bans a player by UUID with an optional reason.",
                    Map.of(
                            "uuid", Schemas.str("Player UUID"),
                            "reason", Schemas.str("Optional reason"),
                            "confirm", Schemas.confirmSchema()),
                    List.of("uuid", "confirm")),
            relay(
                    "player_unban",
                    Role.ADMIN,
                    true,
                    true,
                    false,
                    false,
                    "Unbans a player by UUID.",
                    Map.of(
                            "uuid", Schemas.str("Player UUID"),
                            "confirm", Schemas.confirmSchema()),
                    List.of("uuid", "confirm")),
            relay(
                    "player_whitelist_add",
                    Role.ADMIN,
                    false,
                    true,
                    false,
                    false,
                    "Adds a player to the whitelist by UUID.",
                    Map.of("uuid", Schemas.str("Player UUID")),
                    List.of("uuid")),
            relay(
                    "player_whitelist_remove",
                    Role.ADMIN,
                    true,
                    true,
                    false,
                    false,
                    "Removes a player from the whitelist by UUID.",
                    Map.of(
                            "uuid", Schemas.str("Player UUID"),
                            "confirm", Schemas.confirmSchema()),
                    List.of("uuid", "confirm")),

            // ---- control ----
            relay(
                    "command_execute",
                    Role.OPS,
                    true,
                    false,
                    false,
                    true,
                    "Executes a server console command, gated by the backend's per-role allowlist.",
                    Map.of(
                            "command", Schemas.str("Console command to run (no leading slash)"),
                            "confirm", Schemas.confirmSchema()),
                    List.of("command", "confirm")),
            relay(
                    "block_set",
                    Role.ADMIN,
                    true,
                    false,
                    false,
                    true,
                    "Sets a single block from the backend's curated material allowlist.",
                    Map.of(
                            "world", Schemas.str("World key (name)"),
                            "x", Schemas.integer("X coordinate"),
                            "y", Schemas.integer("Y coordinate"),
                            "z", Schemas.integer("Z coordinate"),
                            "material", Schemas.str("Block material (registry key)"),
                            "confirm", Schemas.confirmSchema()),
                    List.of("world", "x", "y", "z", "material", "confirm")),
            relay(
                    "server_restart",
                    Role.ADMIN,
                    true,
                    false,
                    false,
                    true,
                    "Restarts a specific backend server using its configured restart strategy.",
                    Map.of(
                            "announce", Schemas.str("Optional broadcast message"),
                            "countdownSeconds", Schemas.integer("Countdown before restart (default 10)"),
                            "confirm", Schemas.confirmSchema()),
                    List.of("confirm")),
            relay(
                    "server_stop",
                    Role.ADMIN,
                    true,
                    false,
                    false,
                    true,
                    "Stops a specific backend server gracefully.",
                    Map.of(
                            "announce", Schemas.str("Optional broadcast message"),
                            "countdownSeconds", Schemas.integer("Countdown before stop (default 10)"),
                            "confirm", Schemas.confirmSchema()),
                    List.of("confirm")),

            // ---- proxy-level ----
            fleetStatus(),
            playerLocateDef());
}
