package dev.mc2p.plugin.facade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
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

import dev.mc2p.common.validate.Validators;
import dev.mc2p.plugin.facade.model.Model;
import dev.mc2p.plugin.facade.model.Model.BlockInfo;
import dev.mc2p.plugin.facade.model.Model.CommandResult;
import dev.mc2p.plugin.facade.model.Model.EntityDetails;
import dev.mc2p.plugin.facade.model.Model.EntityInfo;
import dev.mc2p.plugin.facade.model.Model.PlayerDetails;
import dev.mc2p.plugin.facade.model.Model.PlayerInfo;
import dev.mc2p.plugin.facade.model.Model.PluginInfo;
import dev.mc2p.plugin.facade.model.Model.Status;
import dev.mc2p.plugin.facade.model.Model.StatsInfo;
import dev.mc2p.plugin.facade.model.Model.Tps;
import dev.mc2p.plugin.facade.model.Model.WorldInfo;
import dev.mc2p.plugin.thread.MainThread;

/** Bukkit/Paper implementation of {@link ServerFacade}. Every entry point runs on the main thread. */
public final class PaperServerFacade implements ServerFacade {

    private static final Logger log = LoggerFactory.getLogger(PaperServerFacade.class);

    private final Plugin plugin;
    private final MainThread mainThread;
    private final String serverId;
    private final String restartStrategy;
    private final long startedAt = System.currentTimeMillis();

