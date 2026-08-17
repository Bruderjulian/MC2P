package dev.mc2p.plugin.tools;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.validate.CommandPolicy;
import dev.mc2p.common.validate.Validators;
import dev.mc2p.plugin.config.BackendConfig;
import dev.mc2p.plugin.config.BackendConfig.LimitsSection;
import dev.mc2p.plugin.facade.ServerFacade;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Registers the mutating tool set (ops/admin). */
public final class WriteTools {

    /** Curated safe block set for {@code block_set}; deliberately excludes griefing and admin blocks. */
    public static final Set<String> SAFE_BLOCK_MATERIALS = Set.of(
            "stone",
            "cobblestone",
            "dirt",
            "grass_block",
            "sand",
            "gravel",
            "oak_planks",
            "spruce_planks",
            "birch_planks",
            "jungle_planks",
            "acacia_planks",
            "dark_oak_planks",
            "oak_log",
            "spruce_log",
            "birch_log",
            "glass",
            "glass_pane",
            "bricks",
            "stone_bricks",
            "mossy_stone_bricks",
            "cobblestone_wall",
            "oak_fence",
            "oak_fence_gate",
            "oak_door",
            "oak_trapdoor",
            "oak_stairs",
            "stone_slab",
            "oak_slab",
            "torch",
            "lantern",
            "sea_lantern",
            "glowstone",
            "wheat",
            "wheat_seeds",
            "carrots",
            "potatoes",
            "water",
            "lava",
            "ice",
            "packed_ice",
            "snow_block",
            "snow",
            "moss_block",
            "azalea",
            "flowering_azalea",
            "pumpkin",
            "melon",
            "sweet_berry_bush",
            "white_wool",
            "gray_wool",
            "black_wool",
            "coal_ore",
            "iron_ore",
            "gold_ore",
            "copper_ore",
            "diamond_ore",
            "emerald_ore",
            "coal_block",
            "iron_block",
            "gold_block",
            "diamond_block",
            "emerald_block",
            "redstone_lamp",
            "lightning_rod",
            "note_block",
            "jukebox",
            "candle",
            "chain",
            "ladder",
            "scaffolding",
            "tnt" /* unignited only; set requires confirm + admin */);

    private WriteTools() {}

