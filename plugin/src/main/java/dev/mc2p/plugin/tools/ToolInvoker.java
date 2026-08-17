package dev.mc2p.plugin.tools;

import java.util.Map;

import dev.mc2p.common.audit.AuditLogger;
import dev.mc2p.common.json.Json;
import dev.mc2p.common.role.Role;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Enforces the tool-level authorization policy for every invocation, regardless of
 * transport (HTTP bearer or RPC envelope): role >= required, confirm flag for destructive
 * tools, and fail-closed audit before destructive execution.
 */
public final class ToolInvoker {

    private final ToolRegistry registry;
    private final AuditLogger audit;
    private final String serverId;

    public ToolInvoker(ToolRegistry registry, AuditLogger audit, String serverId) {
        this.registry = registry;
        this.audit = audit;
        this.serverId = serverId;
    }

    public CallToolResult invoke(String toolName, Map<String, Object> args, AuthContext auth) {
        ToolSpec spec = registry.get(toolName);
        if (spec == null) {
            return ToolResult.error("unknown tool: " + toolName);
        }
        Role role = auth.role();
        if (role == null) {
            return ToolResult.error("unauthenticated");
        }
        if (!role.can(spec.requiredRole())) {
            return ToolResult.error("tool '" + toolName + "' requires role " + spec.requiredRole() + " (client: "
                    + role + ")");
        }
        if (spec.requiresConfirm() && !Boolean.TRUE.equals(args == null ? null : args.get("confirm"))) {
            return ToolResult.error("tool '" + toolName + "' is destructive and requires confirm: true");
        }
        if (spec.destructive()) {
            // Fail closed: the action must not run if it cannot be recorded.
            try {
                audit.log(role, auth.tokenId(), serverId, toolName, "execute",
                        Json.toJson(redactSecrets(spec.name(), args == null ? Map.of() : args)));
            } catch (RuntimeException e) {
                return ToolResult.error("audit write failed; action refused: " + e.getMessage());
            }
        }
        try {
            Object result = spec.handler().invoke(args == null ? Map.of() : args, auth);
            return ToolResult.success(result == null ? Map.of() : result);
        } catch (ToolException e) {
            return ToolResult.error(e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error("internal error: " + safeMessage(e));
        }
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }

    private static Map<String, Object> redactSecrets(String tool, Map<String, Object> args) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>(args);
        // Command arguments may carry tokens in plaintext; only record the first token.
        if (copy.containsKey("command") && copy.get("command") instanceof String cmd) {
            int end = cmd.indexOf(' ');
            copy.put("command", end < 0 ? cmd : cmd.substring(0, end) + " …");
        }
        return copy;
    }
}