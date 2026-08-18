package dev.mc2p.plugin.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of the tools this backend exposes; shared by the MCP server and the RPC path. */
public final class ToolRegistry {

    private final Map<String, ToolSpec> tools = new LinkedHashMap<>();

    public void register(final ToolSpec spec) {
        tools.put(spec.name(), spec);
    }

    public ToolSpec get(final String name) {
        return tools.get(name);
    }

    public boolean contains(final String name) {
        return tools.containsKey(name);
    }

    public List<ToolSpec> all() {
        return new ArrayList<>(tools.values());
    }

    public int size() {
        return tools.size();
    }
}
