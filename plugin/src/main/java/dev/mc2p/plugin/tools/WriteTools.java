package dev.mc2p.plugin.tools;

import dev.mc2p.common.exceptions.FacadeException;
import dev.mc2p.common.exceptions.ToolException;
import dev.mc2p.common.json.Schemas;
import dev.mc2p.common.validate.Args;
import dev.mc2p.common.validate.Validators;
import dev.mc2p.plugin.config.BackendConfig;
import dev.mc2p.plugin.config.BackendConfig.LimitsSection;
import dev.mc2p.plugin.facade.ServerFacade;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class WriteTools {

        private WriteTools() {
        }

        public static void register(final ToolRegistry registry, final ServerFacade facade,
                        final BackendConfig config) {
                final LimitsSection limits = config.limits();

                registry.register(new ToolSpec(
                                "player_message",
                                false,
                                false,
                                "Sends a chat message to a player as the console. Formatting codes are stripped unless allowFormatting is true.",
                                Schemas.object(
                                                Map.of(
                                                                "uuid",
                                                                Schemas.str("Player UUID"),
                                                                "text",
                                                                Schemas.str("Message text (<=256 chars)"),
                                                                "allowFormatting",
                                                                Schemas.bool("Allow Minecraft formatting codes")),
                                                List.of("uuid", "text")),
                                (args, auth) -> {
                                        final UUID uuid = Args.requiredUUID(args, "uuid");
                                        final String text = Args.requiredString(args, "text");
                                        if (text.length() > 256) {
                                                throw new ToolException("message too long (max 256)");
                                        }
                                        facade.messagePlayer(uuid, text, Args.bool(args, "allowFormatting", true));
                                        return Map.of("serverId", facade.serverId(), "sent", true);
                                }));

                registry.register(new ToolSpec(
                                "player_kick",
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
                                        final UUID uuid = Args.requiredUUID(args, "uuid");
                                        final String reason = Args.string(args, "reason", null);
                                        if (reason != null && !Validators.isSafeReason(reason)) {
                                                throw new ToolException("reason too long (max 256)");
                                        }
                                        facade.kickPlayer(uuid, reason);
                                        return Map.of("serverId", facade.serverId(), "kicked", true);
                                }));

                registry.register(new ToolSpec(
                                "player_teleport",
                                false,
                                false,
                                "Teleports a player to coordinates in a world, or to another player.",
                                Schemas.object(
                                                Map.of(
                                                                "uuid",
                                                                Schemas.str("Player UUID"),
                                                                "targetUuid",
                                                                Schemas.str("Teleport to this player instead of coordinates"),
                                                                "world",
                                                                Schemas.str("World key (with x,y,z)"),
                                                                "x",
                                                                Schemas.integer("X coordinate"),
                                                                "y",
                                                                Schemas.integer("Y coordinate"),
                                                                "z",
                                                                Schemas.integer("Z coordinate")),
                                                List.of("uuid")),
                                (args, auth) -> {
                                        final UUID uuid = Args.requiredUUID(args, "uuid");
                                        final UUID target = Args.optionalUuid(args, "targetUuid");
                                        if (target != null) {
                                                facade.teleport(uuid, target);
                                        }
                                        final String worldKey = Args.string(args, "world", null);
                                        if (worldKey == null || !Validators.isSafeWorldKey(worldKey)
                                                        || !facade.worldExists(worldKey)) {
                                                throw new ToolException("world is required and must exist");
                                        }
                                        if (!auth.restrictions().isWorldAllowed(worldKey)) {
                                                throw new ToolException("world '" + worldKey
                                                                + "' is not allowed for this token");
                                        }
                                        final World world = Bukkit.getWorld(worldKey);
                                        if (world == null) {
                                                throw new FacadeException("unknown world: " + worldKey);
                                        }
                                        final int max = limits.maxCoordinate();
                                        final int x = Args.integer(args, "x", 0);
                                        final int y = Args.integer(args, "y", 0);
                                        final int z = Args.integer(args, "z", 0);
                                        if (!Validators.isWithinCoordinate(x, max)
                                                        || !Validators.isWithinCoordinate(y, max)
                                                        || !Validators.isWithinCoordinate(z, max)) {
                                                throw new ToolException("coordinate out of bounds (±" + max + ")");
                                        }
                                        facade.teleport(uuid, new Location(world, x + 0.5, y, z + 0.5));
                                        return Map.of("serverId", facade.serverId(), "teleported", true);
                                }));

                registry.register(new ToolSpec(
                                "player_gamemode",
                                false,
                                false,
                                "Sets a player's gamemode.",
                                Schemas.object(
                                                Map.of(
                                                                "uuid",
                                                                Schemas.str("Player UUID"),
                                                                "gamemode",
                                                                Schemas.str("survival|creative|adventure|spectator")),
                                                List.of("uuid", "gamemode")),
                                (args, auth) -> {
                                        final UUID uuid = Args.requiredUUID(args, "uuid");
                                        final String gamemode = Validators
                                                        .normalizeGamemode(Args.requiredString(args, "gamemode"));
                                        if (gamemode == null) {
                                                throw new ToolException("invalid gamemode");
                                        }
                                        facade.setGamemode(uuid, gamemode);
                                        return Map.of("serverId", facade.serverId(), "gamemode",
                                                        gamemode.toLowerCase());
                                }));

                registry.register(new ToolSpec(
                                "player_effect",
                                false,
                                false,
                                "Applies a potion effect to a player.",
                                Schemas.object(
                                                Map.of(
                                                                "uuid",
                                                                Schemas.str("Player UUID"),
                                                                "effect",
                                                                Schemas.str("Effect name (registry key, e.g. speed)"),
                                                                "durationSeconds",
                                                                Schemas.integer("Duration in seconds"),
                                                                "amplifier",
                                                                Schemas.integer("Amplifier level (0-based)")),
                                                List.of("uuid", "effect", "durationSeconds")),
                                (args, auth) -> {
                                        final UUID uuid = Args.requiredUUID(args, "uuid");
                                        final String effect = Args.requiredString(args, "effect");
                                        if (!Validators.isSafeEffectName(effect)) {
                                                throw new ToolException("invalid effect name");
                                        }
                                        final int duration = Args.integer(args, "durationSeconds", 60);
                                        if (!Validators.isWithin(duration, 1, 3600)) {
                                                throw new ToolException("duration must be 1..3600 seconds");
                                        }
                                        final int amplifier = Args.integer(args, "amplifier", 0);
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
                                        final UUID uuid = Args.requiredUUID(args, "uuid");
                                        final String reason = Args.string(args, "reason", null);
                                        if (reason != null && !Validators.isSafeReason(reason)) {
                                                throw new ToolException("reason too long (max 256)");
                                        }
                                        facade.ban(uuid, reason);
                                        return Map.of("serverId", facade.serverId(), "banned", true);
                                }));

                registry.register(new ToolSpec(
                                "player_unban",
                                true,
                                true,
                                "Unbans a player by UUID.",
                                Schemas.object(
                                                Map.of(
                                                                "uuid", Schemas.str("Player UUID"),
                                                                "confirm", Schemas.confirmSchema()),
                                                List.of("uuid", "confirm")),
                                (args, auth) -> {
                                        facade.unban(Args.requiredUUID(args, "uuid"));
                                        return Map.of("serverId", facade.serverId(), "unbanned", true);
                                }));

                registry.register(new ToolSpec(
                                "player_whitelist_add",
                                false,
                                false,
                                "Adds a player to the whitelist by UUID.",
                                Schemas.object(Map.of("uuid", Schemas.str("Player UUID")), List.of("uuid")),
                                (args, auth) -> {
                                        facade.whitelistAdd(Args.requiredUUID(args, "uuid"));
                                        return Map.of("serverId", facade.serverId(), "whitelisted", true);
                                }));

                registry.register(new ToolSpec(
                                "player_whitelist_remove",
                                true,
                                true,
                                "Removes a player from the whitelist by UUID.",
                                Schemas.object(
                                                Map.of(
                                                                "uuid", Schemas.str("Player UUID"),
                                                                "confirm", Schemas.confirmSchema()),
                                                List.of("uuid", "confirm")),
                                (args, auth) -> {
                                        facade.whitelistRemove(Args.requiredUUID(args, "uuid"));
                                        return Map.of("serverId", facade.serverId(), "unwhitelisted", true);
                                }));

                registry.register(new ToolSpec(
                                "command_execute",
                                true,
                                true,
                                "Executes a server console command, gated by the per-role allowlist and the global deny list.",
                                Schemas.object(
                                                Map.of(
                                                                "command",
                                                                Schemas.str("Console command to run (no leading slash)"),
                                                                "confirm",
                                                                Schemas.confirmSchema()),
                                                List.of("command", "confirm")),
                                (args, auth) -> {
                                        final String command = Args.requiredString(args, "command");
                                        if (!auth.restrictions().isCommandAllowed(command)) {
                                                throw new ToolException("command is not allowed for this token");
                                        }
                                        return facade.executeCommand(command).toMap();
                                }));

                registry.register(new ToolSpec(
                                "block_set",
                                true,
                                true,
                                "Sets a single block from a curated material allowlist.",
                                Schemas.object(
                                                Map.of(
                                                                "world",
                                                                Schemas.str("World key (name)"),
                                                                "x",
                                                                Schemas.integer("X coordinate"),
                                                                "y",
                                                                Schemas.integer("Y coordinate"),
                                                                "z",
                                                                Schemas.integer("Z coordinate"),
                                                                "material",
                                                                Schemas.str("Block material (registry key)"),
                                                                "confirm",
                                                                Schemas.confirmSchema()),
                                                List.of("world", "x", "y", "z", "material", "confirm")),
                                (args, auth) -> {
                                        final String world = Args.requiredString(args, "world");
                                        if (!Validators.isSafeWorldKey(world) || !facade.worldExists(world)) {
                                                throw new ToolException("invalid or unknown world");
                                        }
                                        if (!auth.restrictions().isWorldAllowed(world)) {
                                                throw new ToolException(
                                                                "world '" + world + "' is not allowed for this token");
                                        }
                                        final int max = limits.maxCoordinate();
                                        final int x = Args.integer(args, "x", 0);
                                        final int y = Args.integer(args, "y", 0);
                                        final int z = Args.integer(args, "z", 0);
                                        if (!Validators.isWithinCoordinate(x, max)
                                                        || !Validators.isWithinCoordinate(y, max)
                                                        || !Validators.isWithinCoordinate(z, max)) {
                                                throw new ToolException("coordinate out of bounds (±" + max + ")");
                                        }
                                        final String material = Args.requiredString(args, "material").toLowerCase();
                                        if (!Validators.isSafeMaterialName(material)) {
                                                throw new ToolException("material is not in the block_set allowlist");
                                        }
                                        facade.setBlock(world, x, y, z, material);
                                        return Map.of("serverId", facade.serverId(), "set", true, "material", material);
                                }));

                registry.register(new ToolSpec(
                                "server_restart",
                                true,
                                true,
                                "Restarts the server using the configured restart strategy. Reports if restart is unavailable.",
                                Schemas.object(
                                                Map.of(
                                                                "announce",
                                                                Schemas.str("Optional broadcast message"),
                                                                "countdownSeconds",
                                                                Schemas.integer("Countdown before restart (default 10)"),
                                                                "confirm",
                                                                Schemas.confirmSchema()),
                                                List.of("confirm")),
                                (args, auth) -> {
                                        if (!facade.restartAvailable()) {
                                                throw new ToolException(
                                                                "restart is not available on this host: "
                                                                                + facade.restartStrategyHealth());
                                        }
                                        final int countdown = Args.integer(args, "countdownSeconds", 10);
                                        if (!Validators.isWithin(countdown, 1, 300)) {
                                                throw new ToolException("countdownSeconds must be 1..300");
                                        }
                                        facade.scheduleRestart(
                                                        Args.string(args, "announce",
                                                                        "Server is restarting in " + countdown),
                                                        countdown);
                                        return Map.of("serverId", facade.serverId(), "restarting", true,
                                                        "countdownSeconds", countdown);
                                }));

                registry.register(new ToolSpec(
                                "server_stop",
                                true,
                                true,
                                "Stops the server gracefully. For hosts that auto-restart on exit this doubles as a restart.",
                                Schemas.object(
                                                Map.of(
                                                                "announce",
                                                                Schemas.str("Optional broadcast message"),
                                                                "countdownSeconds",
                                                                Schemas.integer("Countdown before stop (default 10)"),
                                                                "confirm",
                                                                Schemas.confirmSchema()),
                                                List.of("confirm")),
                                (args, auth) -> {
                                        final int countdown = Args.integer(args, "countdownSeconds", 10);
                                        if (!Validators.isWithin(countdown, 1, 300)) {
                                                throw new ToolException("countdownSeconds must be 1..300");
                                        }
                                        facade.scheduleStop(Args.string(args, "announce",
                                                        "Server is stopping in " + countdown), countdown);
                                        return Map.of("serverId", facade.serverId(), "stopping", true,
                                                        "countdownSeconds", countdown);
                                }));
        }
}
