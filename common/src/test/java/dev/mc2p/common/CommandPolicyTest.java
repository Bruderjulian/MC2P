package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.validate.CommandPolicy;

class CommandPolicyTest {

    private static final CommandPolicy POLICY = new CommandPolicy(
            List.of("gamemode", "tp", "teleport", "time*"),
            List.of("*"),
            List.of("stop", "restart", "op"),
            512);

    @Test
    void opsAllowlistExactAndPrefix() {
        assertNull(POLICY.rejectionReason("gamemode creative", Role.OPS));
        assertNull(POLICY.rejectionReason("time set day", Role.OPS));
        assertNotNull(POLICY.rejectionReason("give Notch diamond", Role.OPS));
    }

    @Test
    void adminWildcardAllowsAllButDenyPrecedence() {
        assertNull(POLICY.rejectionReason("give Notch diamond", Role.ADMIN));
        assertNull(POLICY.rejectionReason("ban Steve", Role.ADMIN));
        assertNotNull(POLICY.rejectionReason("stop", Role.ADMIN));
        assertNotNull(POLICY.rejectionReason("op Steve", Role.ADMIN));
    }

    @Test
    void emptyAndOversizedCommandsRejected() {
        assertNotNull(POLICY.rejectionReason("", Role.ADMIN));
        assertNotNull(POLICY.rejectionReason("   ", Role.ADMIN));
        String longCommand = "say " + "x".repeat(600);
        assertNotNull(POLICY.rejectionReason(longCommand, Role.ADMIN));
    }

    @Test
    void isAllowedMirrorsRejectionReason() {
        assertTrue(POLICY.isAllowed("gamemode survival", Role.OPS));
        assertFalse(POLICY.isAllowed("op Steve", Role.OPS));
    }

    @Test
    void emptyAllowlistDeniesEverything() {
        CommandPolicy strict = new CommandPolicy(List.of(), List.of(), List.of(), 512);
        assertNotNull(strict.rejectionReason("gamemode survival", Role.OPS));
        assertNotNull(strict.rejectionReason("anything", Role.ADMIN));
    }
}