package dev.mc2p.plugin.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemasTest {

    @Test
    void objectWithRequired() {
        Map<String, Object> schema = Schemas.object(Map.of("a", Schemas.str("desc")), List.of("a"));
        assertEquals("object", schema.get("type"));
        assertEquals(Map.of("a", Map.of("type", "string", "description", "desc")), schema.get("properties"));
        assertEquals(List.of("a"), schema.get("required"));
        assertEquals(false, schema.get("additionalProperties"));
    }

    @Test
    void objectWithoutRequiredWhenEmptyOrNull() {
        assertNull(Schemas.object(Map.of(), List.of()).get("required"));
        assertNull(Schemas.object(Map.of(), null).get("required"));
    }

    @Test
    void strWithoutEnumAndNullDescription() {
        assertEquals("string", Schemas.str(null).get("type"));
        assertFalse(Schemas.str(null).containsKey("description"));
        assertFalse(Schemas.str("x", null).containsKey("enum"));
    }

    @Test
    void strWithEnum() {
        Map<String, Object> schema = Schemas.str("mode", List.of("auto", "manual"));
        assertEquals("string", schema.get("type"));
        assertEquals("mode", schema.get("description"));
        assertEquals(List.of("auto", "manual"), schema.get("enum"));
    }

    @Test
    void numericAndBoolTypes() {
        assertEquals("integer", Schemas.integer("i").get("type"));
        assertEquals("i", Schemas.integer("i").get("description"));
        assertEquals("number", Schemas.num("n").get("type"));
        assertEquals("n", Schemas.num("n").get("description"));
        assertEquals("boolean", Schemas.bool("b").get("type"));
        assertEquals("b", Schemas.bool("b").get("description"));
    }

    @Test
    void confirmSchemaIsBoolean() {
        assertEquals("boolean", Schemas.confirmSchema().get("type"));
    }
}
