package dev.mc2p.common.rpc;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code mc2p:rpc} plugin-messaging wire format, JSON encoded. Messages are
 * small
 * envelopes; responses larger than a threshold are split into base64 chunks
 * that the
 * proxy reassembles by id.
 *
 * <pre>
 *   hello      {"t":"hello","secret":"..."}
 *   hello-ok   {"t":"hello-ok","serverId":"..."}
 *   hello-no   {"t":"hello-no","error":"..."}
 *   req        {"t":"req","id":"...","method":"...","client":"...","tokenId":"...","restrictions":{...},"params":{...}}
 *   resp       {"t":"resp","id":"...","ok":true,"result":{...} | "error":"..."}
 *   chunk      {"t":"chunk","id":"...","idx":0,"count":3,"data":"<base64>"}
 *   event      {"t":"event","event":"...","params":{...}}
 * </pre>
 */
public final class RpcMessage {

    /**
     * Responses at or below this serialized size travel as a single {@code resp}.
     */
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;

    private RpcMessage() {
    }

    public static Map<String, Object> hello(final String secret) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "hello");
        m.put("secret", secret);
        return m;
    }

    public static Map<String, Object> helloOk(final String serverId) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "hello-ok");
        m.put("serverId", serverId);
        return m;
    }

    public static Map<String, Object> helloNo(final String error) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "hello-no");
        m.put("error", error);
        return m;
    }

    public static Map<String, Object> request(
            final String id,
            final String method,
            final String client,
            final String tokenId,
            final Map<String, Object> restrictions,
            final Map<String, Object> params) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "req");
        m.put("id", id);
        m.put("method", method);
        m.put("client", client == null ? "" : client);
        m.put("tokenId", tokenId == null ? "" : tokenId);
        m.put("restrictions", restrictions == null ? Map.of() : restrictions);
        m.put("params", params == null ? Map.of() : params);
        return m;
    }

    public static Map<String, Object> response(final String id, final boolean ok, final Object result,
            final String error) {
        final Map<String, Object> m = new LinkedHashMap<>();
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

    public static Map<String, Object> event(final String event, final Map<String, Object> params) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "event");
        m.put("event", event);
        m.put("params", params == null ? Map.of() : params);
        return m;
    }

    /**
     * Encodes a response. If the serialized response fits the single-message limit
     * the
     * typed {@code resp} is returned; otherwise the serialized bytes are split into
     * {@code chunk} messages that the receiver reassembles and parses as a
     * {@code resp}.
     *
     * @param serializedResponse the {@code resp} message serialized to JSON bytes
     */
    public static List<Map<String, Object>> encodeResponse(final String id, final byte[] serializedResponse) {
        if (serializedResponse.length <= MAX_PAYLOAD_BYTES) {
            return List.of(responseFromJson(serializedResponse));
        }
        final List<Map<String, Object>> chunks = new ArrayList<>();
        final int chunkCount = (serializedResponse.length + MAX_PAYLOAD_BYTES - 1) / MAX_PAYLOAD_BYTES;
        for (int i = 0; i < chunkCount; i++) {
            final int from = i * MAX_PAYLOAD_BYTES;
            final int to = Math.min(serializedResponse.length, from + MAX_PAYLOAD_BYTES);
            final byte[] part = new byte[to - from];
            System.arraycopy(serializedResponse, from, part, 0, part.length);
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", "chunk");
            m.put("id", id);
            m.put("idx", i);
            m.put("count", chunkCount);
            m.put("data", Base64.getEncoder().encodeToString(part));
            chunks.add(m);
        }
        return chunks;
    }

    /**
     * Minimal JSON parse of a {@code resp} message back into a map (single-message
     * path).
     */
    public static Map<String, Object> responseFromJson(final byte[] json) {
        return dev.mc2p.common.json.Json.parse(json);
    }

    public static String type(final Map<String, Object> message) {
        final Object v = message.get("t");
        return v == null ? null : String.valueOf(v);
    }

    public static String id(final Map<String, Object> message) {
        final Object v = message.get("id");
        return v == null ? null : String.valueOf(v);
    }
}
