package dev.mc2p.plugin.rpc;

import dev.mc2p.common.json.Json;
import dev.mc2p.common.rpc.RpcMessage;
import dev.mc2p.common.util.Tokens;
import dev.mc2p.plugin.tools.AuthContext;
import dev.mc2p.plugin.tools.ToolInvoker;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backend-mode listener for the {@code mc2p:rpc} plugin-messaging channel. Authenticates
 * the proxy with the shared {@code proxySecret}, then serves tool requests carrying the
 * client role (re-checked by the tool layer). Backends behind a proxy open zero HTTP
 * ports.
 */
public final class BackendRpcServer implements PluginMessageListener {

    private static final Logger log = LoggerFactory.getLogger(BackendRpcServer.class);

    private final Plugin plugin;
    private final ToolInvoker invoker;
    private final String serverId;
    private final String channel;
    private final String proxySecret;
    private final long timeoutNanos;

    private static final class TrustedSender {
        final Player player;
        final long expires;

        TrustedSender(Player player, long expires) {
            this.player = player;
            this.expires = expires;
        }
    }

    private final ConcurrentHashMap<String, TrustedSender> trustedSenders = new ConcurrentHashMap<>();

    public BackendRpcServer(
            Plugin plugin,
            ToolInvoker invoker,
            String serverId,
            String channel,
            String proxySecret,
            long timeoutMillis) {
        this.plugin = plugin;
        this.invoker = invoker;
        this.serverId = serverId;
        this.channel = channel;
        this.proxySecret = proxySecret;
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1000, timeoutMillis));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!this.channel.equals(channel)) {
            return;
        }
        Map<String, Object> decoded;
        try {
            decoded = Json.parse(message);
        } catch (RuntimeException e) {
            log.warn("mc2p:rpc: malformed message from {}", senderName(player));
            return;
        }
        String type = RpcMessage.type(decoded);
        if ("hello".equals(type)) {
            handleHello(player, decoded);
        } else if ("req".equals(type)) {
            handleRequest(player, decoded);
        } else if ("ping".equals(type)) {
            handlePing(player);
        }
    }

    private void handleHello(Player player, Map<String, Object> message) {
        String presented = String.valueOf(message.get("secret"));
        if (proxySecret == null || proxySecret.isBlank()) {
            send(player, RpcMessage.helloNo("proxy secret not configured on backend"));
            return;
        }
        if (!Tokens.constantTimeEquals(presented, proxySecret)) {
            log.warn("mc2p:rpc: rejected hello from {}: bad proxy secret", senderName(player));
            send(player, RpcMessage.helloNo("bad proxy secret"));
            return;
        }
        trustedSenders.put(
                senderKey(player), new TrustedSender(player, System.nanoTime() + TimeUnit.MINUTES.toNanos(5)));
        send(player, RpcMessage.helloOk(serverId));
        log.info("mc2p:rpc: proxy {} authenticated", senderName(player));
    }

    private void handlePing(Player player) {
        send(player, Map.of("t", "pong", "serverId", serverId));
    }

    private void handleRequest(Player player, Map<String, Object> message) {
        if (!isTrusted(player)) {
            log.warn("mc2p:rpc: dropping request from unauthenticated sender {}", senderName(player));
            return;
        }
        String id = RpcMessage.id(message);
        String method = String.valueOf(message.get("method"));
        String role = String.valueOf(message.getOrDefault("role", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());

        if (id == null || method.isBlank()) {
            return;
        }
        dev.mc2p.common.role.Role parsedRole = dev.mc2p.common.role.Role.fromString(role);
        if (parsedRole == null) {
            sendResponse(player, id, RpcMessage.response(id, false, null, "invalid role in envelope"));
            return;
        }
        AuthContext auth = new AuthContext(parsedRole, "rpc:" + parsedRole, senderAddress(player), "rpc");
        try {
            var result = invoker.invoke(method, params, auth);
            Map<String, Object> encoded = RpcResultCodec.encode(result);
            boolean ok = Boolean.TRUE.equals(encoded.get("ok"));
            Map<String, Object> response = ok
                    ? RpcMessage.response(id, true, encoded.get("result"), null)
                    : RpcMessage.response(id, false, null, String.valueOf(encoded.get("error")));
            sendResponse(player, id, response);
        } catch (RuntimeException e) {
            sendResponse(player, id, RpcMessage.response(id, false, null, "backend error"));
        }
    }

    private void sendResponse(Player player, String id, Map<String, Object> response) {
        byte[] jsonBytes = Json.toJsonBytes(response);
        for (Map<String, Object> message : RpcMessage.encodeResponse(id, jsonBytes)) {
            send(player, message);
        }
    }

    private void send(Player player, Map<String, Object> message) {
        try {
            player.sendPluginMessage(plugin, channel, Json.toJsonBytes(message));
        } catch (RuntimeException e) {
            log.warn("mc2p:rpc: failed to send to {}: {}", senderName(player), e.getMessage());
        }
    }

    private boolean isTrusted(Player player) {
        TrustedSender sender = trustedSenders.get(senderKey(player));
        return sender != null && System.nanoTime() < sender.expires;
    }

    /**
     * Pushes a {@code event} message to every currently authenticated proxy. Used by the
     * plugin to signal player join/leave so the proxy can forward a resource-list-changed
     * SSE notification to connected MCP clients.
     */
    public void notifyEvent(String event, Map<String, Object> params) {
        if (trustedSenders.isEmpty()) {
            return;
        }
        byte[] payload = Json.toJsonBytes(RpcMessage.event(event, params));
        for (TrustedSender sender : trustedSenders.values()) {
            if (System.nanoTime() >= sender.expires) {
                trustedSenders.remove(senderKey(sender.player));
                continue;
            }
            try {
                sender.player.sendPluginMessage(plugin, channel, payload);
            } catch (RuntimeException e) {
                log.warn("mc2p:rpc: failed to push event to {}: {}", senderName(sender.player), e.getMessage());
            }
        }
    }

    private String senderKey(Player player) {
        return player.getUniqueId().toString();
    }

    private String senderName(Player player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private String senderAddress(Player player) {
        try {
            java.net.InetSocketAddress a = player.getAddress();
            return a == null
                    ? ""
                    : String.valueOf(
                            a.getAddress() == null
                                    ? a.getHostString()
                                    : a.getAddress().getHostAddress());
        } catch (Exception e) {
            return "";
        }
    }
}
