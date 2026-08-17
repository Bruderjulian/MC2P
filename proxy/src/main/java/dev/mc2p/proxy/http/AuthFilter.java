package dev.mc2p.proxy.http;

import dev.mc2p.common.net.Cidr;
import dev.mc2p.common.ratelimit.TokenBucketRateLimiter;
import dev.mc2p.common.tokens.TokenManager;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport enforcement point for the MCP endpoint: IP allowlist, rate limiting, body-size
 * cap, and Bearer-token authentication (token → role). Runs off the main thread.
 *
 * <p>
 * On success it stamps the request with the resolved identity attributes, which the
 * {@link McpRequestContextExtractor} turns into the MCP transport context that tool
 * handlers read. This is only the first layer — relay tool handlers re-check roles and
 * the backends re-check every call regardless of transport.
 */
public final class AuthFilter implements Filter {

    public static final String ATTR_ROLE = "mc2p.role";
    public static final String ATTR_TOKEN_ID = "mc2p.tokenId";
    public static final String ATTR_REMOTE_IP = "mc2p.remoteIp";

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private final TokenManager tokens;
    private final List<Cidr> ipAllowlist;
    private final TokenBucketRateLimiter rateLimiter;
    private final int bodyLimitBytes;

    public AuthFilter(
            TokenManager tokens, List<String> ipAllowlist, TokenBucketRateLimiter rateLimiter, int bodyLimitBytes) {
        this.tokens = tokens;
        this.ipAllowlist = Cidr.parseAll(ipAllowlist);
        this.rateLimiter = rateLimiter;
        this.bodyLimitBytes = bodyLimitBytes;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String remoteIp = request.getRemoteAddr();
        InetAddress address;
        try {
            address = InetAddress.getByName(remoteIp);
        } catch (IOException e) {
            address = null;
        }
        if (!ipAllowlist.isEmpty() && (address == null || !Cidr.anyMatch(ipAllowlist, address))) {
            log.info("mcp request from {} rejected by IP allowlist", remoteIp);
            reject(response, 403, "{\"error\":\"forbidden\"}");
            return;
        }
        if (!rateLimiter.tryAcquire(remoteIp)) {
            log.warn("mcp request from {} rate-limited", remoteIp);
            reject(response, 429, "{\"error\":\"too many requests\"}");
            return;
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength > bodyLimitBytes) {
            reject(response, 413, "{\"error\":\"payload too large\"}");
            return;
        }
        if (contentLength < 0) {
            request = new LimitedHttpServletRequest(request, bodyLimitBytes);
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(response, 401, "{\"error\":\"unauthorized\"}");
            return;
        }
        String presented = auth.substring(7).trim();
        TokenManager.AuthResult result = tokens.authenticate(presented);
        if (result == null) {
            reject(response, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        request.setAttribute(ATTR_ROLE, result.role());
        request.setAttribute(ATTR_TOKEN_ID, result.tokenId());
        request.setAttribute(ATTR_REMOTE_IP, remoteIp);

        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
