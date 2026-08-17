package dev.mc2p.plugin.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.mc2p.common.role.Role;

/** Registry of the tools this backend exposes; shared by the MCP server and the RPC path. */
public final class ToolRegistry {

    private final Map<String, ToolSpec> tools = new LinkedHashMap<>();

    public void register(ToolSpec spec) {
        tools.put(spec.name(), spec);
    }

    public ToolSpec get(String name) {
        return tools.get(name);
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public List<ToolSpec> all() {
        return new ArrayList<>(tools.values());
    }

    public int size() {
        return tools.size();
    }

    /** The tools visible to a role (required role satisfied). */
    public List<ToolSpec> visibleTo(Role role) {
        return tools.values().stream().filter(spec -> role != null && role.can(spec.requiredRole())).toList();
    }
}