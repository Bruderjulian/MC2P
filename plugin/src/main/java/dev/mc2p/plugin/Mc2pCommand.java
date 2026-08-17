package dev.mc2p.plugin;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.mc2p.common.activity.ClientActivityTracker;
import dev.mc2p.common.role.Role;
import dev.mc2p.common.setup.SetupSupport;
import dev.mc2p.common.tokens.TokenManager;
import dev.mc2p.common.tokens.TokenManager.TokenInfo;
import dev.mc2p.plugin.config.BackendConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /mc2p} admin console: setup, status, reload, token create/revoke/list.
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
                .withSubcommand(new CommandAPICommand("activity")
                        .executes((CommandSender sender, CommandArguments args) -> activity(sender)))
                .withSubcommand(new CommandAPICommand("token")
                        .withSubcommand(new CommandAPICommand("create")
                                .withArguments(new StringArgument("name"))
                                .withArguments(new MultiLiteralArgument("role", roleLiterals()))
                                .executes((CommandSender sender, CommandArguments args) ->
                                        create(sender, (String) args.get("name"), (String) args.get("role"))))
                        .withSubcommand(new CommandAPICommand("revoke")
                                .withArguments(new StringArgument("name"))
                                .executes((CommandSender sender, CommandArguments args) ->
                                        revoke(sender, (String) args.get("name"))))
                        .withSubcommand(new CommandAPICommand("list")
                                .executes((CommandSender sender, CommandArguments args) -> list(sender))))
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
        for (Map.Entry<String, String> e : plugin.ensureTokens().entrySet()) {
            sender.sendMessage(PREFIX.append(
                    Component.text("Generated token '" + e.getKey() + "' (shown once):", NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("  " + e.getValue(), NamedTextColor.YELLOW));
        }
        for (Map.Entry<String, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            TokenInfo info = e.getValue();
            sender.sendMessage(Component.text(
                            "  " + info.name() + " [" + info.role() + "] token id: " + info.tokenId()
                                    + (info.configured() ? " (config)" : " (runtime)"),
                            NamedTextColor.GRAY)
                    .append(Component.text("", NamedTextColor.WHITE)));
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
                + "and <TOKEN> with a token for the role you grant the agent:")));
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
        for (Map.Entry<String, TokenInfo> e : plugin.tokens().snapshot().entrySet()) {
            TokenInfo info = e.getValue();
            sender.sendMessage(Component.text(
                            "  token " + info.name() + " [" + info.role() + "]: ", NamedTextColor.GRAY)
                    .append(Component.text(
                            (info.configured() ? "(config) " : "(runtime) ") + info.tokenId(), NamedTextColor.WHITE)));
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

    private void activity(CommandSender sender) {
        java.util.List<ClientActivityTracker.Entry> active = plugin.activity().active();
        int windowMinutes = plugin.config().auth().activityWindowMinutes();
        sender.sendMessage(PREFIX.append(Component.text("Active clients (last " + windowMinutes + " min):")));
        if (active.isEmpty()) {
            sender.sendMessage(Component.text("  none", NamedTextColor.GRAY));
            return;
        }
        for (ClientActivityTracker.Entry e : active) {
            sender.sendMessage(Component.text(
                    "  " + e.name() + " [" + e.role() + "] " + e.remoteIp() + " requests=" + e.requestCount() + " last="
                            + relativeTime(e.lastSeenMillis()),
                    NamedTextColor.GRAY));
        }
    }

    private static String relativeTime(long millis) {
        long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
        if (seconds < 60) {
            return seconds + "s ago";
        }
        return (seconds / 60) + "m " + (seconds % 60) + "s ago";
    }

    private void create(CommandSender sender, String name, String roleName) {
        if (!TokenManager.isValidName(name)) {
            sender.sendMessage(PREFIX.append(Component.text(
                    "Invalid token name: " + name + " (use letters, digits, - and _, max 40)", NamedTextColor.RED)));
            return;
        }
        Role role = Role.fromString(roleName);
        if (role == null) {
            sender.sendMessage(PREFIX.append(Component.text("Unknown role: " + roleName, NamedTextColor.RED)));
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
        sender.sendMessage(PREFIX.append(Component.text(
                "New token '" + name + "' (" + role.name().toLowerCase() + "), shown once:", NamedTextColor.GREEN)));
        sender.sendMessage(Component.text(token, NamedTextColor.YELLOW));
    }

    private void revoke(CommandSender sender, String name) {
        boolean revoked = plugin.tokens().revoke(name);
        plugin.audit()
                .log(null, "console", "console", plugin.serverId(), "token", "revoke", "{\"name\":\"" + name + "\"}");
        if (revoked) {
            sender.sendMessage(PREFIX.append(Component.text(
                    "Token '" + name + "' revoked (config token with the same name, if any, is active again).",
                    NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(PREFIX.append(
                    Component.text("No runtime token named '" + name + "' to revoke.", NamedTextColor.YELLOW)));
        }
    }

    private void list(CommandSender sender) {
        Map<String, TokenInfo> snapshot = plugin.tokens().snapshot();
        if (snapshot.isEmpty()) {
            sender.sendMessage(
                    PREFIX.append(Component.text("No tokens configured. Run /mc2p token create <name> <role>.")));
            return;
        }
        sender.sendMessage(PREFIX.append(Component.text("Tokens:")));
        for (Map.Entry<String, TokenInfo> e : snapshot.entrySet()) {
            TokenInfo info = e.getValue();
            sender.sendMessage(Component.text(
                    "  " + info.name() + " [" + info.role() + "] " + info.tokenId()
                            + (info.configured() ? " (config)" : " (runtime)"),
                    NamedTextColor.GRAY));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text("Usage:")));
        sender.sendMessage(Component.text("  /mc2p setup", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p status", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p reload", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p activity", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p token create <name> <reader|ops|admin>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p token revoke <name>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mc2p token list", NamedTextColor.GRAY));
    }
}
