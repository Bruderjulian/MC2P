package dev.mc2p.plugin.facade;

import dev.mc2p.plugin.facade.model.Model.BlockInfo;
import dev.mc2p.plugin.facade.model.Model.CommandResult;
import dev.mc2p.plugin.facade.model.Model.EntityDetails;
import dev.mc2p.plugin.facade.model.Model.EntityInfo;
import dev.mc2p.plugin.facade.model.Model.PlayerDetails;
import dev.mc2p.plugin.facade.model.Model.PlayerInfo;
import dev.mc2p.plugin.facade.model.Model.PluginInfo;
import dev.mc2p.plugin.facade.model.Model.StatsInfo;
import dev.mc2p.plugin.facade.model.Model.Status;
import dev.mc2p.plugin.facade.model.Model.WorldInfo;
import java.util.List;
import java.util.UUID;

/**
 * Abstraction over the host server, implemented for Paper. All Bukkit API access happens
 * inside the implementation, which schedules onto the main thread. Tool handlers and the
 * HTTP/RPC layers only ever see this interface.
 */
public interface ServerFacade {

    String serverId();

    String pluginVersion();

    String minecraftVersion();

    boolean onlineMode();

    boolean isStopping();

    Status status();

    List<WorldInfo> worlds();

    List<PluginInfo> plugins();

    List<PlayerInfo> players();

    PlayerDetails playerInfo(UUID uuid);

    StatsInfo playerStats(UUID uuid);

    BlockInfo blockAt(String worldKey, int x, int y, int z);

    /** Bounded block dump; {@code cap} bounds the number of blocks returned. */
    List<BlockInfo> region(String worldKey, int x1, int y1, int z1, int x2, int y2, int z2, int cap);

    List<EntityInfo> entities(String worldKey, String type, int limit, int page);

    EntityDetails entityInfo(UUID uuid);

    void messagePlayer(UUID uuid, String text, boolean allowFormatting);

    void kickPlayer(UUID uuid, String reason);

    void teleport(UUID uuid, int[] coords, String worldKey, UUID targetUuid);

    void setGamemode(UUID uuid, String gamemode);

    void applyEffect(UUID uuid, String effect, int durationSeconds, int amplifier);

    void ban(UUID uuid, String reason);

    void unban(UUID uuid);

    void whitelistAdd(UUID uuid);

    void whitelistRemove(UUID uuid);

    void setBlock(String worldKey, int x, int y, int z, String material);

    CommandResult executeCommand(String command);

    boolean restartAvailable();

    String restartStrategyHealth();

    void scheduleRestart(String announce, int countdownSeconds);

    void scheduleStop(String announce, int countdownSeconds);

    /** True when the given world key exists. */
    boolean worldExists(String worldKey);
}
