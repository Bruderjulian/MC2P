package dev.mc2p.plugin;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.mc2p.common.role.Role;
import dev.mc2p.common.setup.SetupSupport;
import dev.mc2p.common.tokens.TokenManager.TokenInfo;
import dev.mc2p.plugin.config.BackendConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /mc2p} admin console: setup, status, reload, token rotate, token revoke.
 */
public final class Mc2pCommand {

    private static final String PREFIX = ChatColor.AQUA + "[MC2P] " + ChatColor.RESET;

    private final Mc2pPlugin plugin;

    public Mc2pCommand(Mc2pPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("mc2p")
                .withPermission("mc2p.admin")
                .withSubcommand(new CommandAPICommand("setup")
                        .executes((CommandSender sender, CommandArguments args) -> setup(sender)))
                .withSubcommand(new CommandAPICommand("status")
                        .executes((CommandSender sender, CommandArguments args) -> status(sender)))
                .withSubcommand(new CommandAPICommand("reload")
                        .executes((CommandSender sender, CommandArguments args) -> reload(sender)))
                .withSubcommand(new CommandAPICommand("token")
                        .withSubcommand(new CommandAPICommand("rotate")
                                .withArguments(new MultiLiteralArgument("role", roleLiterals()))
                                .executes((CommandSender sender, CommandArguments args) ->
                                        rotate(sender, (String) args.get("role"))))
                        .withSubcommand(new CommandAPICommand("revoke")
                                .withArguments(new MultiLiteralArgument("role", roleLiterals()))
                                .executes((CommandSender sender, CommandArguments args) ->
                                        revoke(sender, (String) args.get("role")))))
                .withSubcommand(new CommandAPICommand("help")
                        .executes((CommandSender sender, CommandArguments args) -> sendHelp(sender)))
                .executes((CommandSender sender, CommandArguments args) -> sendHelp(sender))
                .register();
    }

    private static String[] roleLiterals() {
        Role[] roles = Role.values();
        String[] literals = new String[roles.length];
        for (int i = 0; i < roles.length; i++) {
            literals[i] = roles[i].name().toLowerCase();
        }
        return literals;
    }

    private void setup(CommandSender sender) {
        BackendConfig config = plugin.config();
        if ("backend".equals(plugin.effectiveMode())) {
            String secret = plugin.resolveProxySecret();
            if (secret == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Backend mode: no proxy secret is set. "
                        + "Set " + config.proxy().secretEnv() + " or place plugins/MC2P/proxy-secret, "
                        + "then run /mc2p reload.");
                return;
            }
            sender.sendMessage(PREFIX + "MC2P backend is active.");
            sender.sendMessage(ChatColor.GRAY + "  serverId: " + ChatColor.WHITE + plugin.serverId());
            sender.sendMessage(ChatColor.GRAY + "  rpc channel: " + ChatColor.WHITE
                    + config.proxy().rpcChannel());
            sender.sendMessage(ChatColor.GRAY + "  proxy secret: " + ChatColor.GREEN + "set");
            sender.sendMessage(PREFIX + "Backends hold no API tokens - the proxy owns them. "
                    + "Agent config lives on the proxy (run /mc2p setup there).");
            return;
        }

