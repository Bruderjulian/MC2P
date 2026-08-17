package dev.mc2p.plugin.http;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the identity attributes stamped by {@link AuthFilter} into the MCP transport
 * context that tool handlers read from {@code exchange.transportContext()}.
 */
public final class McpRequestContextExtractor implements McpTransportContextExtractor<HttpServletRequest> {

    public static final String KEY_ROLE = "mc2p.role";
    public static final String KEY_TOKEN_ID = "mc2p.tokenId";
    public static final String KEY_REMOTE_IP = "mc2p.remoteIp";
    public static final String KEY_CLIENT_NAME = "mc2p.clientName";

    @Override
    public McpTransportContext extract(HttpServletRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        Object role = request.getAttribute(AuthFilter.ATTR_ROLE);
        Object tokenId = request.getAttribute(AuthFilter.ATTR_TOKEN_ID);
        Object remoteIp = request.getAttribute(AuthFilter.ATTR_REMOTE_IP);
        Object clientName = request.getAttribute(AuthFilter.ATTR_CLIENT_NAME);
        if (role != null) {
            metadata.put(KEY_ROLE, role);
        }
        if (tokenId != null) {
            metadata.put(KEY_TOKEN_ID, tokenId);
        }
        if (remoteIp != null) {
            metadata.put(KEY_REMOTE_IP, remoteIp);
        }
        if (clientName != null) {
            metadata.put(KEY_CLIENT_NAME, clientName);
        }
        return McpTransportContext.create(metadata);
    }
}
