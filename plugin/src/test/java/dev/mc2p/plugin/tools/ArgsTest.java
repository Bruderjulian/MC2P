package dev.mc2p.plugin.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArgsTest {

    @Test
    void stringReturnsNullWhenAbsent() {
        assertNull(Args.string(Map.of(), "key"));
        java.util.Map<String, Object> withNull = new java.util.HashMap<>();
        withNull.put("key", null);
        assertNull(Args.string(withNull, "key"));
        assertEquals("value", Args.string(Map.of("key", "value"), "key"));
        assertEquals("42", Args.string(Map.of("key", 42), "key"));
    }

    @Test
    void requiredStringTrimsAndValidates() {
        assertEquals("value", Args.requiredString(Map.of("key", "  value  "), "key"));
        assertThrows(ToolException.class, () -> Args.requiredString(Map.of(), "key"));
        assertThrows(ToolException.class, () -> Args.requiredString(Map.of("key", "   "), "key"));
    }

    @Test
    void boolParsesFormsAndDefaults() {
        assertTrue(Args.bool(Map.of("key", true), "key"));
        assertFalse(Args.bool(Map.of("key", false), "key"));
        assertTrue(Args.bool(Map.of("key", "true"), "key"));
        assertFalse(Args.bool(Map.of("key", "false"), "key"));
        assertFalse(Args.bool(Map.of("key", "garbage"), "key"));
        assertFalse(Args.bool(Map.of("key", 1), "key"));
        assertFalse(Args.bool(Map.of(), "key"));
    }

    @Test
    void integerParsesAndFallsBack() {
        assertEquals(5, Args.integer(Map.of(), "key", 5));
        assertEquals(7, Args.integer(Map.of("key", 7), "key", 5));
        assertEquals(12, Args.integer(Map.of("key", 12.9), "key", 5));
        assertEquals(12, Args.integer(Map.of("key", " 12 "), "key", 5));
        assertThrows(ToolException.class, () -> Args.integer(Map.of("key", "abc"), "key", 5));
        assertThrows(ToolException.class, () -> Args.integer(Map.of("key", true), "key", 5));
    }

    @Test
    void uuidValidates() {
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, Args.uuid(Map.of("key", uuid.toString()), "key"));
        assertThrows(ToolException.class, () -> Args.uuid(Map.of(), "key"));
        assertThrows(ToolException.class, () -> Args.uuid(Map.of("key", "not-a-uuid"), "key"));
    }

    @Test
    void optionalUuidReturnsNullWhenAbsentOrBlank() {
        assertNull(Args.optionalUuid(Map.of(), "key"));
        assertNull(Args.optionalUuid(Map.of("key", "   "), "key"));
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, Args.optionalUuid(Map.of("key", uuid.toString()), "key"));
        assertThrows(ToolException.class, () -> Args.optionalUuid(Map.of("key", "bad"), "key"));
    }
}
