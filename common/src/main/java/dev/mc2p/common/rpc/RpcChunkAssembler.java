package dev.mc2p.common.rpc;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reassembles chunked RPC responses (proxy side) and splits large requests
 * (proxy side,
 * for the backend) back into message maps.
 */
public final class RpcChunkAssembler {

    private static final class Assembly {
        final int count;
        final byte[][] parts;
        int received = 0;

        Assembly(final int count) {
            this.count = count;
            this.parts = new byte[count][];
        }
    }

    private final Map<String, Assembly> pending = new HashMap<>();
    private final int maxChunks;

    public RpcChunkAssembler() {
        this(-1);
    }

    /**
     * @param maxChunks reject responses announced with more chunks than this (<=0 =
     *                  unlimited)
     */
    public RpcChunkAssembler(final int maxChunks) {
        this.maxChunks = maxChunks;
    }

    /**
     * Feeds a {@code chunk} message; returns the fully reassembled bytes once all
     * chunks
     * for the id are present.
     */
    public Optional<byte[]> addChunk(final Map<String, Object> chunk) {
        final String id = RpcMessage.id(chunk);
        if (id == null) {
            return Optional.empty();
        }
        final int idx = (int) ((Number) chunk.getOrDefault("idx", -1));
        final int count = (int) ((Number) chunk.getOrDefault("count", -1));
        if (idx < 0 || count <= 0 || idx >= count) {
            return Optional.empty();
        }
        if (maxChunks > 0 && count > maxChunks) {
            return Optional.empty();
        }
        final String data = String.valueOf(chunk.get("data"));
        byte[] part;
        try {
            part = Base64.getDecoder().decode(data);
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
        final Assembly assembly = pending.computeIfAbsent(id, k -> new Assembly(count));
        if (idx < assembly.parts.length && assembly.parts[idx] == null) {
            assembly.parts[idx] = part;
            assembly.received++;
        }
        if (assembly.received == assembly.count) {
            pending.remove(id);
            int total = 0;
            for (final byte[] p : assembly.parts) {
                total += p.length;
            }
            final byte[] result = new byte[total];
            int offset = 0;
            for (final byte[] p : assembly.parts) {
                System.arraycopy(p, 0, result, offset, p.length);
                offset += p.length;
            }
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public void clear() {
        pending.clear();
    }
}
