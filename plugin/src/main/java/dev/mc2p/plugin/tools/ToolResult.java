package dev.mc2p.plugin.tools;

import dev.mc2p.common.json.Json;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;

/** Helpers for building MCP CallToolResult values. */
public final class ToolResult {

    private ToolResult() {}

    public static CallToolResult success(final Object result) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder(Json.toJson(result)).build()))
                .build();
    }

    public static CallToolResult error(final String message) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder("{\"error\":\"" + escape(message) + "\"}")
                        .build()))
                .isError(true)
                .build();
    }

    private static String escape(final String s) {
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
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
