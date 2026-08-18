package dev.mc2p.plugin.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.role.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private static ToolSpec spec(String name, Role role) {
        return new ToolSpec(name, role, false, false, "desc", Map.of(), (args, auth) -> null);
    }

    @Test
    void registerGetContainsAndSize() {
        ToolRegistry registry = new ToolRegistry();
        ToolSpec admin = spec("admin-cmd", Role.ADMIN);
        ToolSpec reader = spec("read", Role.READER);
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
    void visibleToFiltersByRole() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(spec("read", Role.READER));
        registry.register(spec("op-cmd", Role.OPS));
        registry.register(spec("admin-cmd", Role.ADMIN));
        assertEquals(3, registry.visibleTo(Role.ADMIN).size());
        assertEquals(List.of("read", "op-cmd"), registry.visibleTo(Role.OPS).stream()
                .map(ToolSpec::name)
                .toList());
        assertEquals(List.of("read"), registry.visibleTo(Role.READER).stream()
                .map(ToolSpec::name)
                .toList());
        assertTrue(registry.visibleTo(null).isEmpty());
    }
}