package dev.mc2p.plugin.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private static ToolSpec spec(String name, boolean destructive) {
        return new ToolSpec(name, destructive, false, "desc", Map.of(), (args, auth) -> null);
    }

    @Test
    void registerGetContainsAndSize() {
        ToolRegistry registry = new ToolRegistry();
        ToolSpec admin = spec("admin-cmd", true);
        ToolSpec reader = spec("read", false);
        registry.register(admin);
        registry.register(reader);
        assertEquals(2, registry.size());
        assertTrue(registry.contains("read"));
        assertFalse(registry.contains("missing"));
        assertSame(reader, registry.get("read"));
        assertNull(registry.get("missing"));
        assertEquals(List.of(admin, reader), registry.all());
    }

    @Test
    void registerOverridesSameName() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("dup", false));
        registry.register(spec("dup", true));
        assertEquals(1, registry.size());
        assertTrue(registry.get("dup").destructive());
    }
}
