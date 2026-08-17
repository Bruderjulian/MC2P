package dev.mc2p.proxy.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import dev.mc2p.common.json.Json;

/** Unauthenticated, minimal health endpoint shared with the MCP port. */
public final class HealthzServlet extends HttpServlet {

    private final String serverId;
    private final String version;
    private final long startedAt = System.currentTimeMillis();

    public HealthzServlet(String serverId, String version) {
        this.serverId = serverId;
        this.version = version;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(200);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(Json.toJson(Map.of(
                "serverId", serverId,
                "plugin", version,
                "role", "proxy",
                "uptimeSeconds", (System.currentTimeMillis() - startedAt) / 1000)));
    }
}