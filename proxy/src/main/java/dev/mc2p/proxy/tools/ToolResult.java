package dev.mc2p.proxy.tools;

import dev.mc2p.common.json.Json;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;

/** Helpers for building MCP CallToolResult values (proxy side). */
public final class ToolResult {

    private ToolResult() {}

    public static CallToolResult success(Object result) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder(Json.toJson(result)).build()))
                .build();
    }

    public static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder("{\"error\":\"" + escape(message) + "\"}")
                        .build()))
                .isError(true)
                .build();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
