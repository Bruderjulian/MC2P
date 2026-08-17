package dev.mc2p.common.rpc;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code mc2p:rpc} plugin-messaging wire format, JSON encoded. Messages are small
 * envelopes; responses larger than a threshold are split into base64 chunks that the
 * proxy reassembles by id.
 *
 * <pre>
 *   hello      {"t":"hello","secret":"..."}
 *   hello-ok   {"t":"hello-ok","serverId":"..."}
 *   hello-no   {"t":"hello-no","error":"..."}
 *   req        {"t":"req","id":"...","method":"...","role":"...","params":{...}}
 *   resp       {"t":"resp","id":"...","ok":true,"result":{...} | "error":"..."}
 *   chunk      {"t":"chunk","id":"...","idx":0,"count":3,"data":"<base64>"}
 *   event      {"t":"event","event":"...","params":{...}}
 * </pre>
 */
public final class RpcMessage {

    /** Responses at or below this serialized size travel as a single {@code resp}. */
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;

    private RpcMessage() {
    }

    public static Map<String, Object> hello(String secret) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "hello");
        m.put("secret", secret);
        return m;
    }

    public static Map<String, Object> helloOk(String serverId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "hello-ok");
        m.put("serverId", serverId);
        return m;
    }

    public static Map<String, Object> helloNo(String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "hello-no");
        m.put("error", error);
        return m;
    }

    public static Map<String, Object> request(String id, String method, String role, Map<String, Object> params) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "req");
        m.put("id", id);
        m.put("method", method);
        m.put("role", role);
        m.put("params", params == null ? Map.of() : params);
        return m;
    }

    public static Map<String, Object> response(String id, boolean ok, Object result, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "resp");
        m.put("id", id);
        m.put("ok", ok);
        if (ok) {
            m.put("result", result == null ? Map.of() : result);
        } else {
            m.put("error", error == null ? "unknown error" : error);
        }
        return m;
    }

    public static Map<String, Object> event(String event, Map<String, Object> params) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "event");
        m.put("event", event);
        m.put("params", params == null ? Map.of() : params);
        return m;
    }

    /**
     * Encodes a response. If the serialized response fits the single-message limit the
     * typed {@code resp} is returned; otherwise the serialized bytes are split into
     * {@code chunk} messages that the receiver reassembles and parses as a {@code resp}.
     *
     * @param serializedResponse the {@code resp} message serialized to JSON bytes
     */
    public static List<Map<String, Object>> encodeResponse(String id, byte[] serializedResponse) {
        if (serializedResponse.length <= MAX_PAYLOAD_BYTES) {
            return List.of(responseFromJson(serializedResponse));
        }
        List<Map<String, Object>> chunks = new ArrayList<>();
        int chunkCount = (serializedResponse.length + MAX_PAYLOAD_BYTES - 1) / MAX_PAYLOAD_BYTES;
        for (int i = 0; i < chunkCount; i++) {
            int from = i * MAX_PAYLOAD_BYTES;
            int to = Math.min(serializedResponse.length, from + MAX_PAYLOAD_BYTES);
            byte[] part = new byte[to - from];
            System.arraycopy(serializedResponse, from, part, 0, part.length);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", "chunk");
            m.put("id", id);
            m.put("idx", i);
            m.put("count", chunkCount);
            m.put("data", Base64.getEncoder().encodeToString(part));
            chunks.add(m);
        }
        return chunks;
    }

    /** Minimal JSON parse of a {@code resp} message back into a map (single-message path). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> responseFromJson(byte[] json) {
        return dev.mc2p.common.json.Json.parse(json);
    }

    public static String type(Map<String, Object> message) {
        Object v = message.get("t");
        return v == null ? null : String.valueOf(v);
    }

    public static String id(Map<String, Object> message) {
        Object v = message.get("id");
        return v == null ? null : String.valueOf(v);
    }
}