package dev.mc2p.proxy;

import com.velocitypowered.api.command.CommandSource;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.mc2p.common.role.Role;
import dev.mc2p.common.setup.SetupSupport;
import dev.mc2p.common.tokens.TokenManager.TokenInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** {@code /mc2p} proxy console: setup, status, reload, servers, token rotate/revoke. */
public final class Mc2pCommand {

    private final McpProxyPlugin plugin;

    public Mc2pCommand(McpProxyPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("mc2p")
                .withPermission("mc2p.admin")
                .withSubcommand(new CommandAPICommand("setup")
                        .executes((CommandSource sender, CommandArguments args) -> setup(sender)))
                .withSubcommand(new CommandAPICommand("status")
                        .executes((CommandSource sender, CommandArguments args) -> status(sender)))
                .withSubcommand(new CommandAPICommand("reload")
                        .executes((CommandSource sender, CommandArguments args) -> reload(sender)))
                .withSubcommand(new CommandAPICommand("servers")
                        .executes((CommandSource sender, CommandArguments args) -> servers(sender)))
                .withSubcommand(new CommandAPICommand("token")
                        .withSubcommand(new CommandAPICommand("rotate")
                                .withArguments(new MultiLiteralArgument("role", roleLiterals()))
                                .executes((CommandSource sender, CommandArguments args) ->
                                        rotate(sender, (String) args.get("role"))))
                        .withSubcommand(new CommandAPICommand("revoke")
                                .withArguments(new MultiLiteralArgument("role", roleLiterals()))
                                .executes((CommandSource sender, CommandArguments args) ->
                                        revoke(sender, (String) args.get("role")))))
                .withSubcommand(new CommandAPICommand("help")
                        .executes((CommandSource sender, CommandArguments args) -> help(sender)))
                .executes((CommandSource sender, CommandArguments args) -> help(sender))
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

    private void setup(CommandSource source) {
        var config = plugin.config();
        source.sendMessage(Component.text("[MC2P] setup", NamedTextColor.AQUA));

        for (Map.Entry<Role, String> e : plugin.ensureTokens().entrySet()) {
            source.sendMessage(Component.text(
                    "Generated " + e.getKey().name().toLowerCase() + " token (shown once):", NamedTextColor.GREEN));
            source.sendMessage(Component.text("  " + e.getValue(), NamedTextColor.YELLOW));
        }
        for (Map.Entry<Role, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            source.sendMessage(Component.text("  " + e.getKey().name().toLowerCase() + " token id: "
                    + e.getValue().tokenId()));
        }

        String secret = plugin.proxySecret();
        if (secret == null) {
            secret = plugin.ensureProxySecret();
            source.sendMessage(Component.text(
                    "Generated shared proxy secret (shown once) - set it on EVERY backend:", NamedTextColor.GREEN));
        } else {
            source.sendMessage(Component.text(
                    "Shared proxy secret (set it on EVERY backend exactly as shown):", NamedTextColor.GREEN));
        }
        source.sendMessage(Component.text("  " + secret, NamedTextColor.YELLOW));
        source.sendMessage(Component.text(
                "  On each backend: export " + config.rpc().secretEnv() + "=\"...\" or place it in "
                        + "plugins/MC2P/proxy-secret, then restart/reload.",
                NamedTextColor.GRAY));

        plugin.activateBackends();
        source.sendMessage(Component.text(
                "  backends activated: " + plugin.backendServerIds().size()));

        String template = SetupSupport.clientConfigTemplate(config.mcp().port());
        try {
            Files.writeString(plugin.dataDirectory().resolve("mcpServers.json"), template);
            source.sendMessage(Component.text(
                    "Client template written to plugins/mc2p-proxy/mcpServers.json", NamedTextColor.GREEN));
        } catch (IOException ex) {
            source.sendMessage(
                    Component.text("Could not write mcpServers.json: " + ex.getMessage(), NamedTextColor.RED));
        }
        source.sendMessage(
                Component.text("Agent mcpServers.json - replace <HOST> with your public host and <TOKEN> with the "
                        + "token of the role you grant the agent:"));
        source.sendMessage(Component.text("  " + template));
        source.sendMessage(Component.text(
                "Trust: with tls.mode=selfsigned, export the proxy's generated cert and pin it "
                        + "client-side; never insecureSkipVerify.",
                NamedTextColor.GRAY));
    }

    private void status(CommandSource source) {
        var config = plugin.config();
        source.sendMessage(Component.text("[MC2P] status", NamedTextColor.AQUA));
        source.sendMessage(Component.text("  serverId: " + plugin.serverId()));
        source.sendMessage(Component.text("  mcp endpoint: " + config.mcp().bind() + ":"
                + config.mcp().port() + config.mcp().endpoint() + " (tls="
                + config.mcp().tls().mode() + ")"));
        source.sendMessage(
                Component.text("  backends: " + plugin.backendServerIds().size()));
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

    private void rotate(CommandSource source, String roleName) {
        Role role = Role.fromString(roleName);
        if (role == null) {
            source.sendMessage(Component.text("[MC2P] Unknown role: " + roleName, NamedTextColor.RED));
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
        source.sendMessage(Component.text(
                "[MC2P] New " + role.name().toLowerCase() + " token (shown once):", NamedTextColor.GREEN));
        source.sendMessage(Component.text(token, NamedTextColor.YELLOW));
    }

    private void revoke(CommandSource source, String roleName) {
        Role role = Role.fromString(roleName);
        if (role == null) {
            source.sendMessage(Component.text("[MC2P] Unknown role: " + roleName, NamedTextColor.RED));
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
            source.sendMessage(Component.text(
                    "[MC2P] Rotated " + role.name().toLowerCase()
                            + " token revoked (config token, if any, is active again).",
                    NamedTextColor.GREEN));
        } else {
            source.sendMessage(Component.text(
                    "[MC2P] No rotated " + role.name().toLowerCase() + " token to revoke.", NamedTextColor.YELLOW));
        }
    }

    private void help(CommandSource source) {
        source.sendMessage(Component.text("[MC2P] Usage:"));
        source.sendMessage(Component.text("  /mc2p setup"));
        source.sendMessage(Component.text("  /mc2p status"));
        source.sendMessage(Component.text("  /mc2p reload"));
        source.sendMessage(Component.text("  /mc2p servers"));
        source.sendMessage(Component.text("  /mc2p token rotate|revoke <reader|ops|admin>"));
    }
}
