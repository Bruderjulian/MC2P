package dev.mc2p.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void toJsonSerializesMaps() {
        assertEquals("{\"a\":1}", Json.toJson(Map.of("a", 1)));
    }

    @Test
    void toJsonBytesRoundTrips() {
        byte[] bytes = Json.toJsonBytes(Map.of("k", "v"));
        assertEquals("{\"k\":\"v\"}", new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void parseNullAndBlankReturnEmpty() {
        assertTrue(Json.parse((String) null).isEmpty());
        assertTrue(Json.parse("   ").isEmpty());
    }

    @Test
    void parseValidJson() {
        Map<String, Object> parsed = Json.parse("{\"a\":1,\"b\":\"x\"}");
        assertEquals(1, parsed.get("a"));
        assertEquals("x", parsed.get("b"));
    }

    @Test
    void parseBytesDelegates() {
        Map<String, Object> parsed = Json.parse("{\"n\":42}".getBytes(StandardCharsets.UTF_8));
        assertEquals(42, parsed.get("n"));
    }

    @Test
    void parseInvalidJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{not json"));
    }

    @Test
    void toJsonOfUnserializableThrows() {
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);
        assertThrows(IllegalArgumentException.class, () -> Json.toJson(cyclic));
        assertThrows(IllegalArgumentException.class, () -> Json.toJsonBytes(cyclic));
    }
}
