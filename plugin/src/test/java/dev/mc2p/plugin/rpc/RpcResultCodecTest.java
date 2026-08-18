package dev.mc2p.plugin.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RpcResultCodecTest {

    private static CallToolResult text(String text, boolean isError) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder(text).build()))
                .isError(isError)
                .build();
    }

    @Test
    void okWithJsonResultPassesThrough() {
        Map<String, Object> encoded = RpcResultCodec.encode(text("{\"a\":1}", false));
        assertEquals(true, encoded.get("ok"));
        assertEquals(Map.of("a", 1), encoded.get("result"));
    }

    @Test
    void okWithPlainTextWrapsInTextMap() {
        Map<String, Object> encoded = RpcResultCodec.encode(text("hello world", false));
        assertEquals(Map.of("text", "hello world"), encoded.get("result"));
    }

    @Test
    void okWithEmptyTextProducesEmptyTextMap() {
        Map<String, Object> encoded = RpcResultCodec.encode(text("", false));
        assertEquals(Map.of("text", ""), encoded.get("result"));
    }

    @Test
    void okWithNoTextContentProducesEmptyTextMap() {
        CallToolResult result = CallToolResult.builder().content(List.of()).build();
        Map<String, Object> encoded = RpcResultCodec.encode(result);
        assertEquals(true, encoded.get("ok"));
        assertEquals(Map.of("text", ""), encoded.get("result"));
    }

    @Test
    void errorExtractsParsedErrorField() {
        Map<String, Object> encoded = RpcResultCodec.encode(text("{\"error\":\"denied\"}", true));
        assertEquals(false, encoded.get("ok"));
        assertEquals("denied", encoded.get("error"));
    }

    @Test
    void errorWithPlainTextUsesTextAsMessage() {
        Map<String, Object> encoded = RpcResultCodec.encode(text("boom", true));
        assertEquals("boom", encoded.get("error"));
    }

    @Test
    void errorWithNoErrorFieldUsesRawText() {
        Map<String, Object> encoded = RpcResultCodec.encode(text("{\"x\":1}", true));
        assertEquals("{\"x\":1}", encoded.get("error"));
    }

    @Test
    void errorWithEmptyContentFallsBackToDefaultMessage() {
        CallToolResult result = CallToolResult.builder().content(List.of()).isError(true).build();
        Map<String, Object> encoded = RpcResultCodec.encode(result);
        assertEquals("tool error", encoded.get("error"));
    }
}