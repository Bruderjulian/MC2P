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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /mc2p} admin console: setup, status, reload, token rotate, token revoke.
 */
public final class Mc2pCommand {

    private static final Component PREFIX = Component.text("[MC2P] ", NamedTextColor.AQUA);

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
                sender.sendMessage(PREFIX.append(Component.text(
                        "Backend mode: no proxy secret is set. "
                                + "Set " + config.proxy().secretEnv() + " or place plugins/MC2P/proxy-secret, "
                                + "then run /mc2p reload.",
                        NamedTextColor.RED)));
                return;
            }
            sender.sendMessage(PREFIX.append(Component.text("MC2P backend is active.")));
            sender.sendMessage(Component.text("  serverId: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.serverId(), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  rpc channel: ", NamedTextColor.GRAY)
                    .append(Component.text(config.proxy().rpcChannel(), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  proxy secret: ", NamedTextColor.GRAY)
                    .append(Component.text("set", NamedTextColor.GREEN)));
            sender.sendMessage(PREFIX.append(Component.text("Backends hold no API tokens - the proxy owns them. "
                    + "Agent config lives on the proxy (run /mc2p setup there).")));
            return;
        }

        sender.sendMessage(PREFIX.append(Component.text("MC2P setup (standalone)")));
        for (Map.Entry<Role, String> e : plugin.ensureTokens().entrySet()) {
            sender.sendMessage(PREFIX.append(Component.text(
                    "Generated " + e.getKey().name().toLowerCase() + " token (shown once):", NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("  " + e.getValue(), NamedTextColor.YELLOW));
        }
        for (Map.Entry<Role, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            sender.sendMessage(
                    Component.text("  " + e.getKey().name().toLowerCase() + " token id: ", NamedTextColor.GRAY)
                            .append(Component.text(e.getValue().tokenId(), NamedTextColor.WHITE)));
        }
        sender.sendMessage(Component.text("  endpoint: ", NamedTextColor.GRAY)
                .append(Component.text(
                        config.mcp().bind() + ":" + config.mcp().port()
                                + config.mcp().endpoint() + " (tls="
                                + config.mcp().tls().mode() + ")",
                        NamedTextColor.WHITE)));

        String template = SetupSupport.clientConfigTemplate(config.mcp().port());
        try {
            Files.writeString(plugin.dataDirectory().resolve("mcpServers.json"), template);
            sender.sendMessage(PREFIX.append(
                    Component.text("Client template written to plugins/MC2P/mcpServers.json", NamedTextColor.GREEN)));
        } catch (IOException ex) {
            sender.sendMessage(PREFIX.append(
                    Component.text("Could not write mcpServers.json: " + ex.getMessage(), NamedTextColor.RED)));
        }
        sender.sendMessage(PREFIX.append(Component.text("Agent mcpServers.json - replace <HOST> with your public host "
                + "and <TOKEN> with the token of the role you grant the agent:")));
        sender.sendMessage(Component.text(template, NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
                "Trust: with tls.mode=selfsigned, export plugins/MC2P/keystore.p12 "
                        + "and pin it client-side; never insecureSkipVerify.",
                NamedTextColor.GRAY));
    }

    private void status(CommandSender sender) {
        BackendConfig config = plugin.config();
        sender.sendMessage(PREFIX.append(Component.text("MC2P status")));
        sender.sendMessage(Component.text("  mode: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.effectiveMode(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  serverId: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.serverId(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  mcp endpoint: ", NamedTextColor.GRAY)
                .append(Component.text(
                        config.mcp().bind() + ":" + config.mcp().port()
                                + config.mcp().endpoint() + " (tls="
                                + config.mcp().tls().mode() + ")",
                        NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  restart strategy: ", NamedTextColor.GRAY)
                .append(Component.text(config.restartStrategy(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  tools registered: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.registry().size(), NamedTextColor.WHITE)));
        for (Map.Entry<Role, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            TokenInfo info = e.getValue();
            sender.sendMessage(Component.text("  token " + e.getKey().name().toLowerCase() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(
                            (info.configured() ? "(config) " : "(rotated) ") + info.tokenId(), NamedTextColor.WHITE)));
        }
        sender.sendMessage(Component.text("  audit log: ", NamedTextColor.GRAY)
                .append(Component.text(config.audit().file(), NamedTextColor.WHITE)));
    }

    private void reload(CommandSender sender) {
        try {
            plugin.applyConfig();
            sender.sendMessage(PREFIX.append(Component.text("Configuration reloaded.", NamedTextColor.GREEN)));
        } catch (RuntimeException e) {
            sender.sendMessage(PREFIX.append(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED)));
        }
    }

    private void rotate(CommandSender sender, String roleName) {
        Role role = Role.fromString(roleName);
        if (role == null) {
            sender.sendMessage(PREFIX.append(Component.text("Unknown role: " + roleName, NamedTextColor.RED)));
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
        sender.sendMessage(PREFIX.append(
                Component.text("New " + role.name().toLowerCase() + " token (shown once):", NamedTextColor.GREEN)));
        sender.sendMessage(Component.text(token, NamedTextColor.YELLOW));
    }

    private void revoke(CommandSender sender, String roleName) {
        Role role = Role.fromString(roleName);
        if (role == null) {
            sender.sendMessage(PREFIX.append(Component.text("Unknown role: " + roleName, NamedTextColor.RED)));
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
            sender.sendMessage(PREFIX.append(Component.text(
                    "Rotated " + role.name().toLowerCase() + " token revoked (config token, if any, is active again).",
                    NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(PREFIX.append(Component.text(
                    "No rotated " + role.name().toLowerCase() + " token to revoke.", NamedTextColor.YELLOW)));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text("Usage:")));
        sender.sendMessage(Component.text("  /mc2p setup", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p status", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p reload", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p token rotate <reader|ops|admin>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p token revoke <reader|ops|admin>", NamedTextColor.GRAY));
    }
}
