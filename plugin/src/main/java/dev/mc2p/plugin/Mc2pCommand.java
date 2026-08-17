package dev.mc2p.plugin;

import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.tokens.TokenManager.TokenInfo;
import dev.mc2p.plugin.config.BackendConfig;

/**
 * {@code /mc2p} admin console: status, reload, token rotate, token revoke.
 */
public final class Mc2pCommand implements CommandExecutor {

    private static final String PREFIX = ChatColor.AQUA + "[MC2P] " + ChatColor.RESET;

    private final Mc2pPlugin plugin;

    public Mc2pCommand(Mc2pPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mc2p.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "token" -> token(sender, args);
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void status(CommandSender sender) {
        BackendConfig config = plugin.config();
        sender.sendMessage(PREFIX + "MC2P status");
        sender.sendMessage(ChatColor.GRAY + "  mode: " + ChatColor.WHITE + plugin.effectiveMode());
        sender.sendMessage(ChatColor.GRAY + "  serverId: " + ChatColor.WHITE + plugin.serverId());
        sender.sendMessage(ChatColor.GRAY + "  mcp endpoint: " + ChatColor.WHITE + config.mcp().bind() + ":"
                + config.mcp().port() + config.mcp().endpoint() + " (tls=" + config.mcp().tls().mode() + ")");
        sender.sendMessage(ChatColor.GRAY + "  restart strategy: " + ChatColor.WHITE + config.restartStrategy());
        sender.sendMessage(ChatColor.GRAY + "  tools registered: " + ChatColor.WHITE + plugin.registry().size());
        for (Map.Entry<Role, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            TokenInfo info = e.getValue();
            sender.sendMessage(ChatColor.GRAY + "  token " + e.getKey().name().toLowerCase() + ": "
                    + ChatColor.WHITE + (info.configured() ? "(config) " : "(rotated) ") + info.tokenId());
        }
        sender.sendMessage(ChatColor.GRAY + "  audit log: " + ChatColor.WHITE + config.audit().file());
    }

    private void reload(CommandSender sender) {
        try {
            plugin.applyConfig();
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration reloaded.");
        } catch (RuntimeException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Reload failed: " + e.getMessage());
        }
    }

    private void token(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /mc2p token <rotate|revoke> <reader|ops|admin>");
            return;
        }
        Role role = Role.fromString(args[2]);
        if (role == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown role: " + args[2]);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "rotate" -> {
                String token = plugin.tokens().rotate(role);
                plugin.audit().log(null, "console", plugin.serverId(), "token", "rotate",
                        "{\"role\":\"" + role.name().toLowerCase() + "\"}");
                sender.sendMessage(PREFIX + ChatColor.GREEN + "New " + role.name().toLowerCase() + " token (shown once):");
                sender.sendMessage(ChatColor.YELLOW + token);
            }
            case "revoke" -> {
                boolean revoked = plugin.tokens().revoke(role);
                plugin.audit().log(null, "console", plugin.serverId(), "token", "revoke",
                        "{\"role\":\"" + role.name().toLowerCase() + "\"}");
                if (revoked) {
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Rotated " + role.name().toLowerCase()
                            + " token revoked (config token, if any, is active again).");
                } else {
                    sender.sendMessage(PREFIX + ChatColor.YELLOW + "No rotated " + role.name().toLowerCase()
                            + " token to revoke.");
                }
            }
            default -> sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /mc2p token <rotate|revoke> <reader|ops|admin>");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "  /mc2p status");
        sender.sendMessage(ChatColor.GRAY + "  /mc2p reload");
        sender.sendMessage(ChatColor.GRAY + "  /mc2p token rotate <reader|ops|admin>");
        sender.sendMessage(ChatColor.GRAY + "  /mc2p token revoke <reader|ops|admin>");
    }
}