package dev.mc2p.plugin.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolExceptionTest {

    @Test
    void messageIsPropagated() {
        ToolException exception = new ToolException("nope");
        assertEquals("nope", exception.getMessage());
    }

    @Test
    void mapBuildsSingleEntry() {
        assertEquals(Map.of("reason", "nope"), ToolException.map("reason", "nope"));
    }
}