        sender.sendMessage(PREFIX + "MC2P setup (standalone)");
        for (Map.Entry<Role, String> e : plugin.ensureTokens().entrySet()) {
            sender.sendMessage(
                    PREFIX + ChatColor.GREEN + "Generated " + e.getKey().name().toLowerCase() + " token (shown once):");
            sender.sendMessage(ChatColor.YELLOW + "  " + e.getValue());
        }
        for (Map.Entry<Role, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  " + e.getKey().name().toLowerCase() + " token id: " + ChatColor.WHITE
                    + e.getValue().tokenId());
        }
        sender.sendMessage(ChatColor.GRAY + "  endpoint: " + ChatColor.WHITE
                + config.mcp().bind() + ":" + config.mcp().port() + config.mcp().endpoint()
                + " (tls=" + config.mcp().tls().mode() + ")");

        String template = SetupSupport.clientConfigTemplate(config.mcp().port());
        try {
            Files.writeString(plugin.dataDirectory().resolve("mcpServers.json"), template);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Client template written to plugins/MC2P/mcpServers.json");
        } catch (IOException ex) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Could not write mcpServers.json: " + ex.getMessage());
        }
        sender.sendMessage(PREFIX + "Agent mcpServers.json - replace <HOST> with your public host "
                + "and <TOKEN> with the token of the role you grant the agent:");
        sender.sendMessage(ChatColor.WHITE + template);
        sender.sendMessage(ChatColor.GRAY + "Trust: with tls.mode=selfsigned, export plugins/MC2P/keystore.p12 "
                + "and pin it client-side; never insecureSkipVerify.");
    }

    private void status(CommandSender sender) {
        BackendConfig config = plugin.config();
        sender.sendMessage(PREFIX + "MC2P status");
        sender.sendMessage(ChatColor.GRAY + "  mode: " + ChatColor.WHITE + plugin.effectiveMode());
        sender.sendMessage(ChatColor.GRAY + "  serverId: " + ChatColor.WHITE + plugin.serverId());
        sender.sendMessage(ChatColor.GRAY + "  mcp endpoint: " + ChatColor.WHITE
                + config.mcp().bind() + ":" + config.mcp().port() + config.mcp().endpoint() + " (tls="
                + config.mcp().tls().mode() + ")");
        sender.sendMessage(ChatColor.GRAY + "  restart strategy: " + ChatColor.WHITE + config.restartStrategy());
        sender.sendMessage(ChatColor.GRAY + "  tools registered: " + ChatColor.WHITE
                + plugin.registry().size());
        for (Map.Entry<Role, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            TokenInfo info = e.getValue();
            sender.sendMessage(ChatColor.GRAY + "  token " + e.getKey().name().toLowerCase() + ": " + ChatColor.WHITE
                    + (info.configured() ? "(config) " : "(rotated) ") + info.tokenId());
        }
        sender.sendMessage(ChatColor.GRAY + "  audit log: " + ChatColor.WHITE
                + config.audit().file());
    }

    private void reload(CommandSender sender) {
        try {
            plugin.applyConfig();
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration reloaded.");
        } catch (RuntimeException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Reload failed: " + e.getMessage());
        }
    }

    private void rotate(CommandSender sender, String roleName) {
        Role role = Role.fromString(roleName);
        if (role == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown role: " + roleName);
            return;
        }
        String token = plugin.tokens().rotate(role);
        plugin.audit()
                .log(
                        null,
                        "console",
                        plugin.serverId(),
                        "token",
                        "rotate",
                        "{\"role\":\"" + role.name().toLowerCase() + "\"}");
        sender.sendMessage(PREFIX + ChatColor.GREEN + "New " + role.name().toLowerCase() + " token (shown once):");
        sender.sendMessage(ChatColor.YELLOW + token);
    }

    private void revoke(CommandSender sender, String roleName) {
        Role role = Role.fromString(roleName);
        if (role == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown role: " + roleName);
            return;
        }
        boolean revoked = plugin.tokens().revoke(role);
        plugin.audit()
                .log(
                        null,
                        "console",
                        plugin.serverId(),
                        "token",
                        "revoke",
                        "{\"role\":\"" + role.name().toLowerCase() + "\"}");
        if (revoked) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Rotated "
                    + role.name().toLowerCase() + " token revoked (config token, if any, is active again).");
        } else {
            sender.sendMessage(
                    PREFIX + ChatColor.YELLOW + "No rotated " + role.name().toLowerCase() + " token to revoke.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "  /mc2p setup");
        sender.sendMessage(ChatColor.GRAY + "  /mc2p status");
        sender.sendMessage(ChatColor.GRAY + "  /mc2p reload");
        sender.sendMessage(ChatColor.GRAY + "  /mc2p token rotate <reader|ops|admin>");
        sender.sendMessage(ChatColor.GRAY + "  /mc2p token revoke <reader|ops|admin>");
    }
}
