package dev.mc2p.plugin.facade;

import dev.mc2p.common.exceptions.FacadeException;
import dev.mc2p.common.facade.Model.BlockInfo;
import dev.mc2p.common.facade.Model.CommandResult;
import dev.mc2p.common.facade.Model.EntityDetails;
import dev.mc2p.common.facade.Model.EntityInfo;
import dev.mc2p.common.facade.Model.PlayerDetails;
import dev.mc2p.common.facade.Model.PlayerInfo;
import dev.mc2p.common.facade.Model.PluginInfo;
import dev.mc2p.common.facade.Model.StatsInfo;
import dev.mc2p.common.facade.Model.Status;
import dev.mc2p.common.facade.Model.Tps;
import dev.mc2p.common.facade.Model.WorldInfo;
import dev.mc2p.common.facade.ServerFacade;
import dev.mc2p.common.validate.Utils;
import dev.mc2p.plugin.thread.MainThread;
import io.papermc.paper.ban.BanListType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.block.Block;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bukkit/Paper implementation of {@link ServerFacade}. Every entry point runs
 * on the main thread.
 */
public final class PaperServerFacade implements ServerFacade {

    private static final Logger log = LoggerFactory.getLogger(PaperServerFacade.class);

    private final Plugin plugin;
    private final MainThread mainThread;
    private final String serverId;
    private final String restartStrategy;
    private final long startedAt = System.currentTimeMillis();

    public PaperServerFacade(
            final Plugin plugin, final MainThread mainThread, final String serverId, final String restartStrategy) {
        this.plugin = plugin;
        this.mainThread = mainThread;
        this.serverId = serverId;
        this.restartStrategy = restartStrategy;
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public String pluginVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public String minecraftVersion() {
        return Bukkit.getMinecraftVersion();
    }

    @Override
    public boolean onlineMode() {
        return Bukkit.getOnlineMode();
    }

    @Override
    public boolean isStopping() {
        return false;
    }

    @Override
    public Status status() {
        return mainThread.call(() -> {
            final double[] tpsArray = safeTps();
            final Tps tps = new Tps(
                    tpsArray.length > 0 ? tpsArray[0] : 20.0,
                    tpsArray.length > 1 ? tpsArray[1] : 20.0,
                    tpsArray.length > 2 ? tpsArray[2] : 20.0);
            final List<String> worlds = new ArrayList<>();
            for (final World w : Bukkit.getWorlds()) {
                worlds.add(w.getName());
            }
            final List<String> plugins = new ArrayList<>();
            for (final Plugin p : Bukkit.getPluginManager().getPlugins()) {
                plugins.add(p.getName());
            }
            final Runtime rt = Runtime.getRuntime();
            return new Status(
                    serverId,
                    Bukkit.getMinecraftVersion(),
                    Bukkit.getVersion(),
                    plugin.getPluginMeta().getVersion(),
                    tps,
                    safeTick(),
                    (System.currentTimeMillis() - startedAt) / 1000,
                    Bukkit.getOnlinePlayers().size(),
                    Bukkit.getMaxPlayers(),
                    Bukkit.hasWhitelist(),
                    Bukkit.getOnlineMode(),
                    worlds,
                    plugins,
                    rt.totalMemory() - rt.freeMemory(),
                    rt.maxMemory(),
                    restartStrategy,
                    restartAvailable());
        });
    }

    @Override
    public List<WorldInfo> worlds() {
        return mainThread.call(() -> {
            final List<WorldInfo> result = new ArrayList<>();
            for (final World w : Bukkit.getWorlds()) {
                result.add(new WorldInfo(
                        w.getName(),
                        dimensionName(w.getEnvironment()),
                        new int[] {
                                w.getSpawnLocation().getBlockX(),
                                w.getSpawnLocation().getBlockY(),
                                w.getSpawnLocation().getBlockZ()
                        },
                        w.getLoadedChunks().length));
            }
            return result;
        });
    }

    @Override
    public List<PluginInfo> plugins() {
        return mainThread.call(() -> {
            final List<PluginInfo> result = new ArrayList<>();
            for (final Plugin p : Bukkit.getPluginManager().getPlugins()) {
                result.add(new PluginInfo(p.getName(), p.getPluginMeta().getVersion(), p.isEnabled()));
            }
            return result;
        });
    }

    @Override
    public List<PlayerInfo> players() {
        return mainThread.call(() -> {
            final List<PlayerInfo> result = new ArrayList<>();
            for (final Player p : Bukkit.getOnlinePlayers()) {
                result.add(playerInfoOf(p));
            }
            result.sort(java.util.Comparator.comparing(PlayerInfo::name));
            return result;
        });
    }

    private static PlayerInfo playerInfoOf(final Player p) {
        return new PlayerInfo(
                p.getUniqueId(),
                p.getName(),
                safePing(p),
                p.getGameMode().name().toLowerCase(Locale.ROOT),
                p.getHealth(),
                p.getFoodLevel(),
                p.getLevel(),
                p.getLocation().getX(),
                p.getLocation().getY(),
                p.getLocation().getZ(),
                p.getWorld().getName());
    }

    @Override
    public PlayerDetails playerInfo(final UUID uuid) {
        return mainThread.call(() -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            final List<String> effects = new ArrayList<>();
            for (final PotionEffect effect : p.getActivePotionEffects()) {
                effects.add(
                        effect.getType().getKey().getKey() + ":" + effect.getAmplifier() + ":" + effect.getDuration());
            }
            return new PlayerDetails(playerInfoOf(p), effects, p.isOp());
        });
    }

