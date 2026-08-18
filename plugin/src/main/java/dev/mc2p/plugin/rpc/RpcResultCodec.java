package dev.mc2p.plugin.rpc;

import dev.mc2p.common.json.Json;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;

/**
 * Converts MCP CallToolResult values to the {@code mc2p:rpc} response wire
 * format.
 */
public final class RpcResultCodec {

    private RpcResultCodec() {
    }

    /**
     * @return {ok, result} or {ok=false, error}
     */
    public static Map<String, Object> encode(final CallToolResult result) {
        final String text = firstText(result.content());
        if (Boolean.TRUE.equals(result.isError())) {
            final Map<String, Object> errorJson = safeParse(text);
            final Object message = errorJson == null ? null : errorJson.get("error");
            return Map.of(
                    "ok",
                    false,
                    "error",
                    message == null ? (text == null ? "tool error" : text) : String.valueOf(message));
        }
        final Map<String, Object> parsed = safeParse(text);
        return Map.of("ok", true, "result", parsed == null ? Map.of("text", text == null ? "" : text) : parsed);
    }

    private static String firstText(final List<McpSchema.Content> content) {
        if (content == null) {
            return null;
        }
        for (final McpSchema.Content c : content) {
            if (c instanceof final McpSchema.TextContent t) {
                return t.text();
            }
        }
        return null;
    }

    private static Map<String, Object> safeParse(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Json.parse(text);
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
