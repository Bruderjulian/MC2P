package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.json.Json;
import dev.mc2p.common.rpc.RpcChunkAssembler;
import dev.mc2p.common.rpc.RpcMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RpcMessageTest {

    @Test
    void smallResponseSingleMessage() {
        Map<String, Object> response = RpcMessage.response("id-1", true, Map.of("ok", 1), null);
        byte[] json = Json.toJsonBytes(response);
        List<Map<String, Object>> encoded = RpcMessage.encodeResponse("id-1", json);
        assertEquals(1, encoded.size());
        assertEquals("resp", RpcMessage.type(encoded.get(0)));
        assertEquals("id-1", RpcMessage.id(encoded.get(0)));
    }

    @Test
    void largeResponseChunkedRoundTrip() {
        Map<String, Object> big = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            rows.add(Map.of("uuid", "player-" + i, "name", "p" + i, "x", i, "y", i * 2, "z", i * 3));
        }
        big.put("players", rows);
        Map<String, Object> response = RpcMessage.response("id-big", true, big, null);
        byte[] json = Json.toJsonBytes(response);
        assertTrue(json.length > RpcMessage.MAX_PAYLOAD_BYTES);

        List<Map<String, Object>> encoded = RpcMessage.encodeResponse("id-big", json);
        assertTrue(encoded.size() > 1);

        RpcChunkAssembler assembler = new RpcChunkAssembler();
        byte[] reassembled = null;
        for (Map<String, Object> message : encoded) {
            if ("chunk".equals(RpcMessage.type(message))) {
                Optional<byte[]> done = assembler.addChunk(message);
                if (done.isPresent()) {
                    reassembled = done.get();
                }
            }
        }
        assertArrayEquals(json, reassembled);
    }

    @Test
    void requestEnvelopeFields() {
        Map<String, Object> request = RpcMessage.request("r1", "player_info", "reader", Map.of("uuid", "abc"));
        assertEquals("req", RpcMessage.type(request));
        assertEquals("r1", RpcMessage.id(request));
        assertEquals("player_info", String.valueOf(request.get("method")));
        assertEquals("reader", String.valueOf(request.get("role")));
    }

    @Test
    void helloNoCarriesError() {
        Map<String, Object> denied = RpcMessage.helloNo("bad secret");
        assertEquals("hello-no", RpcMessage.type(denied));
        assertEquals("bad secret", String.valueOf(denied.get("error")));
        assertNull(RpcMessage.type(Map.of()));
    }
}