    public PaperServerFacade(Plugin plugin, MainThread mainThread, String serverId, String restartStrategy) {
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
            double[] tpsArray = safeTps();
            Tps tps = new Tps(tpsArray.length > 0 ? tpsArray[0] : 20.0, tpsArray.length > 1 ? tpsArray[1] : 20.0,
                    tpsArray.length > 2 ? tpsArray[2] : 20.0);
            List<String> worlds = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                worlds.add(w.getName());
            }
            List<String> plugins = new ArrayList<>();
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                plugins.add(p.getName());
            }
            Runtime rt = Runtime.getRuntime();
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
            List<WorldInfo> result = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                result.add(new WorldInfo(w.getName(), dimensionName(w.getEnvironment()),
                        new int[] { w.getSpawnLocation().getBlockX(), w.getSpawnLocation().getBlockY(),
                                w.getSpawnLocation().getBlockZ() },
                        w.getLoadedChunks().length));
            }
            return result;
        });
    }

    @Override
    public List<PluginInfo> plugins() {
        return mainThread.call(() -> {
            List<PluginInfo> result = new ArrayList<>();
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                result.add(new PluginInfo(p.getName(), p.getPluginMeta().getVersion(), p.isEnabled()));
            }
            return result;
        });
    }

    @Override
    public List<PlayerInfo> players() {
        return mainThread.call(() -> {
            List<PlayerInfo> result = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                result.add(playerInfoOf(p));
            }
            result.sort(java.util.Comparator.comparing(PlayerInfo::name));
            return result;
        });
    }

    private static PlayerInfo playerInfoOf(Player p) {
        return new PlayerInfo(p.getUniqueId(), p.getName(), safePing(p),
                p.getGameMode().name().toLowerCase(Locale.ROOT), p.getHealth(), p.getFoodLevel(), p.getLevel(),
                p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
                p.getWorld().getName());
    }

    @Override
    public PlayerDetails playerInfo(UUID uuid) {
        return mainThread.call(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            List<String> effects = new ArrayList<>();
            for (PotionEffect effect : p.getActivePotionEffects()) {
                effects.add(effect.getType().getKey().getKey() + ":" + effect.getAmplifier() + ":" + effect.getDuration());
            }
            return new PlayerDetails(playerInfoOf(p), effects, p.isOp());
        });
    }

    @Override
    public StatsInfo playerStats(UUID uuid) {
        return mainThread.call(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            Map<String, Object> stats = new LinkedHashMap<>();
            for (Statistic stat : Statistic.values()) {
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
    public BlockInfo blockAt(String worldKey, int x, int y, int z) {
        return mainThread.call(() -> {
            World w = requireWorld(worldKey);
            if (y < w.getMinHeight() || y > w.getMaxHeight()) {
                throw new FacadeException("y out of world height range [" + w.getMinHeight() + ", " + w.getMaxHeight() + "]");
            }
            Block b = w.getBlockAt(x, y, z);
            return blockInfoOf(w, b, x, y, z);
        });
    }

    @Override
    public List<BlockInfo> region(String worldKey, int x1, int y1, int z1, int x2, int y2, int z2, int cap) {
        // Phase 1 (main thread): capture chunk snapshots. getChunkAt may load chunks and
        // must run on the main thread, but ChunkSnapshot reads are safe off-thread (spec 4.4).
        Map<String, org.bukkit.ChunkSnapshot> snapshots = mainThread.call(() -> {
            World w = requireWorld(worldKey);
            Map<String, org.bukkit.ChunkSnapshot> map = new LinkedHashMap<>();
            for (int x = x1; x <= x2 && map.size() < 128; x++) {
                for (int z = z1; z <= z2 && map.size() < 128; z++) {
                    int cx = x >> 4;
                    int cz = z >> 4;
                    String key = cx + "," + cz;
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
        List<BlockInfo> result = new ArrayList<>(Math.min(volume, cap));
        for (int x = x1; x <= x2 && result.size() < cap; x++) {
            for (int y = y1; y <= y2 && result.size() < cap; y++) {
                for (int z = z1; z <= z2 && result.size() < cap; z++) {
                    String key = (x >> 4) + "," + (z >> 4);
                    org.bukkit.ChunkSnapshot snapshot = snapshots.get(key);
                    if (snapshot == null) {
                        continue;
                    }
                    int localX = x & 15;
                    int localZ = z & 15;
                    Material material = snapshot.getBlockType(localX, y, localZ);
                    String blockData = snapshot.getBlockData(localX, y, localZ).getAsString();
                    int light = snapshot.getBlockEmittedLight(localX, y, localZ);
                    int skyLight = snapshot.getBlockSkyLight(localX, y, localZ);
                    result.add(new BlockInfo(worldKey, x, y, z,
                            material.getKey().getKey(), blockData,
                            snapshotBiomeName(snapshot, localX, y, localZ),
                            light, skyLight, true));
                }
            }
        }
        return result;
    }

    @Override
    public List<EntityInfo> entities(String worldKey, String type, int limit, int page) {
        return mainThread.call(() -> {
            World w = requireWorld(worldKey);
            List<EntityInfo> all = new ArrayList<>();
            for (Entity e : w.getEntities()) {
                String entityType = e.getType().getKey().getKey();
                if (type != null && !type.isBlank() && !entityType.equalsIgnoreCase(type)) {
                    continue;
                }
                all.add(new EntityInfo(e.getUniqueId(), entityType,
                        e.getLocation().getX(), e.getLocation().getY(), e.getLocation().getZ(), worldKey,
                        e instanceof LivingEntity le ? le.getHealth() : -1,
                        entityDisplayName(e)));
            }
            all.sort(java.util.Comparator.comparing(e -> e.uuid().toString()));
            int from = page * limit;
            if (from >= all.size()) {
                return List.of();
            }
            return all.subList(from, Math.min(from + limit, all.size()));
        });
    }

    @Override
    public EntityDetails entityInfo(UUID uuid) {
        return mainThread.call(() -> {
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e.getUniqueId().equals(uuid)) {
                        List<String> passengers = new ArrayList<>();
                        for (Entity p : e.getPassengers()) {
                            passengers.add(p.getType().getKey().getKey());
                        }
                        String vehicle = e.getVehicle() == null ? null : e.getVehicle().getType().getKey().getKey();
                        EntityInfo base = new EntityInfo(e.getUniqueId(), e.getType().getKey().getKey(),
                                e.getLocation().getX(), e.getLocation().getY(), e.getLocation().getZ(), w.getName(),
                                e instanceof LivingEntity le ? le.getHealth() : -1, entityDisplayName(e));
                        return new EntityDetails(base, passengers, vehicle);
                    }
                }
            }
            throw new FacadeException("entity not found: " + uuid);
        });
    }

    @Override
    public void messagePlayer(UUID uuid, String text, boolean allowFormatting) {
        mainThread.call(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            p.sendMessage(allowFormatting ? text : stripFormatting(text));
            return null;
        });
    }

    @Override
    public void kickPlayer(UUID uuid, String reason) {
        mainThread.call(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            p.kickPlayer(reason == null || reason.isBlank() ? null : stripFormatting(reason));
            return null;
        });
    }

    @Override
    public void teleport(UUID uuid, int[] coords, String worldKey, UUID targetUuid) {
        mainThread.call(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            if (targetUuid != null) {
                Player target = Bukkit.getPlayer(targetUuid);
                if (target == null) {
                    throw new FacadeException("target player not online: " + targetUuid);
                }
                p.teleport(target.getLocation());
                return null;
            }
            World w = requireWorld(worldKey);
            p.teleport(new org.bukkit.Location(w, coords[0] + 0.5, coords[1], coords[2] + 0.5));
            return null;
        });
    }

    @Override
    public void setGamemode(UUID uuid, String gamemode) {
        mainThread.call(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            p.setGameMode(GameMode.valueOf(gamemode));
            return null;
        });
    }

    @Override
    public void applyEffect(UUID uuid, String effect, int durationSeconds, int amplifier) {
        mainThread.call(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                throw new FacadeException("player not online: " + uuid);
            }
            PotionEffectType type = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effect));
            if (type == null) {
                throw new FacadeException("unknown effect: " + effect);
            }
            p.addPotionEffect(new PotionEffect(type, durationSeconds * 20, amplifier, true, true));
            return null;
        });
    }

    @Override
    public void ban(UUID uuid, String reason) {
        mainThread.call(() -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName();
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                    .addBan(name == null ? uuid.toString() : name, reason, null, "mc2p");
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.kickPlayer(reason == null || reason.isBlank() ? "Banned" : stripFormatting(reason));
            }
            return null;
        });
    }

    @Override
    public void unban(UUID uuid) {
        mainThread.call(() -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName();
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                    .pardon(name == null ? uuid.toString() : name);
            return null;
        });
    }

    @Override
    public void whitelistAdd(UUID uuid) {
        mainThread.call(() -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            op.setWhitelisted(true);
            return null;
        });
    }

    @Override
    public void whitelistRemove(UUID uuid) {
        mainThread.call(() -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            op.setWhitelisted(false);
            return null;
        });
    }

    @Override
    public void setBlock(String worldKey, int x, int y, int z, String material) {
        mainThread.call(() -> {
            World w = requireWorld(worldKey);
            Material m = Material.matchMaterial(material);
            if (m == null || !m.isBlock()) {
                throw new FacadeException("unknown or non-block material: " + material);
            }
            w.getBlockAt(x, y, z).setType(m);
            return null;
        });
    }

    @Override
    public CommandResult executeCommand(String command) {
        return mainThread.call(() -> {
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            RecordingCommandSender recording = new RecordingCommandSender(console);
            boolean ok;
            try {
                ok = Bukkit.dispatchCommand(recording, command);
            } catch (Throwable t) {
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
            case "auto" -> "auto (spigot restart if a restart-script is configured, else host-restart via graceful stop)";
            case "spigot-restart" -> "spigot restart (requires spigot.yml settings.restart-script)";
            case "host-restart" -> "graceful stop; the host panel auto-restart will reboot";
            case "disabled" -> "disabled by configuration";
            default -> "unknown strategy '" + restartStrategy + "'";
        };
    }

    @Override
    public void scheduleRestart(String announce, int countdownSeconds) {
        mainThread.run(() -> scheduleShutdown(announce, countdownSeconds, true));
    }

    @Override
    public void scheduleStop(String announce, int countdownSeconds) {
        mainThread.run(() -> scheduleShutdown(announce, countdownSeconds, false));
    }

    private void scheduleShutdown(String announce, int countdownSeconds, boolean restart) {
        String message = (restart ? "Server restarting" : "Server stopping") + " in "
                + countdownSeconds + " second" + (countdownSeconds == 1 ? "" : "s")
                + (announce == null || announce.isBlank() ? "" : " - " + announce);
        Bukkit.broadcastMessage(stripFormatting(message));
        for (int i = countdownSeconds; i > 0; i--) {
            int remaining = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (remaining <= 5 || remaining % 10 == 0) {
                    Bukkit.broadcastMessage(stripFormatting("Server " + (restart ? "restarting" : "stopping")
                            + " in " + remaining + "s"));
                }
            }, (countdownSeconds - i) * 20L + 1);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> doShutdown(restart), countdownSeconds * 20L + 20L);
    }

    private void doShutdown(boolean restart) {
        if (restart) {
            try {
                if (trySpigotRestart()) {
                    return;
                }
            } catch (Throwable t) {
                log.warn("spigot restart unavailable ({}), falling back to graceful stop", t.getMessage());
            }
            // host-restart fallback: graceful stop; the host panel reboots on exit
        }
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.shutdown());
    }

    private boolean trySpigotRestart() throws Throwable {
        Bukkit.spigot().restart();
        return true;
    }

    @Override
    public boolean worldExists(String worldKey) {
        return mainThread.call(() -> Bukkit.getWorld(worldKey) != null);
    }

    // ---- internals ----

    private World requireWorld(String worldKey) {
        if (!Validators.isSafeWorldKey(worldKey)) {
            throw new FacadeException("invalid world key");
        }
        World w = Bukkit.getWorld(worldKey);
        if (w == null) {
            throw new FacadeException("unknown world: " + worldKey);
        }
        return w;
    }

    private static double[] safeTps() {
        try {
            double[] tps = Bukkit.getTPS();
            return tps == null ? new double[] { 20.0 } : tps;
        } catch (Throwable t) {
            return new double[] { 20.0 };
        }
    }

    private static long safeTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int safePing(Player p) {
        try {
            return p.getPing();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String dimensionName(World.Environment env) {
        return switch (env) {
            case NORMAL -> "overworld";
            case NETHER -> "nether";
            case THE_END -> "the_end";
            default -> env.name().toLowerCase(Locale.ROOT);
        };
    }

    private static String entityDisplayName(Entity e) {
        try {
            if (e instanceof Player p) {
                return p.getName();
            }
            if (e instanceof LivingEntity le) {
                String custom = le.getCustomName();
                if (custom != null && !custom.isBlank()) {
                    return org.bukkit.ChatColor.stripColor(custom);
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return null;
    }

    private static String stripFormatting(String text) {
        if (text == null) {
            return null;
        }
        String noSection = org.bukkit.ChatColor.stripColor(text);
        return noSection.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    private static BlockInfo blockInfoOf(World w, Block b, int x, int y, int z) {
        String biome = "unknown";
        try {
            biome = w.getBiome(x, y, z).getKey().getKey();
        } catch (Throwable ignored) {
        }
        int light = -1;
        int sky = -1;
        try {
            light = b.getLightLevel();
            sky = b.getLightFromSky();
        } catch (Throwable ignored) {
        }
        return new BlockInfo(w.getName(), x, y, z, b.getType().getKey().getKey(), b.getBlockData().getAsString(),
                biome, light, sky, w.isChunkLoaded(x >> 4, z >> 4));
    }

    private static String safeBiomeName(org.bukkit.ChunkSnapshot snapshot, int x, int y, int z, World w) {
        try {
            return snapshot.getBiome(x, y, z).getKey().getKey();
        } catch (Throwable t) {
            try {
                return w.getBiome(snapshot.getX() * 16 + x, y, snapshot.getZ() * 16 + z).getKey().getKey();
            } catch (Throwable t2) {
                return "unknown";
            }
        }
    }

    /** Off-thread-safe biome name from a snapshot only (snapshots carry biomes when captured with includeBiome). */
    private static String snapshotBiomeName(org.bukkit.ChunkSnapshot snapshot, int x, int y, int z) {
        try {
            return snapshot.getBiome(x, y, z).getKey().getKey();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** Delegates to the console sender while recording output lines for the tool response. */
    private static final class RecordingCommandSender implements ConsoleCommandSender {

        private final ConsoleCommandSender delegate;
        private final StringBuilder output = new StringBuilder();

        RecordingCommandSender(ConsoleCommandSender delegate) {
            this.delegate = delegate;
        }

        String output() {
            return output.toString();
        }

        private void record(String message) {
            if (output.length() > 0) {
                output.append('\n');
            }
            if (output.length() > 4000) {
                return;
            }
            output.append(message);
        }

        @Override
        public void sendMessage(String message) {
            delegate.sendMessage(message);
            record(org.bukkit.ChatColor.stripColor(message));
        }

        @Override
        public void sendMessage(String... messages) {
            delegate.sendMessage(messages);
            for (String message : messages) {
                record(org.bukkit.ChatColor.stripColor(message));
            }
        }

        @Override
        public void sendMessage(java.util.UUID uuid, String message) {
            delegate.sendMessage(uuid, message);
            record(org.bukkit.ChatColor.stripColor(message));
        }

        @Override
        public void sendMessage(java.util.UUID uuid, String... messages) {
            delegate.sendMessage(uuid, messages);
            for (String message : messages) {
                record(org.bukkit.ChatColor.stripColor(message));
            }
        }

        @Override
        public void sendRawMessage(String message) {
            delegate.sendRawMessage(message);
        }

        @Override
        public void sendRawMessage(java.util.UUID uuid, String message) {
            delegate.sendRawMessage(uuid, message);
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
        public void setOp(boolean value) {
            delegate.setOp(value);
        }

        @Override
        public boolean isPermissionSet(String name) {
            return delegate.isPermissionSet(name);
        }

        @Override
        public boolean isPermissionSet(org.bukkit.permissions.Permission perm) {
            return delegate.isPermissionSet(perm);
        }

        @Override
        public boolean hasPermission(String name) {
            return delegate.hasPermission(name);
        }

        @Override
        public boolean hasPermission(org.bukkit.permissions.Permission perm) {
            return delegate.hasPermission(perm);
        }

        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return delegate.addAttachment(plugin, name, value);
        }

        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(Plugin plugin) {
            return delegate.addAttachment(plugin);
        }

        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(Plugin plugin, String name, boolean value,
                int ticks) {
            return delegate.addAttachment(plugin, name, value, ticks);
        }

        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return delegate.addAttachment(plugin, ticks);
        }

        @Override
        public void removeAttachment(org.bukkit.permissions.PermissionAttachment attachment) {
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
        public void acceptConversationInput(String input) {
            delegate.acceptConversationInput(input);
        }

        @Override
        public boolean beginConversation(org.bukkit.conversations.Conversation conversation) {
            return delegate.beginConversation(conversation);
        }

        @Override
        public void abandonConversation(org.bukkit.conversations.Conversation conversation) {
            delegate.abandonConversation(conversation);
        }

        @Override
        public void abandonConversation(org.bukkit.conversations.Conversation conversation,
                org.bukkit.conversations.ConversationAbandonedEvent details) {
            delegate.abandonConversation(conversation, details);
        }
    }
}