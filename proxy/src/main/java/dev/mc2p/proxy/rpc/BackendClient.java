package dev.mc2p.proxy.rpc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import dev.mc2p.common.json.Json;
import dev.mc2p.common.rpc.RpcChunkAssembler;
import dev.mc2p.common.rpc.RpcMessage;

/**
 * Proxy-side relay over {@code mc2p:rpc}. Sends an authenticated {@code hello} before each
 * request (plugin-messaging ordering guarantees the backend processes it first, so the
 * shared {@code proxySecret} handshake is enforced on every call), correlates responses by
 * id, and reassembles chunked responses. Runs entirely on Velocity/event threads.
 */
public final class BackendClient {

    private static final Logger log = LoggerFactory.getLogger(BackendClient.class);

    private final ChannelIdentifier channel;
    private final String proxySecret;
    private final long timeoutMillis;
    private final long helloWindowNanos;
    private final Runnable resourceChangedHook;

    /** velocity server name → serverId (reverse of the config servers map). */
    private final Map<String, String> nameToServerId = new ConcurrentHashMap<>();

    private static final class Connection {
        volatile RegisteredServer server;
        volatile long authenticatedUntil = 0;
        final Map<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
        final RpcChunkAssembler assembler = new RpcChunkAssembler();
    }

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public BackendClient(ChannelIdentifier channel, String proxySecret, long timeoutMillis, long helloWindowNanos,
            Runnable resourceChangedHook) {
        this.channel = channel;
        this.proxySecret = proxySecret;
        this.timeoutMillis = Math.max(1000, timeoutMillis);
        this.helloWindowNanos = helloWindowNanos;
        this.resourceChangedHook = resourceChangedHook;
    }

    public void registerServer(String serverId, RegisteredServer server) {
        nameToServerId.put(server.getServerInfo().getName(), serverId);
        Connection conn = connections.computeIfAbsent(serverId, k -> new Connection());
        conn.server = server;
        send(server, RpcMessage.hello(proxySecret));
    }

    public void unregisterServer(String serverName) {
        String serverId = nameToServerId.remove(serverName);
        if (serverId != null) {
            Connection conn = connections.get(serverId);
            if (conn != null) {
                for (CompletableFuture<Map<String, Object>> f : conn.pending.values()) {
                    f.complete(null);
                }
                conn.pending.clear();
            }
        }
    }

    public java.util.List<String> knownServerIds() {
        return new java.util.ArrayList<>(connections.keySet());
    }

    /** Resolves a Velocity server name to its configured serverId, if registered. */
    public Optional<String> serverIdForVelocityName(String name) {
        return Optional.ofNullable(nameToServerId.get(name));
    }

    /**
     * Relays one tool call to a backend and blocks until the response arrives or the
     * timeout elapses.
     *
     * @return the {@code resp} message ({@code ok/result|error}), or empty if unreachable
     */
    public Optional<Map<String, Object>> call(String serverId, String method, String role,
            Map<String, Object> params) {
        Connection conn = connections.get(serverId);
        if (conn == null) {
            return Optional.empty();
        }
        RegisteredServer server = conn.server;
        if (server == null) {
            return Optional.empty();
        }

        String id = UUID.randomUUID().toString();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        conn.pending.put(id, future);
        try {
            if (System.nanoTime() > conn.authenticatedUntil) {
                send(server, RpcMessage.hello(proxySecret));
            }
            send(server, RpcMessage.request(id, method, role, params));
            Map<String, Object> response = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return response == null ? Optional.empty() : Optional.of(response);
        } catch (TimeoutException e) {
            log.warn("mc2p:rpc: request {} to {} timed out after {}ms", id, serverId, timeoutMillis);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("mc2p:rpc: request {} to {} failed: {}", id, serverId, e.getMessage());
            return Optional.empty();
        } finally {
            conn.pending.remove(id);
        }
    }

    /** Handles an inbound plugin message from a backend server. */
    public void handleServerMessage(RegisteredServer server, byte[] data) {
        String serverId = nameToServerId.get(server.getServerInfo().getName());
        if (serverId == null) {
            return;
        }
        Connection conn = connections.get(serverId);
        if (conn == null) {
            return;
        }
        Map<String, Object> decoded;
        try {
            decoded = Json.parse(data);
        } catch (RuntimeException e) {
            log.warn("mc2p:rpc: malformed message from {}", serverId);
            return;
        }
        String type = RpcMessage.type(decoded);
        switch (type == null ? "" : type) {
            case "hello-ok" -> {
                Object reported = decoded.get("serverId");
                if (reported != null && !String.valueOf(reported).equals(serverId)) {
                    log.warn("mc2p:rpc: server {} reported serverId '{}'", serverId, reported);
                }
                conn.authenticatedUntil = System.nanoTime() + helloWindowNanos;
            }
            case "hello-no" -> {
                log.warn("mc2p:rpc: backend {} rejected handshake: {}", serverId, decoded.get("error"));
                conn.authenticatedUntil = 0;
            }
            case "resp" -> complete(conn, RpcMessage.id(decoded), decoded);
            case "chunk" -> {
                Optional<byte[]> reassembled = conn.assembler.addChunk(decoded);
                if (reassembled.isPresent()) {
                    try {
                        Map<String, Object> response = Json.parse(reassembled.get());
                        complete(conn, RpcMessage.id(response), response);
                    } catch (RuntimeException e) {
                        log.warn("mc2p:rpc: failed to parse reassembled response from {}", serverId);
                    }
                }
            }
            case "event" -> notifyClients();
            default -> {
            }
        }
    }

    private void complete(Connection conn, String id, Map<String, Object> response) {
        if (id == null) {
            return;
        }
        CompletableFuture<Map<String, Object>> future = conn.pending.remove(id);
        if (future != null) {
            future.complete(response);
        }
    }

    private void notifyClients() {
        if (resourceChangedHook != null) {
            resourceChangedHook.run();
        }
    }

    private void send(RegisteredServer server, Map<String, Object> message) {
        try {
            server.sendPluginMessage(channel, Json.toJsonBytes(message));
        } catch (RuntimeException e) {
            log.warn("mc2p:rpc: failed to send to {}: {}", server.getServerInfo().getName(), e.getMessage());
        }
    }
}