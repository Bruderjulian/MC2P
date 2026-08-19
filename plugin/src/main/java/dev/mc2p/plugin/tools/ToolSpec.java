package dev.mc2p.plugin.tools;

import java.util.Map;

import dev.mc2p.common.exceptions.ToolException;

/**
 * Registration metadata for one MCP tool. {@code destructive} tools are audited and fail
 * closed if the audit entry cannot be written; {@code requiresConfirm} tools additionally
 * require {@code confirm: true} in the arguments. Access is governed by the caller's
 * {@link dev.mc2p.common.config.RestrictionsConfig}, not a role.
 */
public record ToolSpec(
        String name,
        boolean destructive,
        boolean requiresConfirm,
        String description,
        Map<String, Object> inputSchema,
        ToolHandler handler) {

    public interface ToolHandler {
        Object invoke(Map<String, Object> args, AuthContext auth) throws ToolException;
    }
}
