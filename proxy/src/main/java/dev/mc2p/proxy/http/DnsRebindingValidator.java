package dev.mc2p.proxy.http;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;

/**
 * DNS-rebinding / CSRF protection: non-browser MCP clients send no {@code Origin} header
 * and are allowed; browser-initiated requests (the DNS-rebinding attack vector) must have
 * an {@code Origin} that matches the request {@code Host} (same-origin).
 */
public final class DnsRebindingValidator implements ServerTransportSecurityValidator {

    private static final String ORIGIN = "Origin";
    private static final String HOST = "Host";

    @Override
    public void validateHeaders(Map<String, List<String>> headers) throws ServerTransportSecurityException {
        String origin = first(headers, ORIGIN);
        if (origin == null || origin.isBlank()) {
            return;
        }
        String host = first(headers, HOST);
        if (host == null || host.isBlank()) {
            throw new ServerTransportSecurityException(421, "Invalid Host header");
        }
        try {
            java.net.URI uri = new java.net.URI(origin);
            String originHost = uri.getHost();
            if (originHost == null) {
                throw new ServerTransportSecurityException(403, "Invalid Origin header");
            }
            String hostName = host.split(":")[0];
            if (!originHost.equalsIgnoreCase(hostName)) {
                throw new ServerTransportSecurityException(403, "Cross-origin request rejected");
            }
        } catch (java.net.URISyntaxException e) {
            throw new ServerTransportSecurityException(403, "Invalid Origin header");
        }
    }

    private static String first(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }
}