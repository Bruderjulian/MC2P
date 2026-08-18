package dev.mc2p.plugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Watches player join/leave so the MCP resource-list-changed notification can
 * be sent
 * (keeps agent resource listings fresh without polling).
 */
public final class PlayerTracker implements Listener {

    private final Mc2pPlugin plugin;

    public PlayerTracker(final Mc2pPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        plugin.notifyPlayersChanged();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        plugin.notifyPlayersChanged();
    }
}
