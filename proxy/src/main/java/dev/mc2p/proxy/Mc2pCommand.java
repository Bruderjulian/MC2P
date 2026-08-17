package dev.mc2p.proxy;

import java.util.Map;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.tokens.TokenManager.TokenInfo;

/** {@code /mc2p} proxy console: status, reload, servers, token rotate/revoke. */
public final class Mc2pCommand implements SimpleCommand {

    private final McpProxyPlugin plugin;

    public Mc2pCommand(McpProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("mc2p.admin")) {
            source.sendMessage(Component.text("[MC2P] No permission.", NamedTextColor.RED));
            return;
        }
        String[] args = invocation.arguments();
        if (args.length == 0) {
            help(source);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> status(source);
            case "reload" -> reload(source);
            case "servers" -> servers(source);
            case "token" -> token(source, args);
            default -> help(source);
        }
    }

    private void status(CommandSource source) {
        var config = plugin.config();
        source.sendMessage(Component.text("[MC2P] status", NamedTextColor.AQUA));
        source.sendMessage(Component.text("  serverId: " + plugin.serverId()));
        source.sendMessage(Component.text("  mcp endpoint: " + config.mcp().bind() + ":" + config.mcp().port()
                + config.mcp().endpoint() + " (tls=" + config.mcp().tls().mode() + ")"));
        source.sendMessage(Component.text("  backends: " + plugin.backendServerIds().size()));
        source.sendMessage(Component.text("  tools registered: " + plugin.toolCount()));
        for (Map.Entry<Role, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            TokenInfo info = e.getValue();
            source.sendMessage(Component.text("  token " + e.getKey().name().toLowerCase() + ": "
                    + (info.configured() ? "(config) " : "(rotated) ") + info.tokenId()));
        }
        source.sendMessage(Component.text("  audit log: " + config.audit().file()));
    }

    private void reload(CommandSource source) {
        try {
            plugin.reload();
            source.sendMessage(Component.text("[MC2P] Configuration reloaded.", NamedTextColor.GREEN));
        } catch (RuntimeException e) {
            source.sendMessage(Component.text("[MC2P] Reload failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void servers(CommandSource source) {
        var ids = plugin.backendServerIds();
        if (ids.isEmpty()) {
            source.sendMessage(Component.text("[MC2P] No backends connected."));
            return;
        }
        source.sendMessage(Component.text("[MC2P] Connected backends:"));
        for (String id : ids) {
            source.sendMessage(Component.text("  " + id));
        }
    }

    private void token(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("[MC2P] Usage: /mc2p token <rotate|revoke> <reader|ops|admin>",
                    NamedTextColor.RED));
            return;
        }
        Role role = Role.fromString(args[2]);
        if (role == null) {
            source.sendMessage(Component.text("[MC2P] Unknown role: " + args[2], NamedTextColor.RED));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "rotate" -> {
                String token = plugin.tokens().rotate(role);
                plugin.audit().log(null, "console", plugin.serverId(), "token", "rotate",
                        "{\"role\":\"" + role.name().toLowerCase() + "\"}");
                source.sendMessage(Component.text("[MC2P] New " + role.name().toLowerCase()
                        + " token (shown once):", NamedTextColor.GREEN));
                source.sendMessage(Component.text(token, NamedTextColor.YELLOW));
            }
            case "revoke" -> {
                boolean revoked = plugin.tokens().revoke(role);
                plugin.audit().log(null, "console", plugin.serverId(), "token", "revoke",
                        "{\"role\":\"" + role.name().toLowerCase() + "\"}");
                if (revoked) {
                    source.sendMessage(Component.text("[MC2P] Rotated " + role.name().toLowerCase()
                            + " token revoked (config token, if any, is active again).", NamedTextColor.GREEN));
                } else {
                    source.sendMessage(Component.text("[MC2P] No rotated " + role.name().toLowerCase()
                            + " token to revoke.", NamedTextColor.YELLOW));
                }
            }
            default -> source.sendMessage(Component.text(
                    "[MC2P] Usage: /mc2p token <rotate|revoke> <reader|ops|admin>", NamedTextColor.RED));
        }
    }

    private void help(CommandSource source) {
        source.sendMessage(Component.text("[MC2P] Usage:"));
        source.sendMessage(Component.text("  /mc2p status"));
        source.sendMessage(Component.text("  /mc2p reload"));
        source.sendMessage(Component.text("  /mc2p servers"));
        source.sendMessage(Component.text("  /mc2p token rotate|revoke <reader|ops|admin>"));
    }
}