    @Override
    public StatsInfo playerStats(final UUID uuid) {
        return mainThread.call(() -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            final Map<String, Object> stats = new LinkedHashMap<>();
            for (final Statistic stat : Statistic.values()) {
                if (stat.getType() != Statistic.Type.UNTYPED) {
                    continue;
                }
                try {
                    stats.put(stat.name().toLowerCase(Locale.ROOT), p.getStatistic(stat));
                } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
                    // some statistics are not tracked on this server
                }
            }
            return new StatsInfo(stats);
        });
    }

    @Override
    public BlockInfo blockAt(final String worldKey, final int x, final int y, final int z) {
        return mainThread.call(() -> {
            final World w = requireWorld(worldKey);
            if (y < w.getMinHeight() || y > w.getMaxHeight()) {
                throw new FacadeException(
                        "y out of world height range [" + w.getMinHeight() + ", " + w.getMaxHeight() + "]");
            }
            final Block b = w.getBlockAt(x, y, z);
            return blockInfoOf(w, b, x, y, z);
        });
    }

    @Override
    public List<BlockInfo> region(
            final String worldKey,
            final int x1,
            final int y1,
            final int z1,
            final int x2,
            final int y2,
            final int z2,
            final int cap) {
        // Phase 1 (main thread): capture chunk snapshots. getChunkAt may load chunks
        // and
        // must run on the main thread, but ChunkSnapshot reads are safe off-thread
        // (spec 4.4).
        final Map<String, org.bukkit.ChunkSnapshot> snapshots = mainThread.call(() -> {
            final World w = requireWorld(worldKey);
            final Map<String, org.bukkit.ChunkSnapshot> map = new LinkedHashMap<>();
            for (int x = x1; x <= x2 && map.size() < 128; x++) {
                for (int z = z1; z <= z2 && map.size() < 128; z++) {
                    final int cx = x >> 4;
                    final int cz = z >> 4;
                    final String key = cx + "," + cz;
                    if (!map.containsKey(key)) {
                        map.put(key, w.getChunkAt(cx, cz).getChunkSnapshot(true, true, false));
                    }
                }
            }
            return map;
        });

        // Phase 2 (off-thread): read blocks from the captured snapshots only.
        int volume = (int) (Math.min(x2 - x1 + 1L, 2000L) * Math.min(y2 - y1 + 1L, 400L)
                * Math.min(z2 - z1 + 1L, 2000L));
        if (volume > cap) {
            volume = cap;
        }
        final List<BlockInfo> result = new ArrayList<>(Math.min(volume, cap));
        for (int x = x1; x <= x2 && result.size() < cap; x++) {
            for (int y = y1; y <= y2 && result.size() < cap; y++) {
                for (int z = z1; z <= z2 && result.size() < cap; z++) {
                    final String key = (x >> 4) + "," + (z >> 4);
                    final org.bukkit.ChunkSnapshot snapshot = snapshots.get(key);
                    if (snapshot == null) {
                        continue;
                    }
                    final int localX = x & 15;
                    final int localZ = z & 15;
                    final Material material = snapshot.getBlockType(localX, y, localZ);
                    final String blockData = snapshot.getBlockData(localX, y, localZ).getAsString();
                    final int light = snapshot.getBlockEmittedLight(localX, y, localZ);
                    final int skyLight = snapshot.getBlockSkyLight(localX, y, localZ);
                    result.add(new BlockInfo(
                            worldKey,
                            x,
                            y,
                            z,
                            material.getKey().getKey(),
                            blockData,
                            snapshotBiomeName(snapshot, localX, y, localZ),
                            light,
                            skyLight,
                            true));
                }
            }
        }
        return result;
    }

    @Override
    public List<EntityInfo> entities(final String worldKey, final String type, final int limit, final int page) {
        return mainThread.call(() -> {
            final World w = requireWorld(worldKey);
            final List<EntityInfo> all = new ArrayList<>();
            for (final Entity e : w.getEntities()) {
                final String entityType = e.getType().getKey().getKey();
                if (type != null && !type.isBlank() && !entityType.equalsIgnoreCase(type)) {
                    continue;
                }
                all.add(new EntityInfo(
                        e.getUniqueId(),
                        entityType,
                        e.getLocation().getX(),
                        e.getLocation().getY(),
                        e.getLocation().getZ(),
                        worldKey,
                        e instanceof final LivingEntity le ? le.getHealth() : -1,
                        entityDisplayName(e)));
            }
            all.sort(java.util.Comparator.comparing(e -> e.uuid().toString()));
            final int from = page * limit;
            if (from >= all.size()) {
                return List.of();
            }
            return all.subList(from, Math.min(from + limit, all.size()));
        });
    }

    @Override
    public EntityDetails entityInfo(final UUID uuid) {
        return mainThread.call(() -> {
            for (final World w : Bukkit.getWorlds()) {
                for (final Entity e : w.getEntities()) {
                    if (e.getUniqueId().equals(uuid)) {
                        final List<String> passengers = new ArrayList<>();
                        for (final Entity p : e.getPassengers()) {
                            passengers.add(p.getType().getKey().getKey());
                        }
                        final String vehicle = e.getVehicle() == null
                                ? null
                                : e.getVehicle().getType().getKey().getKey();
                        final EntityInfo base = new EntityInfo(
                                e.getUniqueId(),
                                e.getType().getKey().getKey(),
                                e.getLocation().getX(),
                                e.getLocation().getY(),
                                e.getLocation().getZ(),
                                w.getName(),
                                e instanceof final LivingEntity le ? le.getHealth() : -1,
                                entityDisplayName(e));
                        return new EntityDetails(base, passengers, vehicle);
                    }
                }
            }
            throw new FacadeException("entity not found: " + uuid);
        });
    }

    @Override
    public void messagePlayer(final UUID uuid, final String text, final boolean allowFormatting) {
        mainThread.call(() -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            p.sendMessage(allowFormatting ? text : stripFormatting(text));
            return null;
        });
    }

    @Override
    public void kickPlayer(final UUID uuid, final String reason) {
        mainThread.call(() -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            p.kick(reason == null || reason.isBlank() ? null : Component.text(stripFormatting(reason)));
            return null;
        });
    }

    @Override
    public void teleport(final UUID uuid, final UUID targetUuid) {
        mainThread.call(() -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            final Player target = Bukkit.getPlayer(targetUuid);
            if (target == null) {
                throw new FacadeException("target player not online: " + targetUuid);
            }
            p.teleport(target.getLocation());
            return null;
        });
    }

    @Override
    public void teleport(final UUID uuid, final Location loc) {
        mainThread.call(() -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            p.teleport(loc);
            return null;
        });
    }

    @Override
    public void setGamemode(final UUID uuid, final String gamemode) {
        mainThread.call(() -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            p.setGameMode(GameMode.valueOf(gamemode));
            return null;
        });
    }

    @Override
    public void applyEffect(final UUID uuid, final String effect, final int durationSeconds, final int amplifier) {
        mainThread.call(() -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            final PotionEffectType type = org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(effect));
            if (type == null) {
                throw new FacadeException("unknown effect: " + effect);
            }
            p.addPotionEffect(new PotionEffect(type, durationSeconds * 20, amplifier, true, true));
            return null;
        });
    }

    @Override
    public void ban(final UUID uuid, final String reason) {
        mainThread.call(() -> {
            final OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            final ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
            bans.addBan(op.getPlayerProfile(), reason, (Instant) null, "mc2p");
            final Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.kick(reason == null || reason.isBlank() ? null : Component.text(stripFormatting(reason)));
            }
            return null;
        });
    }

    @Override
    public void unban(final UUID uuid) {
        mainThread.call(() -> {
            final OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            final ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
            bans.pardon(op.getPlayerProfile());
            return null;
        });
    }

    @Override
    public void whitelistAdd(final UUID uuid) {
        mainThread.call(() -> {
            final OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            op.setWhitelisted(true);
            return null;
        });
    }

    @Override
    public void whitelistRemove(final UUID uuid) {
        mainThread.call(() -> {
            final OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            op.setWhitelisted(false);
            return null;
        });
    }

    @Override
    public void setBlock(final String worldKey, final int x, final int y, final int z, final String material) {
        mainThread.call(() -> {
            final World w = requireWorld(worldKey);
            final Material m = Material.matchMaterial(material);
            if (m == null || !m.isBlock()) {
                throw new FacadeException("unknown or non-block material: " + material);
            }
            w.getBlockAt(x, y, z).setType(m);
            return null;
        });
    }

    @Override
    public CommandResult executeCommand(final String command) {
        return mainThread.call(() -> {
            final ConsoleCommandSender console = Bukkit.getConsoleSender();
            final RecordingCommandSender recording = new RecordingCommandSender(console);
            boolean ok;
            try {
                ok = Bukkit.dispatchCommand(recording, command);
            } catch (final Throwable t) {
                return new CommandResult(false, recording.output(), t.getMessage());
            }
            return new CommandResult(ok, recording.output(), ok ? null : "command returned failure");
        });
    }

    @Override
    public boolean restartAvailable() {
        return !"disabled".equals(restartStrategy);
    }

    @Override
    public String restartStrategyHealth() {
        return switch (restartStrategy) {
            case "auto" ->
                "auto (spigot restart if a restart-script is configured, else host-restart via graceful stop)";
            case "spigot-restart" -> "spigot restart (requires spigot.yml settings.restart-script)";
            case "host-restart" -> "graceful stop; the host panel auto-restart will reboot";
            case "disabled" -> "disabled by configuration";
            default -> "unknown strategy '" + restartStrategy + "'";
        };
    }

    @Override
    public void scheduleRestart(final String announce, final int countdownSeconds) {
        mainThread.run(() -> scheduleShutdown(announce, countdownSeconds, true));
    }

    @Override
    public void scheduleStop(final String announce, final int countdownSeconds) {
        mainThread.run(() -> scheduleShutdown(announce, countdownSeconds, false));
    }

    private void scheduleShutdown(final String announce, final int countdownSeconds, final boolean restart) {
        final String message = (restart ? "Server restarting" : "Server stopping") + " in "
                + countdownSeconds + " second" + (countdownSeconds == 1 ? "" : "s")
                + (announce == null || announce.isBlank() ? "" : " - " + announce);
        Bukkit.broadcast(Component.text(stripFormatting(message)));
        for (int i = countdownSeconds; i > 0; i--) {
            final int remaining = i;
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (remaining <= 5 || remaining % 10 == 0) {
                                    Bukkit.broadcast(Component.text(stripFormatting("Server "
                                            + (restart ? "restarting" : "stopping") + " in " + remaining + "s")));
                                }
                            },
                            (countdownSeconds - i) * 20L + 1);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> doShutdown(restart), countdownSeconds * 20L + 20L);
    }

    private void doShutdown(final boolean restart) {
        if (restart) {
            try {
                if (trySpigotRestart()) {
                    return;
                }
            } catch (final Throwable t) {
                log.warn("spigot restart unavailable ({}), falling back to graceful stop", t.getMessage());
            }
            // host-restart fallback: graceful stop; the host panel reboots on exit
        }
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.shutdown());
    }

    private boolean trySpigotRestart() throws Throwable {
        Bukkit.restart();
        return true;
    }

    @Override
    public boolean worldExists(final String worldKey) {
        return mainThread.call(() -> Bukkit.getWorld(worldKey) != null);
    }

    // ---- internals ----

    private World requireWorld(final String worldKey) {
        if (!Utils.isSafeWorldKey(worldKey)) {
            throw new FacadeException("invalid world key");
        }
        final World w = Bukkit.getWorld(worldKey);
        if (w == null) {
            throw new FacadeException("unknown world: " + worldKey);
        }
        return w;
    }

    private static double[] safeTps() {
        try {
            final double[] tps = Bukkit.getTPS();
            return tps == null ? new double[] { 20.0 } : tps;
        } catch (final Throwable t) {
            return new double[] { 20.0 };
        }
    }

    private static long safeTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (final Throwable t) {
            return 0;
        }
    }

    private static int safePing(final Player p) {
        try {
            return p.getPing();
        } catch (final Throwable t) {
            return -1;
        }
    }

    private static String dimensionName(final World.Environment env) {
        return switch (env) {
            case NORMAL -> "overworld";
            case NETHER -> "nether";
            case THE_END -> "the_end";
            default -> env.name().toLowerCase(Locale.ROOT);
        };
    }

    private static String entityDisplayName(final Entity e) {
        try {
            if (e instanceof final Player p) {
                return p.getName();
            }
            if (e instanceof final LivingEntity le) {
                final Component custom = le.customName();
                if (custom != null) {
                    final String name = PlainTextComponentSerializer.plainText().serialize(custom);
                    if (!name.isBlank()) {
                        return name;
                    }
                }
            }
        } catch (final Throwable ignored) {
            // fall through
        }
        return null;
    }

    private static String stripFormatting(final String text) {
        if (text == null) {
            return null;
        }
        final String plain = PlainTextComponentSerializer.plainText()
                .serialize(LegacyComponentSerializer.legacySection().deserialize(text));
        return plain.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    private static BlockInfo blockInfoOf(final World w, final Block b, final int x, final int y, final int z) {
        String biome = "unknown";
        try {
            biome = w.getBiome(x, y, z).getKey().getKey();
        } catch (final Throwable ignored) {
        }
        int light = -1;
        int sky = -1;
        try {
            light = b.getLightLevel();
            sky = b.getLightFromSky();
        } catch (final Throwable ignored) {
        }
        return new BlockInfo(
                w.getName(),
                x,
                y,
                z,
                b.getType().getKey().getKey(),
                b.getBlockData().getAsString(),
                biome,
                light,
                sky,
                w.isChunkLoaded(x >> 4, z >> 4));
    }

    /**
     * Off-thread-safe biome name from a snapshot only (snapshots carry biomes when
     * captured with includeBiome).
     */
    private static String snapshotBiomeName(
            final org.bukkit.ChunkSnapshot snapshot, final int x, final int y, final int z) {
        try {
            return snapshot.getBiome(x, y, z).getKey().getKey();
        } catch (final Throwable t) {
            return "unknown";
        }
    }

    /**
     * Delegates to the console sender while recording output lines for the tool
     * response.
     */
    private static final class RecordingCommandSender implements ConsoleCommandSender {

        private final ConsoleCommandSender delegate;
        private final StringBuilder output = new StringBuilder();

        RecordingCommandSender(final ConsoleCommandSender delegate) {
            this.delegate = delegate;
        }

        String output() {
            return output.toString();
        }

        private void record(final String message) {
            if (output.length() > 0) {
                output.append('\n');
            }
            if (output.length() > 4000) {
                return;
            }
            output.append(message);
        }

        @Override
        public void sendMessage(final String message) {
            delegate.sendMessage(message);
            record(stripFormatting(message));
        }

        @Override
        public void sendMessage(final String... messages) {
            delegate.sendMessage(messages);
            for (final String message : messages) {
                record(stripFormatting(message));
            }
        }

        @Override
        public void sendMessage(final java.util.UUID uuid, final String message) {
            delegate.sendMessage(message);
            record(stripFormatting(message));
        }

        @Override
        public void sendMessage(final java.util.UUID uuid, final String... messages) {
            delegate.sendMessage(messages);
            for (final String message : messages) {
                record(stripFormatting(message));
            }
        }

        @Override
        public void sendRawMessage(final String message) {
            delegate.sendRawMessage(message);
        }

        @Override
        public void sendRawMessage(final java.util.UUID uuid, final String message) {
            delegate.sendRawMessage(message);
        }

        @Override
        public org.bukkit.Server getServer() {
            return delegate.getServer();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public net.kyori.adventure.text.Component name() {
            return delegate.name();
        }

        @Override
        public org.bukkit.command.CommandSender.Spigot spigot() {
            return delegate.spigot();
        }

        @Override
        public boolean isOp() {
            return delegate.isOp();
        }

        @Override
        public void setOp(final boolean value) {
            delegate.setOp(value);
        }

        @Override
        public boolean isPermissionSet(final String name) {
            return delegate.isPermissionSet(name);
        }

        @Override
        public boolean isPermissionSet(final org.bukkit.permissions.Permission perm) {
            return delegate.isPermissionSet(perm);
        }

        @Override
        public boolean hasPermission(final String name) {
            return delegate.hasPermission(name);
        }

        @Override
        public boolean hasPermission(final org.bukkit.permissions.Permission perm) {
            return delegate.hasPermission(perm);
        }

        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(
                final Plugin plugin, final String name, final boolean value) {
            return delegate.addAttachment(plugin, name, value);
        }

        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(final Plugin plugin) {
            return delegate.addAttachment(plugin);
        }

        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(
                final Plugin plugin, final String name, final boolean value, final int ticks) {
            return delegate.addAttachment(plugin, name, value, ticks);
        }

        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(final Plugin plugin, final int ticks) {
            return delegate.addAttachment(plugin, ticks);
        }

        @Override
        public void removeAttachment(final org.bukkit.permissions.PermissionAttachment attachment) {
            delegate.removeAttachment(attachment);
        }

        @Override
        public void recalculatePermissions() {
            delegate.recalculatePermissions();
        }

        @Override
        public java.util.Set<org.bukkit.permissions.PermissionAttachmentInfo> getEffectivePermissions() {
            return delegate.getEffectivePermissions();
        }

        @Override
        public boolean isConversing() {
            return delegate.isConversing();
        }

        @Override
        public void acceptConversationInput(final String input) {
            delegate.acceptConversationInput(input);
        }

        @Override
        public boolean beginConversation(final org.bukkit.conversations.Conversation conversation) {
            return delegate.beginConversation(conversation);
        }

        @Override
        public void abandonConversation(final org.bukkit.conversations.Conversation conversation) {
            delegate.abandonConversation(conversation);
        }

        @Override
        public void abandonConversation(
                final org.bukkit.conversations.Conversation conversation,
                final org.bukkit.conversations.ConversationAbandonedEvent details) {
            delegate.abandonConversation(conversation, details);
        }
    }
}