    public static void register(ToolRegistry registry, ServerFacade facade, BackendConfig config) {
        LimitsSection limits = config.limits();
        CommandPolicy commandPolicy = new CommandPolicy(
                config.commands().opsAllowlist(),
                config.commands().adminAllowlist(),
                config.commands().deny(),
                limits.maxCommandLength());

        registry.register(new ToolSpec(
                "player_message",
                Role.OPS,
                false,
                false,
                "Sends a chat message to a player as the console. Formatting codes are stripped unless allowFormatting is true.",
                Schemas.object(
                        Map.of(
                                "uuid", Schemas.str("Player UUID"),
                                "text", Schemas.str("Message text (<=256 chars)"),
                                "allowFormatting", Schemas.bool("Allow Minecraft formatting codes")),
                        List.of("uuid", "text")),
                (args, auth) -> {
                    var uuid = Args.uuid(args, "uuid");
                    String text = Args.requiredString(args, "text");
                    if (text.length() > 256) {
                        throw new ToolException("message too long (max 256)");
                    }
                    facade.messagePlayer(uuid, text, Args.bool(args, "allowFormatting"));
                    return Map.of("serverId", facade.serverId(), "sent", true);
                }));

        registry.register(new ToolSpec(
                "player_kick",
                Role.OPS,
                true,
                true,
                "Kicks a player with an optional reason.",
                Schemas.object(
                        Map.of(
                                "uuid", Schemas.str("Player UUID"),
                                "reason", Schemas.str("Optional reason"),
                                "confirm", Schemas.confirmSchema()),
                        List.of("uuid", "confirm")),
                (args, auth) -> {
                    var uuid = Args.uuid(args, "uuid");
                    String reason = Args.string(args, "reason");
                    if (reason != null && !Validators.isSafeReason(reason)) {
                        throw new ToolException("reason too long (max 256)");
                    }
                    facade.kickPlayer(uuid, reason);
                    return Map.of("serverId", facade.serverId(), "kicked", true);
                }));

        registry.register(new ToolSpec(
                "player_teleport",
                Role.OPS,
                false,
                false,
                "Teleports a player to coordinates in a world, or to another player.",
                Schemas.object(
                        Map.of(
                                "uuid", Schemas.str("Player UUID"),
                                "targetUuid", Schemas.str("Teleport to this player instead of coordinates"),
                                "world", Schemas.str("World key (with x,y,z)"),
                                "x", Schemas.integer("X coordinate"),
                                "y", Schemas.integer("Y coordinate"),
                                "z", Schemas.integer("Z coordinate")),
                        List.of("uuid")),
                (args, auth) -> {
                    var uuid = Args.uuid(args, "uuid");
                    UUID target = Args.optionalUuid(args, "targetUuid");
                    int[] coords = null;
                    String world = null;
                    if (target == null) {
                        world = Args.string(args, "world");
                        if (world == null || !Validators.isSafeWorldKey(world) || !facade.worldExists(world)) {
                            throw new ToolException("world is required and must exist");
                        }
                        int max = limits.maxCoordinate();
                        int x = Args.integer(args, "x", 0);
                        int y = Args.integer(args, "y", 0);
                        int z = Args.integer(args, "z", 0);
                        if (!Validators.isWithinCoordinate(x, max)
                                || !Validators.isWithinCoordinate(y, max)
                                || !Validators.isWithinCoordinate(z, max)) {
                            throw new ToolException("coordinate out of bounds (±" + max + ")");
                        }
                        coords = new int[] {x, y, z};
                    }
                    facade.teleport(uuid, coords, world, target);
                    return Map.of("serverId", facade.serverId(), "teleported", true);
                }));

        registry.register(new ToolSpec(
                "player_gamemode",
                Role.OPS,
                false,
                false,
                "Sets a player's gamemode.",
                Schemas.object(
                        Map.of(
                                "uuid", Schemas.str("Player UUID"),
                                "gamemode", Schemas.str("survival|creative|adventure|spectator")),
                        List.of("uuid", "gamemode")),
                (args, auth) -> {
                    var uuid = Args.uuid(args, "uuid");
                    String gamemode = Validators.normalizeGamemode(Args.requiredString(args, "gamemode"));
                    if (gamemode == null) {
                        throw new ToolException("invalid gamemode");
                    }
                    facade.setGamemode(uuid, gamemode);
                    return Map.of("serverId", facade.serverId(), "gamemode", gamemode.toLowerCase());
                }));

        registry.register(new ToolSpec(
                "player_effect",
                Role.OPS,
                false,
                false,
                "Applies a potion effect to a player.",
                Schemas.object(
                        Map.of(
                                "uuid", Schemas.str("Player UUID"),
                                "effect", Schemas.str("Effect name (registry key, e.g. speed)"),
                                "durationSeconds", Schemas.integer("Duration in seconds"),
                                "amplifier", Schemas.integer("Amplifier level (0-based)")),
                        List.of("uuid", "effect", "durationSeconds")),
                (args, auth) -> {
                    var uuid = Args.uuid(args, "uuid");
                    String effect = Args.requiredString(args, "effect");
                    if (!Validators.isSafeEffectName(effect)) {
                        throw new ToolException("invalid effect name");
                    }
                    int duration = Args.integer(args, "durationSeconds", 60);
                    if (!Validators.isWithin(duration, 1, 3600)) {
                        throw new ToolException("duration must be 1..3600 seconds");
                    }
                    int amplifier = Args.integer(args, "amplifier", 0);
                    if (!Validators.isWithin(amplifier, 0, 255)) {
                        throw new ToolException("amplifier must be 0..255");
                    }
                    facade.applyEffect(uuid, effect, duration, amplifier);
                    return Map.of(
                            "serverId",
                            facade.serverId(),
                            "effect",
                            effect,
                            "durationSeconds",
                            duration,
                            "amplifier",
                            amplifier);
                }));

        registry.register(new ToolSpec(
                "player_ban",
                Role.ADMIN,
                true,
                true,
                "Bans a player by UUID with an optional reason.",
                Schemas.object(
                        Map.of(
                                "uuid", Schemas.str("Player UUID"),
                                "reason", Schemas.str("Optional reason"),
                                "confirm", Schemas.confirmSchema()),
                        List.of("uuid", "confirm")),
                (args, auth) -> {
                    var uuid = Args.uuid(args, "uuid");
                    String reason = Args.string(args, "reason");
                    if (reason != null && !Validators.isSafeReason(reason)) {
                        throw new ToolException("reason too long (max 256)");
                    }
                    facade.ban(uuid, reason);
                    return Map.of("serverId", facade.serverId(), "banned", true);
                }));

        registry.register(new ToolSpec(
                "player_unban",
                Role.ADMIN,
                true,
                true,
                "Unbans a player by UUID.",
                Schemas.object(
                        Map.of(
                                "uuid", Schemas.str("Player UUID"),
                                "confirm", Schemas.confirmSchema()),
                        List.of("uuid", "confirm")),
                (args, auth) -> {
                    var uuid = Args.uuid(args, "uuid");
                    facade.unban(uuid);
                    return Map.of("serverId", facade.serverId(), "unbanned", true);
                }));

        registry.register(new ToolSpec(
                "player_whitelist_add",
                Role.ADMIN,
                false,
                false,
                "Adds a player to the whitelist by UUID.",
                Schemas.object(Map.of("uuid", Schemas.str("Player UUID")), List.of("uuid")),
                (args, auth) -> {
                    var uuid = Args.uuid(args, "uuid");
                    facade.whitelistAdd(uuid);
                    return Map.of("serverId", facade.serverId(), "whitelisted", true);
                }));

        registry.register(new ToolSpec(
                "player_whitelist_remove",
                Role.ADMIN,
                true,
                true,
                "Removes a player from the whitelist by UUID.",
                Schemas.object(
                        Map.of(
                                "uuid", Schemas.str("Player UUID"),
                                "confirm", Schemas.confirmSchema()),
                        List.of("uuid", "confirm")),
                (args, auth) -> {
                    var uuid = Args.uuid(args, "uuid");
                    facade.whitelistRemove(uuid);
                    return Map.of("serverId", facade.serverId(), "unwhitelisted", true);
                }));

        registry.register(new ToolSpec(
                "command_execute",
                Role.OPS,
                true,
                true,
                "Executes a server console command, gated by the per-role allowlist and the global deny list.",
                Schemas.object(
                        Map.of(
                                "command", Schemas.str("Console command to run (no leading slash)"),
                                "confirm", Schemas.confirmSchema()),
                        List.of("command", "confirm")),
                (args, auth) -> {
                    String command = Args.requiredString(args, "command");
                    String reason = commandPolicy.rejectionReason(command, auth.role());
                    if (reason != null) {
                        throw new ToolException("command rejected: " + reason);
                    }
                    return facade.executeCommand(command).toMap();
                }));

        if (config.features().blockEdit()) {
            registry.register(new ToolSpec(
                    "block_set",
                    Role.ADMIN,
                    true,
                    true,
                    "Sets a single block from a curated material allowlist.",
                    Schemas.object(
                            Map.of(
                                    "world", Schemas.str("World key (name)"),
                                    "x", Schemas.integer("X coordinate"),
                                    "y", Schemas.integer("Y coordinate"),
                                    "z", Schemas.integer("Z coordinate"),
                                    "material", Schemas.str("Block material (registry key)"),
                                    "confirm", Schemas.confirmSchema()),
                            List.of("world", "x", "y", "z", "material", "confirm")),
                    (args, auth) -> {
                        String world = Args.requiredString(args, "world");
                        if (!Validators.isSafeWorldKey(world) || !facade.worldExists(world)) {
                            throw new ToolException("invalid or unknown world");
                        }
                        int max = limits.maxCoordinate();
                        int x = Args.integer(args, "x", 0);
                        int y = Args.integer(args, "y", 0);
                        int z = Args.integer(args, "z", 0);
                        if (!Validators.isWithinCoordinate(x, max)
                                || !Validators.isWithinCoordinate(y, max)
                                || !Validators.isWithinCoordinate(z, max)) {
                            throw new ToolException("coordinate out of bounds (±" + max + ")");
                        }
                        String material = Args.requiredString(args, "material").toLowerCase();
                        if (!Validators.isSafeMaterialName(material) || !SAFE_BLOCK_MATERIALS.contains(material)) {
                            throw new ToolException("material is not in the block_set allowlist");
                        }
                        facade.setBlock(world, x, y, z, material);
                        return Map.of("serverId", facade.serverId(), "set", true, "material", material);
                    }));
        }

        registry.register(new ToolSpec(
                "server_restart",
                Role.ADMIN,
                true,
                true,
                "Restarts the server using the configured restart strategy. Reports if restart is unavailable.",
                Schemas.object(
                        Map.of(
                                "announce", Schemas.str("Optional broadcast message"),
                                "countdownSeconds", Schemas.integer("Countdown before restart (default 10)"),
                                "confirm", Schemas.confirmSchema()),
                        List.of("confirm")),
                (args, auth) -> {
                    if (!facade.restartAvailable()) {
                        throw new ToolException(
                                "restart is not available on this host: " + facade.restartStrategyHealth());
                    }
                    int countdown = Args.integer(args, "countdownSeconds", 10);
                    if (!Validators.isWithin(countdown, 1, 300)) {
                        throw new ToolException("countdownSeconds must be 1..300");
                    }
                    facade.scheduleRestart(Args.string(args, "announce"), countdown);
                    return Map.of("serverId", facade.serverId(), "restarting", true, "countdownSeconds", countdown);
                }));

        registry.register(new ToolSpec(
                "server_stop",
                Role.ADMIN,
                true,
                true,
                "Stops the server gracefully. For hosts that auto-restart on exit this doubles as a restart.",
                Schemas.object(
                        Map.of(
                                "announce", Schemas.str("Optional broadcast message"),
                                "countdownSeconds", Schemas.integer("Countdown before stop (default 10)"),
                                "confirm", Schemas.confirmSchema()),
                        List.of("confirm")),
                (args, auth) -> {
                    int countdown = Args.integer(args, "countdownSeconds", 10);
                    if (!Validators.isWithin(countdown, 1, 300)) {
                        throw new ToolException("countdownSeconds must be 1..300");
                    }
                    facade.scheduleStop(Args.string(args, "announce"), countdown);
                    return Map.of("serverId", facade.serverId(), "stopping", true, "countdownSeconds", countdown);
                }));
    }
}
