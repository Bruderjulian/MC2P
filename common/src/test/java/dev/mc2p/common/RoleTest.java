package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.role.Role;
import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void orderingIsCumulative() {
        assertTrue(Role.READER.can(Role.READER));
        assertFalse(Role.READER.can(Role.OPS));
        assertTrue(Role.OPS.can(Role.READER));
        assertTrue(Role.OPS.can(Role.OPS));
        assertFalse(Role.OPS.can(Role.ADMIN));
        assertTrue(Role.ADMIN.can(Role.READER));
        assertTrue(Role.ADMIN.can(Role.OPS));
        assertTrue(Role.ADMIN.can(Role.ADMIN));
    }

    @Test
    void nullRequiredAlwaysSatisfied() {
        assertTrue(Role.READER.can(null));
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(Role.READER, Role.fromString("reader"));
        assertEquals(Role.OPS, Role.fromString("OPS"));
        assertEquals(Role.ADMIN, Role.fromString("Admin"));
        assertNull(Role.fromString("superuser"));
        assertNull(Role.fromString(null));
    }

    @Test
    void toStringIsLowercase() {
        assertEquals("reader", Role.READER.toString());
        assertEquals("admin", Role.ADMIN.toString());
    }
}
