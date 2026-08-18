package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        Map<String, Object> request =
                RpcMessage.request("r1", "player_info", "alice", "tid-1", Map.of(), Map.of("uuid", "abc"));
        assertEquals("req", RpcMessage.type(request));
        assertEquals("r1", RpcMessage.id(request));
        assertEquals("player_info", String.valueOf(request.get("method")));
        assertEquals("alice", String.valueOf(request.get("client")));
        assertEquals("tid-1", String.valueOf(request.get("tokenId")));
        assertEquals(Map.of(), request.get("restrictions"));
    }

    @Test
    void helloNoCarriesError() {
        Map<String, Object> denied = RpcMessage.helloNo("bad secret");
        assertEquals("hello-no", RpcMessage.type(denied));
        assertEquals("bad secret", String.valueOf(denied.get("error")));
        assertNull(RpcMessage.type(Map.of()));
    }

    @Test
    void helloCarriesSecret() {
        Map<String, Object> hello = RpcMessage.hello("my-secret");
        assertEquals("hello", RpcMessage.type(hello));
        assertEquals("my-secret", String.valueOf(hello.get("secret")));
    }

    @Test
    void helloOkCarriesServerId() {
        Map<String, Object> ok = RpcMessage.helloOk("server-01");
        assertEquals("hello-ok", RpcMessage.type(ok));
        assertEquals("server-01", String.valueOf(ok.get("serverId")));
    }

    @Test
    void requestDefaultsForNullClientAndParams() {
        Map<String, Object> request = RpcMessage.request("r2", "method", null, null, null, null);
        assertEquals("", String.valueOf(request.get("client")));
        assertEquals("", String.valueOf(request.get("tokenId")));
        assertEquals(Map.of(), request.get("params"));
    }

    @Test
    void responseOkNullResultDefaultsEmpty() {
        Map<String, Object> response = RpcMessage.response("r3", true, null, null);
        assertEquals("resp", RpcMessage.type(response));
        assertEquals(Map.of(), response.get("result"));
        assertFalse(response.containsKey("error"));
    }

    @Test
    void responseErrorNullDefaultsUnknown() {
        Map<String, Object> response = RpcMessage.response("r4", false, null, null);
        assertEquals("unknown error", String.valueOf(response.get("error")));
        Map<String, Object> explicit = RpcMessage.response("r5", false, null, "nope");
        assertEquals("nope", String.valueOf(explicit.get("error")));
    }

    @Test
    void eventCarriesNameAndParams() {
        Map<String, Object> event = RpcMessage.event("player-join", Map.of("name", "steve"));
        assertEquals("event", RpcMessage.type(event));
        assertEquals("player-join", String.valueOf(event.get("event")));
        assertEquals(Map.of("name", "steve"), event.get("params"));
        assertEquals(Map.of(), RpcMessage.event("x", null).get("params"));
    }

    @Test
    void idMissingReturnsNull() {
        assertNull(RpcMessage.id(Map.of("t", "resp")));
        assertEquals("abc", RpcMessage.id(Map.of("id", "abc")));
    }

    @Test
    void encodeResponseSingleMessagePreservesPayload() {
        Map<String, Object> response = RpcMessage.response("id-single", false, null, "boom");
        byte[] json = Json.toJsonBytes(response);
        List<Map<String, Object>> encoded = RpcMessage.encodeResponse("id-single", json);
        assertEquals(1, encoded.size());
        assertEquals("resp", RpcMessage.type(encoded.get(0)));
        assertEquals("boom", String.valueOf(encoded.get(0).get("error")));
    }
}
