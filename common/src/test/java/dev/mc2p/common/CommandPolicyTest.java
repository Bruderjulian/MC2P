package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.validate.CommandPolicy;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandPolicyTest {

    private static final CommandPolicy POLICY = new CommandPolicy(
            List.of("gamemode", "tp", "teleport", "time*"), List.of("*"), List.of("stop", "restart", "op"), 512);

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

    @Test
    void nullRoleUsesOpsAllowlist() {
        CommandPolicy policy = new CommandPolicy(List.of("gamemode"), List.of("*"), List.of(), 512);
        assertNull(policy.rejectionReason("gamemode creative", null));
        assertNotNull(policy.rejectionReason("give steve diamond", null));
    }

    @Test
    void nullRoleWithEmptyAllowlistReportsRole() {
        CommandPolicy strict = new CommandPolicy(List.of(), List.of("tp"), List.of(), 512);
        assertNotNull(strict.rejectionReason("gamemode", null));
    }

    @Test
    void normalizationFiltersBlankAndUppercases() {
        CommandPolicy policy =
                new CommandPolicy(Arrays.asList(null, "  ", "  GAMEMODE  ", "TP"), List.of(), List.of(" Stop "), 512);
        assertNull(policy.rejectionReason("gamemode creative", Role.OPS));
        assertNull(policy.rejectionReason("tp", Role.OPS));
        assertNotNull(policy.rejectionReason("stop", Role.OPS));
    }

    @Test
    void denyPrefixEntryAppliesToAdmin() {
        CommandPolicy policy = new CommandPolicy(List.of("*"), List.of("*"), List.of("restart*", "kill*"), 512);
        assertNotNull(policy.rejectionReason("restart-all", Role.ADMIN));
        assertNotNull(policy.rejectionReason("killall", Role.ADMIN));
        assertNull(policy.rejectionReason("kick steve", Role.ADMIN));
    }

    @Test
    void wildcardEntryMatchesAnyToken() {
        CommandPolicy policy = new CommandPolicy(List.of("*"), List.of(), List.of(), 512);
        assertNull(policy.rejectionReason("anything at all", Role.OPS));
    }

    @Test
    void exactMatchStillAllowedWhenPrefixExists() {
        CommandPolicy policy = new CommandPolicy(List.of("time", "time*"), List.of(), List.of(), 512);
        assertNull(policy.rejectionReason("time", Role.OPS));
        assertNull(policy.rejectionReason("timeout", Role.OPS));
    }

    @Test
    void nullListsNormalizeToEmpty() {
        CommandPolicy policy = new CommandPolicy(null, null, null, 512);
        assertNotNull(policy.rejectionReason("anything", Role.ADMIN));
        assertNotNull(policy.rejectionReason("anything", Role.OPS));
    }

    @Test
    void nullCommandRejected() {
        assertNotNull(POLICY.rejectionReason(null, Role.ADMIN));
        assertNotNull(POLICY.rejectionReason(null, Role.OPS));
    }
}
