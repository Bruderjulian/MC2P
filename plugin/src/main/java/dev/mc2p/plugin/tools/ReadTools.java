package dev.mc2p.plugin.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.validate.Validators;
import dev.mc2p.plugin.config.BackendConfig;
import dev.mc2p.plugin.config.BackendConfig.LimitsSection;
import dev.mc2p.plugin.facade.ServerFacade;
import dev.mc2p.plugin.facade.model.Model.BlockInfo;
import dev.mc2p.plugin.facade.model.Model.EntityInfo;
import dev.mc2p.plugin.facade.model.Model.PlayerInfo;
import dev.mc2p.plugin.facade.model.Model.WorldInfo;

/** Registers the read-only tool set. */
public final class ReadTools {

    private ReadTools() {
    }

    public static void register(ToolRegistry registry, ServerFacade facade, BackendConfig config) {
        LimitsSection limits = config.limits();

        registry.register(new ToolSpec("server_status", Role.READER, false, false,
                "Server health: TPS, tick, uptime, online players, worlds, plugins, heap, restart strategy.",
                Schemas.object(Map.of(), List.of()),
                (args, auth) -> facade.status().toMap()));

        registry.register(new ToolSpec("world_list", Role.READER, false, false,
                "Lists worlds with dimension, spawn and loaded-chunk counts.",
                Schemas.object(Map.of(), List.of()),
                (args, auth) -> {
                    List<Map<String, Object>> worlds = new java.util.ArrayList<>();
                    for (WorldInfo w : facade.worlds()) {
                        worlds.add(w.toMap());
                    }
                    return Map.of("serverId", facade.serverId(), "worlds", worlds);
                }));

        registry.register(new ToolSpec("plugin_list", Role.READER, false, false,
                "Lists loaded plugins with version and enabled state.",
                Schemas.object(Map.of(), List.of()),
                (args, auth) -> {
                    List<Map<String, Object>> plugins = new java.util.ArrayList<>();
                    for (var p : facade.plugins()) {
                        plugins.add(p.toMap());
                    }
                    return Map.of("serverId", facade.serverId(), "plugins", plugins);
                }));

        registry.register(new ToolSpec("player_list", Role.READER, false, false,
                "Lists online players with uuid, name, ping, gamemode, health, food, level and location.",
                Schemas.object(Map.of(), List.of()),
                (args, auth) -> {
                    List<Map<String, Object>> players = new java.util.ArrayList<>();
                    for (PlayerInfo p : facade.players()) {
                        players.add(p.toMap());
                    }
                    return Map.of("serverId", facade.serverId(), "players", players);
                }));

        registry.register(new ToolSpec("player_info", Role.READER, false, false,
                "Detailed info for one player by UUID: effects, dimension, operator status.",
                Schemas.object(Map.of("uuid", Schemas.str("Player UUID")), List.of("uuid")),
                (args, auth) -> facade.playerInfo(Args.uuid(args, "uuid")).toMap()));

        registry.register(new ToolSpec("player_stats", Role.READER, false, false,
                "Bukkit statistics snapshot for one player by UUID.",
                Schemas.object(Map.of("uuid", Schemas.str("Player UUID")), List.of("uuid")),
                (args, auth) -> {
                    if (!config.features().stats()) {
                        throw new ToolException("player stats are disabled by configuration (features.stats)");
                    }
                    return facade.playerStats(Args.uuid(args, "uuid")).toMap();
                }));

        registry.register(new ToolSpec("block_get", Role.READER, false, false,
                "Reads a single block: material, block data, biome, light levels, chunk loaded.",
                Schemas.object(Map.of(
                        "world", Schemas.str("World key (name)"),
                        "x", Schemas.integer("X coordinate"),
                        "y", Schemas.integer("Y coordinate"),
                        "z", Schemas.integer("Z coordinate")), List.of("world", "x", "y", "z")),
                (args, auth) -> {
                    String world = requireWorld(facade, args);
                    int[] xyz = requireCoords(args, limits.maxCoordinate());
                    return facade.blockAt(world, xyz[0], xyz[1], xyz[2]).toMap();
                }));

        registry.register(new ToolSpec("region_get", Role.READER, false, false,
                "Bounded block dump for a region (max " + "blocks enforced by configuration).",
                Schemas.object(Map.of(
                        "world", Schemas.str("World key (name)"),
                        "x1", Schemas.integer("Min X"),
                        "y1", Schemas.integer("Min Y"),
                        "z1", Schemas.integer("Min Z"),
                        "x2", Schemas.integer("Max X"),
                        "y2", Schemas.integer("Max Y"),
                        "z2", Schemas.integer("Max Z")),
                        List.of("world", "x1", "y1", "z1", "x2", "y2", "z2")),
                (args, auth) -> {
                    String world = requireWorld(facade, args);
                    int max = limits.maxCoordinate();
                    int x1 = Args.integer(args, "x1", 0);
                    int y1 = Args.integer(args, "y1", 0);
                    int z1 = Args.integer(args, "z1", 0);
                    int x2 = Args.integer(args, "x2", 0);
                    int y2 = Args.integer(args, "y2", 0);
                    int z2 = Args.integer(args, "z2", 0);
                    for (int v : new int[] { x1, y1, z1, x2, y2, z2 }) {
                        if (!Validators.isWithinCoordinate(v, max)) {
                            throw new ToolException("coordinate out of bounds (±" + max + ")");
                        }
                    }
                    int[] region = { Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                            Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2) };
                    List<BlockInfo> blocks = facade.region(world, region[0], region[1], region[2], region[3],
                            region[4], region[5], limits.maxRegionBlocks());
                    return Map.of("serverId", facade.serverId(), "world", world, "count", blocks.size(),
                            "blocks", blocks.stream().map(BlockInfo::toMap).toList());
                }));

