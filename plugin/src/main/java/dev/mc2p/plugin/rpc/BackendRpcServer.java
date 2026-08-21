package dev.mc2p.plugin.rpc;

import dev.mc2p.common.config.RestrictionsConfig;
import dev.mc2p.common.json.Json;
import dev.mc2p.common.rpc.AuthContext;
import dev.mc2p.common.rpc.RpcMessage;
import dev.mc2p.common.tokens.Tokens;
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
 * Backend-mode listener for the {@code mc2p:rpc} plugin-messaging channel.
 * Authenticates
 * the proxy with the shared {@code proxySecret}, then serves tool requests
 * carrying the
 * client restrictions (re-checked by the tool layer and intersected with this
 * backend's
 * own {@code server-restrictions}). Backends behind a proxy open zero HTTP
 * ports.
 */
public final class BackendRpcServer implements PluginMessageListener {

    private static final Logger log = LoggerFactory.getLogger(BackendRpcServer.class);

    private final Plugin plugin;
    private final ToolInvoker invoker;
    private final RestrictionsConfig serverRestrictions;
    private final String serverId;
    private final String channel;
    private final String proxySecret;
    private final long timeoutNanos;

    private static final class TrustedSender {
        final Player player;
        final long expires;

        TrustedSender(final Player player, final long expires) {
            this.player = player;
            this.expires = expires;
        }
    }

    private final ConcurrentHashMap<String, TrustedSender> trustedSenders = new ConcurrentHashMap<>();

    public BackendRpcServer(
            final Plugin plugin,
            final ToolInvoker invoker,
            final RestrictionsConfig serverRestrictions,
            final String serverId,
            final String channel,
            final String proxySecret,
            final long timeoutMillis) {
        this.plugin = plugin;
        this.invoker = invoker;
        this.serverRestrictions = serverRestrictions;
        this.serverId = serverId;
        this.channel = channel;
        this.proxySecret = proxySecret;
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1000, timeoutMillis));
    }

    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if (!this.channel.equals(channel)) {
            return;
        }
        Map<String, Object> decoded;
        try {
            decoded = Json.parse(message);
        } catch (final RuntimeException e) {
            log.warn("mc2p:rpc: malformed message from {}", senderName(player));
            return;
        }
        final String type = RpcMessage.type(decoded);
        if ("hello".equals(type)) {
            handleHello(player, decoded);
        } else if ("req".equals(type)) {
            handleRequest(player, decoded);
        } else if ("ping".equals(type)) {
            handlePing(player);
        }
    }

    private void handleHello(final Player player, final Map<String, Object> message) {
        final String presented = String.valueOf(message.get("secret"));
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

    private void handlePing(final Player player) {
        send(player, Map.of("t", "pong", "serverId", serverId));
    }

    private void handleRequest(final Player player, final Map<String, Object> message) {
        if (!isTrusted(player)) {
            log.warn("mc2p:rpc: dropping request from unauthenticated sender {}", senderName(player));
            return;
        }
        final String id = RpcMessage.id(message);
        final String method = String.valueOf(message.get("method"));
        final String client = String.valueOf(message.getOrDefault("client", ""));
        @SuppressWarnings("unchecked")
        final Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());
        @SuppressWarnings("unchecked")
        final Map<String, Object> envelopeRestrictions = (Map<String, Object>) message.getOrDefault("restrictions",
                Map.of());

        if (id == null || method.isBlank()) {
            return;
        }
        final RestrictionsConfig parsed = RestrictionsConfig.load(envelopeRestrictions);
        final String tokenId = String.valueOf(message.getOrDefault("tokenId", ""));
        final RestrictionsConfig effective = serverRestrictions.merge(parsed);
        final AuthContext auth = new AuthContext(effective, client, tokenId, senderAddress(player), "rpc");
        try {
            final var result = invoker.invoke(method, params, auth);
            final Map<String, Object> encoded = RpcResultCodec.encode(result);
            final boolean ok = Boolean.TRUE.equals(encoded.get("ok"));
            final Map<String, Object> response = ok
                    ? RpcMessage.response(id, true, encoded.get("result"), null)
                    : RpcMessage.response(id, false, null, String.valueOf(encoded.get("error")));
            sendResponse(player, id, response);
        } catch (final RuntimeException e) {
            sendResponse(player, id, RpcMessage.response(id, false, null, "backend error"));
        }
    }

    private void sendResponse(final Player player, final String id, final Map<String, Object> response) {
        final byte[] jsonBytes = Json.toJsonBytes(response);
        for (final Map<String, Object> message : RpcMessage.encodeResponse(id, jsonBytes)) {
            send(player, message);
        }
    }

    private void send(final Player player, final Map<String, Object> message) {
        try {
            player.sendPluginMessage(plugin, channel, Json.toJsonBytes(message));
        } catch (final RuntimeException e) {
            log.warn("mc2p:rpc: failed to send to {}: {}", senderName(player), e.getMessage());
        }
    }

    private boolean isTrusted(final Player player) {
        final TrustedSender sender = trustedSenders.get(senderKey(player));
        return sender != null && System.nanoTime() < sender.expires;
    }

    /**
     * Pushes a {@code event} message to every currently authenticated proxy. Used
     * by the
     * plugin to signal player join/leave so the proxy can forward a
     * resource-list-changed
     * SSE notification to connected MCP clients.
     */
    public void notifyEvent(final String event, final Map<String, Object> params) {
        if (trustedSenders.isEmpty()) {
            return;
        }
        final byte[] payload = Json.toJsonBytes(RpcMessage.event(event, params));
        for (final TrustedSender sender : trustedSenders.values()) {
            if (System.nanoTime() >= sender.expires) {
                trustedSenders.remove(senderKey(sender.player));
                continue;
            }
            try {
                sender.player.sendPluginMessage(plugin, channel, payload);
            } catch (final RuntimeException e) {
                log.warn("mc2p:rpc: failed to push event to {}: {}", senderName(sender.player), e.getMessage());
            }
        }
    }

    private String senderKey(final Player player) {
        return player.getUniqueId().toString();
    }

    private String senderName(final Player player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private String senderAddress(final Player player) {
        try {
            final java.net.InetSocketAddress a = player.getAddress();
            return a == null
                    ? ""
                    : String.valueOf(
                            a.getAddress() == null
                                    ? a.getHostString()
                                    : a.getAddress().getHostAddress());
        } catch (final Exception e) {
            return "";
        }
    }
}
