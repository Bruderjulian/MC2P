package dev.mc2p.plugin.tools;

import dev.mc2p.common.role.Role;
import java.util.Map;

/**
 * Registration metadata for one MCP tool. {@code destructive} tools are audited and fail
 * closed if the audit entry cannot be written; {@code requiresConfirm} tools additionally
 * require {@code confirm: true} in the arguments.
 */
public record ToolSpec(
        String name,
        Role requiredRole,
        boolean destructive,
        boolean requiresConfirm,
        String description,
        Map<String, Object> inputSchema,
        ToolHandler handler) {

    public interface ToolHandler {
        Object invoke(Map<String, Object> args, AuthContext auth) throws ToolException;
    }
}
