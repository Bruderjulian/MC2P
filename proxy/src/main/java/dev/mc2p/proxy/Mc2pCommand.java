package dev.mc2p.proxy;

import com.velocitypowered.api.command.CommandSource;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.mc2p.common.activity.ClientActivityTracker;
import dev.mc2p.common.role.Role;
import dev.mc2p.common.setup.SetupSupport;
import dev.mc2p.common.tokens.TokenManager;
import dev.mc2p.common.tokens.TokenManager.TokenInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** {@code /mc2p} proxy console: setup, status, reload, servers, token create/revoke/list. */
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
                .withSubcommand(new CommandAPICommand("activity")
                        .executes((CommandSource sender, CommandArguments args) -> activity(sender)))
                .withSubcommand(new CommandAPICommand("token")
                        .withSubcommand(new CommandAPICommand("create")
                                .withArguments(new StringArgument("name"))
                                .withArguments(new MultiLiteralArgument("role", roleLiterals()))
                                .executes((CommandSource sender, CommandArguments args) ->
                                        create(sender, (String) args.get("name"), (String) args.get("role"))))
                        .withSubcommand(new CommandAPICommand("revoke")
                                .withArguments(new StringArgument("name"))
                                .executes((CommandSource sender, CommandArguments args) ->
                                        revoke(sender, (String) args.get("name"))))
                        .withSubcommand(new CommandAPICommand("disable")
                                .withArguments(new StringArgument("name"))
                                .executes((CommandSource sender, CommandArguments args) ->
                                        disable(sender, (String) args.get("name"))))
                        .withSubcommand(new CommandAPICommand("enable")
                                .withArguments(new StringArgument("name"))
                                .executes((CommandSource sender, CommandArguments args) ->
                                        enable(sender, (String) args.get("name"))))
                        .withSubcommand(new CommandAPICommand("list")
                                .executes((CommandSource sender, CommandArguments args) -> list(sender))))
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

        for (Map.Entry<String, String> e : plugin.ensureTokens().entrySet()) {
            source.sendMessage(
                    Component.text("Generated token '" + e.getKey() + "' (shown once):", NamedTextColor.GREEN));
            source.sendMessage(Component.text("  " + e.getValue(), NamedTextColor.YELLOW));
        }
        for (Map.Entry<String, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            TokenInfo info = e.getValue();
            source.sendMessage(Component.text("  " + info.name() + " [" + info.role() + "] token id: " + info.tokenId()
                    + (info.configured() ? " (config)" : " (runtime)") + (info.disabled() ? " (disabled)" : "")));
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
                Component.text("Agent mcpServers.json - replace <HOST> with your public host and <TOKEN> with a token "
                        + "for the role you grant the agent:"));
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
        for (Map.Entry<String, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            TokenInfo info = e.getValue();
            source.sendMessage(Component.text("  token " + info.name() + " [" + info.role() + "]: "
                    + (info.configured() ? "(config) " : "(runtime) ") + info.tokenId()
                    + (info.disabled() ? " (disabled)" : "")));
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

    private void activity(CommandSource source) {
        java.util.List<ClientActivityTracker.Entry> active = plugin.activity().active();
        int windowMinutes = plugin.config().auth().activityWindowMinutes();
        source.sendMessage(
                Component.text("[MC2P] Active clients (last " + windowMinutes + " min):", NamedTextColor.AQUA));
        if (active.isEmpty()) {
            source.sendMessage(Component.text("  none"));
            return;
        }
        for (ClientActivityTracker.Entry e : active) {
            source.sendMessage(Component.text("  " + e.name() + " [" + e.role() + "] " + e.remoteIp() + " requests="
                    + e.requestCount() + " last=" + relativeTime(e.lastSeenMillis())));
        }
    }

    private static String relativeTime(long millis) {
        long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
        if (seconds < 60) {
            return seconds + "s ago";
        }
        return (seconds / 60) + "m " + (seconds % 60) + "s ago";
    }

    private void create(CommandSource source, String name, String roleName) {
        if (!TokenManager.isValidName(name)) {
            source.sendMessage(Component.text(
                    "[MC2P] Invalid token name: " + name + " (use letters, digits, - and _, max 40)",
                    NamedTextColor.RED));
            return;
        }
        Role role = Role.fromString(roleName);
        if (role == null) {
            source.sendMessage(Component.text("[MC2P] Unknown role: " + roleName, NamedTextColor.RED));
            return;
        }
        String token = plugin.tokens().create(name, role);
        plugin.audit()
                .log(
                        null,
                        "console",
                        "console",
                        plugin.serverId(),
                        "token",
                        "create",
                        "{\"name\":\"" + name + "\",\"role\":\"" + role.name().toLowerCase() + "\"}");
        source.sendMessage(Component.text(
                "[MC2P] New token '" + name + "' (" + role.name().toLowerCase() + "), shown once:",
                NamedTextColor.GREEN));
        source.sendMessage(Component.text(token, NamedTextColor.YELLOW));
    }

    private void revoke(CommandSource source, String name) {
        boolean revoked = plugin.tokens().revoke(name);
        plugin.audit()
                .log(null, "console", "console", plugin.serverId(), "token", "revoke", "{\"name\":\"" + name + "\"}");
        if (revoked) {
            source.sendMessage(Component.text(
                    "[MC2P] Token '" + name + "' revoked (config token with the same name, if any, is active again).",
                    NamedTextColor.GREEN));
        } else {
            source.sendMessage(
                    Component.text("[MC2P] No runtime token named '" + name + "' to revoke.", NamedTextColor.YELLOW));
        }
    }

    private void disable(CommandSource source, String name) {
        boolean changed = plugin.tokens().disable(name);
        plugin.audit()
                .log(null, "console", "console", plugin.serverId(), "token", "disable", "{\"name\":\"" + name + "\"}");
        if (changed) {
            source.sendMessage(Component.text("[MC2P] Token '" + name + "' disabled.", NamedTextColor.GREEN));
        } else {
            source.sendMessage(
                    Component.text("[MC2P] No active token named '" + name + "' to disable.", NamedTextColor.YELLOW));
        }
    }

    private void enable(CommandSource source, String name) {
        boolean changed = plugin.tokens().enable(name);
        plugin.audit()
                .log(null, "console", "console", plugin.serverId(), "token", "enable", "{\"name\":\"" + name + "\"}");
        if (changed) {
            source.sendMessage(Component.text("[MC2P] Token '" + name + "' enabled.", NamedTextColor.GREEN));
        } else {
            source.sendMessage(
                    Component.text("[MC2P] No disabled token named '" + name + "' to enable.", NamedTextColor.YELLOW));
        }
    }

    private void list(CommandSource source) {
        Map<String, TokenInfo> snapshot = plugin.tokens().snapshot();
        if (snapshot.isEmpty()) {
            source.sendMessage(Component.text("[MC2P] No tokens configured. Run /mc2p token create <name> <role>."));
            return;
        }
        source.sendMessage(Component.text("[MC2P] Tokens:", NamedTextColor.AQUA));
        for (Map.Entry<String, TokenInfo> e : snapshot.entrySet()) {
            TokenInfo info = e.getValue();
            source.sendMessage(Component.text("  " + info.name() + " [" + info.role() + "] " + info.tokenId()
                    + (info.configured() ? " (config)" : " (runtime)") + (info.disabled() ? " (disabled)" : "")));
        }
    }

    private void help(CommandSource source) {
        source.sendMessage(Component.text("[MC2P] Usage:"));
        source.sendMessage(Component.text("  /mc2p setup"));
        source.sendMessage(Component.text("  /mc2p status"));
        source.sendMessage(Component.text("  /mc2p reload"));
        source.sendMessage(Component.text("  /mc2p servers"));
        source.sendMessage(Component.text("  /mc2p activity"));
        source.sendMessage(Component.text("  /mc2p token create <name> <reader|ops|admin>"));
        source.sendMessage(Component.text("  /mc2p token revoke <name>"));
        source.sendMessage(Component.text("  /mc2p token disable <name>"));
        source.sendMessage(Component.text("  /mc2p token enable <name>"));
        source.sendMessage(Component.text("  /mc2p token list"));
    }
}
