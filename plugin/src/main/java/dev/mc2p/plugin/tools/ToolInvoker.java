package dev.mc2p.plugin.tools;

import dev.mc2p.common.audit.AuditLogger;
import dev.mc2p.common.exceptions.ToolException;
import dev.mc2p.common.json.Json;
import dev.mc2p.common.rpc.AuthContext;
import dev.mc2p.common.rpc.ToolResult;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.Map;

/**
 * Enforces the tool-level authorization policy for every invocation, regardless of
 * transport (HTTP bearer or RPC envelope): the caller's effective restrictions must allow
 * the tool, confirm flag for destructive tools, and fail-closed audit before destructive
 * execution.
 */
public final class ToolInvoker {

    private final ToolRegistry registry;
    private final AuditLogger audit;
    private final String serverId;

    public ToolInvoker(final ToolRegistry registry, final AuditLogger audit, final String serverId) {
        this.registry = registry;
        this.audit = audit;
        this.serverId = serverId;
    }

    public CallToolResult invoke(final String toolName, final Map<String, Object> args, final AuthContext auth) {
        final ToolSpec spec = registry.get(toolName);
        if (spec == null) {
            return ToolResult.error("unknown tool: " + toolName);
        }
        if (auth == null || auth.restrictions() == null) {
            return ToolResult.error("unauthenticated");
        }
        if (!auth.restrictions().isToolAllowed(toolName)) {
            return ToolResult.error("tool '" + toolName + "' is not allowed for this token");
        }
        if (spec.requiresConfirm() && !Boolean.TRUE.equals(args == null ? null : args.get("confirm"))) {
            return ToolResult.error("tool '" + toolName + "' is destructive and requires confirm: true");
        }
        if (spec.destructive()) {
            // Fail closed: the action must not run if it cannot be recorded.
            try {
                audit.log(
                        auth.name(),
                        auth.tokenId(),
                        serverId,
                        toolName,
                        "execute",
                        Json.toJson(redactSecrets(spec.name(), args == null ? Map.of() : args)));
            } catch (final RuntimeException e) {
                return ToolResult.error("audit write failed; action refused: " + e.getMessage());
            }
        }
        try {
            final Object result = spec.handler().invoke(args == null ? Map.of() : args, auth);
            return ToolResult.success(result == null ? Map.of() : result);
        } catch (final ToolException e) {
            return ToolResult.error(e.getMessage());
        } catch (final RuntimeException e) {
            return ToolResult.error("internal error: " + safeMessage(e));
        }
    }

    private static String safeMessage(final Throwable t) {
        final String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }

    private static Map<String, Object> redactSecrets(final String tool, final Map<String, Object> args) {
        final Map<String, Object> copy = new java.util.LinkedHashMap<>(args);
        // Command arguments may carry tokens in plaintext; only record the first token.
        if (copy.containsKey("command") && copy.get("command") instanceof final String cmd) {
            final int end = cmd.indexOf(' ');
            copy.put("command", end < 0 ? cmd : cmd.substring(0, end) + " …");
        }
        return copy;
    }
}