        registry.register(new ToolSpec("entity_list", Role.READER, false, false,
                "Lists entities in a world, optionally filtered by type, paginated.",
                Schemas.object(Map.of(
                        "world", Schemas.str("World key (name)"),
                        "type", Schemas.str("Optional entity type filter"),
                        "limit", Schemas.integer("Max results (<=100)"),
                        "page", Schemas.integer("Page number (0-based)")),
                        List.of("world")),
                (args, auth) -> {
                    String world = requireWorld(facade, args);
                    String type = Args.string(args, "type");
                    if (type != null && !type.isBlank() && !Validators.isSafeEntityType(type)) {
                        throw new ToolException("invalid entity type");
                    }
                    int limit = Args.integer(args, "limit", limits.maxEntityLimit());
                    if (!Validators.isValidLimit(limit, limits.maxEntityLimit())) {
                        throw new ToolException("limit must be between 1 and " + limits.maxEntityLimit());
                    }
                    int page = Args.integer(args, "page", 0);
                    if (!Validators.isValidPage(page, limit)) {
                        throw new ToolException("invalid page");
                    }
                    List<EntityInfo> entities = facade.entities(world, type, limit, page);
                    return Map.of("serverId", facade.serverId(), "world", world, "count", entities.size(),
                            "entities", entities.stream().map(EntityInfo::toMap).toList());
                }));

        registry.register(new ToolSpec("entity_info", Role.READER, false, false,
                "Detailed entity info by UUID: type, position, health, name, vehicle and passengers.",
                Schemas.object(Map.of("uuid", Schemas.str("Entity UUID")), List.of("uuid")),
                (args, auth) -> facade.entityInfo(Args.uuid(args, "uuid")).toMap()));
    }

    private static String requireWorld(ServerFacade facade, Map<String, Object> args) throws ToolException {
        String world = Args.requiredString(args, "world");
        if (!Validators.isSafeWorldKey(world)) {
            throw new ToolException("invalid world key");
        }
        if (!facade.worldExists(world)) {
            throw new ToolException("unknown world: " + world);
        }
        return world;
    }

    private static int[] requireCoords(Map<String, Object> args, int maxCoord) throws ToolException {
        int x = Args.integer(args, "x", 0);
        int y = Args.integer(args, "y", 0);
        int z = Args.integer(args, "z", 0);
        if (!Validators.isWithinCoordinate(x, maxCoord) || !Validators.isWithinCoordinate(y, maxCoord)
                || !Validators.isWithinCoordinate(z, maxCoord)) {
            throw new ToolException("coordinate out of bounds (±" + maxCoord + ")");
        }
        return new int[] { x, y, z };
    }
}