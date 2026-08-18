package dev.mc2p.common.rpc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RpcChunkAssemblerTest {

    private static Map<String, Object> chunk(String id, int idx, int count, byte[] data) {
        Map<String, Object> m = new HashMap<>();
        m.put("t", "chunk");
        m.put("id", id);
        m.put("idx", idx);
        m.put("count", count);
        m.put("data", Base64.getEncoder().encodeToString(data));
        return m;
    }

    @Test
    void missingIdIgnored() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        Map<String, Object> noId = chunk("x", 0, 1, new byte[] {1});
        noId.remove("id");
        assertEquals(Optional.empty(), assembler.addChunk(noId));
    }

    @Test
    void invalidIndexRejected() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        assertEquals(Optional.empty(), assembler.addChunk(chunk("a", -1, 2, new byte[] {1})));
        assertEquals(Optional.empty(), assembler.addChunk(chunk("a", 0, 0, new byte[] {1})));
        assertEquals(Optional.empty(), assembler.addChunk(chunk("a", 2, 2, new byte[] {1})));
        assertEquals(Optional.empty(), assembler.addChunk(chunk("a", 0, -5, new byte[] {1})));
    }

    @Test
    void tooManyChunksRejectedWhenLimited() {
        RpcChunkAssembler assembler = new RpcChunkAssembler(2);
        assertEquals(Optional.empty(), assembler.addChunk(chunk("a", 0, 3, new byte[] {1})));
    }

    @Test
    void maxChunksLimitAllowsBoundaryCount() {
        RpcChunkAssembler assembler = new RpcChunkAssembler(3);
        assertTrue(assembler.addChunk(chunk("a", 0, 3, new byte[] {1})).isEmpty());
        RpcChunkAssembler exact = new RpcChunkAssembler(1);
        assertArrayEquals(new byte[] {9}, exact.addChunk(chunk("b", 0, 1, new byte[] {9})).orElseThrow());
    }

    @Test
    void chunkWithConflictingCountIgnored() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        assertTrue(assembler.addChunk(chunk("a", 0, 2, new byte[] {1})).isEmpty());
        assertTrue(assembler.addChunk(chunk("a", 4, 5, new byte[] {2})).isEmpty());
        assertArrayEquals(new byte[] {1, 3}, assembler.addChunk(chunk("a", 1, 2, new byte[] {3})).orElseThrow());
    }

    @Test
    void unlimitedAcceptsManyChunks() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        assertTrue(assembler.addChunk(chunk("a", 0, 5, new byte[] {1})).isEmpty());
    }

    @Test
    void badBase64Ignored() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        Map<String, Object> m = chunk("a", 0, 1, new byte[] {1});
        m.put("data", "!!!not base64!!!");
        assertEquals(Optional.empty(), assembler.addChunk(m));
    }

    @Test
    void assemblesInAnyOrderAndDeduplicates() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        byte[] first = {1, 2};
        byte[] second = {3, 4, 5};

        assertTrue(assembler.addChunk(chunk("a", 1, 2, second)).isEmpty());
        Optional<byte[]> done = assembler.addChunk(chunk("a", 0, 2, first));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, done.orElseThrow());

        assertTrue(assembler.addChunk(chunk("a", 0, 2, first)).isEmpty());
        assertTrue(assembler.addChunk(chunk("a", 0, 2, first)).isEmpty());
    }

    @Test
    void duplicateIndexDoesNotDoubleCount() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        assertTrue(assembler.addChunk(chunk("a", 0, 2, new byte[] {1})).isEmpty());
        assertTrue(assembler.addChunk(chunk("a", 0, 2, new byte[] {1})).isEmpty());
        assertArrayEquals(new byte[] {1, 2}, assembler.addChunk(chunk("a", 1, 2, new byte[] {2})).orElseThrow());
    }

    @Test
    void twoAssembliesDoNotInterfere() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        assertTrue(assembler.addChunk(chunk("x", 0, 2, new byte[] {1})).isEmpty());
        assertTrue(assembler.addChunk(chunk("y", 0, 2, new byte[] {9})).isEmpty());
        Optional<byte[]> x = assembler.addChunk(chunk("x", 1, 2, new byte[] {2}));
        Optional<byte[]> y = assembler.addChunk(chunk("y", 1, 2, new byte[] {8}));
        assertArrayEquals(new byte[] {1, 2}, x.orElseThrow());
        assertArrayEquals(new byte[] {9, 8}, y.orElseThrow());
    }

    @Test
    void clearDropsPendingAssemblies() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        assertTrue(assembler.addChunk(chunk("a", 0, 2, new byte[] {1})).isEmpty());
        assembler.clear();
        assertTrue(assembler.addChunk(chunk("a", 1, 2, new byte[] {2})).isEmpty());
    }

    @Test
    void singleChunkCompletesImmediately() {
        RpcChunkAssembler assembler = new RpcChunkAssembler();
        assertArrayEquals(new byte[] {7, 8}, assembler.addChunk(chunk("solo", 0, 1, new byte[] {7, 8})).orElseThrow());
    }

    @Test
    void assemblerAcceptsAnyCountForDifferentIds() {
        RpcChunkAssembler assembler = new RpcChunkAssembler(-1);
        assertTrue(assembler.addChunk(chunk("b", 0, 9, new byte[] {1})).isEmpty());
    }
}