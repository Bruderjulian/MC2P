package dev.mc2p.proxy.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.json.Json;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResultTest {

    @Test
    void successSerializesResult() {
        CallToolResult result = ToolResult.success(Map.of("a", 1));
        assertFalse(Boolean.TRUE.equals(result.isError()));
        TextContent content = (TextContent) result.content().get(0);
        assertEquals(Map.of("a", 1), Json.parse(content.text()));
    }

    @Test
    void errorIsMarkedAndEscapesSpecialCharacters() {
        CallToolResult result = ToolResult.error("bad \"quote\" \\ \n\r\t\u0001 ok");
        assertTrue(Boolean.TRUE.equals(result.isError()));
        TextContent content = (TextContent) result.content().get(0);
        Map<String, Object> parsed = Json.parse(content.text());
        assertEquals("bad \"quote\" \\ \n\r\t\u0001 ok", parsed.get("error"));
    }

    @Test
    void errorEscapesQuoteOnly() {
        CallToolResult result = ToolResult.error("say \"hi\"");
        TextContent content = (TextContent) result.content().get(0);
        assertEquals(Map.of("error", "say \"hi\""), Json.parse(content.text()));
    }
}
