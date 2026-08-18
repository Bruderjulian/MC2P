package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.tokens.TokenManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenManagerTest {

    @TempDir
    Path tempDir;

    private static TokenManager.ConfigToken configToken(Role role, String secret) {
        return new TokenManager.ConfigToken(role, secret);
    }

    @Test
    void authenticatesConfiguredTokensAndRejectsUnknown() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of(
                "reader", configToken(Role.READER, "reader-token"),
                "ops", configToken(Role.OPS, "ops-token"),
                "admin", configToken(Role.ADMIN, "admin-token")));

        var result = manager.authenticate("reader-token");
        assertNotNull(result);
        assertEquals("reader", result.name());
        assertEquals(Role.READER, result.role());
        assertFalse(result.tokenId().isBlank());

        assertEquals(Role.OPS, manager.authenticate("ops-token").role());
        assertEquals("admin", manager.authenticate("admin-token").name());
        assertNull(manager.authenticate("wrong-token"));
        assertNull(manager.authenticate(""));
        assertNull(manager.authenticate(null));
    }

    @Test
    void multipleTokensPerRoleAreDistinguishable() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        String alice = manager.create("alice", Role.OPS);
        String bob = manager.create("bob", Role.OPS);

        var aliceResult = manager.authenticate(alice);
        var bobResult = manager.authenticate(bob);
        assertNotNull(aliceResult);
        assertNotNull(bobResult);
        assertEquals("alice", aliceResult.name());
        assertEquals("bob", bobResult.name());
        assertEquals(Role.OPS, aliceResult.role());
        assertEquals(Role.OPS, bobResult.role());
    }

    @Test
    void createReplacesAndRevokeRestoresConfigured() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of("alice", configToken(Role.READER, "reader-token")));

        String created = manager.create("alice", Role.OPS);
        assertNotNull(created);
        var result = manager.authenticate(created);
        assertEquals("alice", result.name());
        assertEquals(Role.OPS, result.role());
        assertNull(manager.authenticate("reader-token"));

        assertTrue(manager.revoke("alice"));
        assertEquals(Role.READER, manager.authenticate("reader-token").role());
        assertNull(manager.authenticate(created));

        assertFalse(manager.revoke("missing"));
    }

    @Test
    void createdTokensSurviveRestart() {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of("ops", configToken(Role.OPS, "ops-token")));
        String created = manager.create("alice", Role.OPS);

        TokenManager reloaded = new TokenManager(file);
        reloaded.updateFromConfig(Map.of("ops", configToken(Role.OPS, "ops-token")));
        var result = reloaded.authenticate(created);
        assertNotNull(result);
        assertEquals("alice", result.name());
        assertEquals(Role.OPS, result.role());
    }

    @Test
    void legacyRoleKeyedRuntimeFileStillLoads() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        java.nio.file.Files.writeString(file, "# old format\nreader: 0011223344556677\n");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of());

        assertTrue(manager.hasRole(Role.READER));
        assertEquals("reader", manager.snapshot().get("reader").name());
    }

    @Test
    void neverStoresPlaintext() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of("admin", configToken(Role.ADMIN, "top-secret")));
        String created = manager.create("admin", Role.ADMIN);

        String persisted = java.nio.file.Files.readString(file);
        assertFalse(persisted.contains("top-secret"));
        assertFalse(persisted.contains(created));
    }

    @Test
    void snapshotReflectsConfiguredAndRuntime() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));
        manager.create("alice", Role.OPS);

        var snapshot = manager.snapshot();
        assertTrue(snapshot.containsKey("reader"));
        assertTrue(snapshot.get("reader").configured());
        assertFalse(snapshot.get("alice").configured());
        assertEquals(Role.OPS, snapshot.get("alice").role());
        assertNotNull(snapshot.get("reader").tokenId());
    }

    @Test
    void hasRoleReflectsActiveTokens() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));
        assertTrue(manager.hasRole(Role.READER));
        assertFalse(manager.hasRole(Role.ADMIN));
        manager.create("admin", Role.ADMIN);
        assertTrue(manager.hasRole(Role.ADMIN));
        manager.revoke("admin");
        assertFalse(manager.hasRole(Role.ADMIN));
    }

    @Test
    void disableAndEnableConfiguredToken() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));

        assertTrue(manager.disable("reader"));
        assertNull(manager.authenticate("reader-token"));
        assertTrue(manager.snapshot().get("reader").disabled());
        assertFalse(manager.hasRole(Role.READER));
        assertFalse(manager.disable("missing"));

        assertTrue(manager.enable("reader"));
        assertEquals(Role.READER, manager.authenticate("reader-token").role());
        assertFalse(manager.snapshot().get("reader").disabled());
        assertTrue(manager.hasRole(Role.READER));
        assertFalse(manager.enable("reader"));
    }

    @Test
    void disableAndEnableRuntimeToken() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        String token = manager.create("alice", Role.OPS);

        assertTrue(manager.disable("alice"));
        assertNull(manager.authenticate(token));
        assertFalse(manager.hasRole(Role.OPS));

        assertTrue(manager.enable("alice"));
        assertEquals("alice", manager.authenticate(token).name());
        assertTrue(manager.hasRole(Role.OPS));
    }

    @Test
    void disabledStateSurvivesRestart() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));
        manager.create("alice", Role.OPS);
        manager.disable("reader");

        TokenManager reloaded = new TokenManager(file);
        reloaded.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));
        assertNull(reloaded.authenticate("reader-token"));
        assertTrue(reloaded.snapshot().get("reader").disabled());
        assertTrue(reloaded.hasRole(Role.OPS));
        assertFalse(reloaded.snapshot().get("alice").disabled());
    }

    @Test
    void createReactivatesDisabledName() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));
        manager.disable("reader");

        String token = manager.create("reader", Role.OPS);
        assertEquals(Role.OPS, manager.authenticate(token).role());
        assertFalse(manager.snapshot().get("reader").disabled());
    }

    @Test
    void revokeOfDisabledRuntimeTokenStaysDisabled() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of("alice", configToken(Role.READER, "reader-token")));
        manager.create("alice", Role.OPS);
        manager.disable("alice");

        assertTrue(manager.revoke("alice"));
        assertNull(manager.authenticate("reader-token"));
        assertFalse(manager.hasRole(Role.READER));
    }

    @Test
    void rejectsInvalidNames() {
        assertFalse(TokenManager.isValidName(null));
        assertFalse(TokenManager.isValidName(""));
        assertFalse(TokenManager.isValidName("has space"));
        assertFalse(TokenManager.isValidName("colon:name"));
        assertTrue(TokenManager.isValidName("alice-2"));
        assertTrue(TokenManager.isValidName("CI_bot"));
    }

    @Test
    void defaultNameLowercasesRole() {
        assertEquals("reader", TokenManager.defaultName(Role.READER));
        assertEquals("ops", TokenManager.defaultName(Role.OPS));
        assertEquals("admin", TokenManager.defaultName(Role.ADMIN));
    }

    @Test
    void nullConfigClearsConfiguredEntries() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));
        manager.updateFromConfig(null);
        assertNull(manager.authenticate("reader-token"));
        assertFalse(manager.hasRole(Role.READER));
    }

    @Test
    void invalidConfiguredEntriesAreSkipped() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        Map<String, TokenManager.ConfigToken> configured = new java.util.LinkedHashMap<>();
        configured.put(null, configToken(Role.READER, "secret"));
        configured.put("", configToken(Role.READER, "secret"));
        configured.put("blank", configToken(Role.READER, "  "));
        configured.put("no-secret", configToken(Role.READER, null));
        configured.put("no-role", new TokenManager.ConfigToken(null, "secret"));
        configured.put(null, null);
        configured.put("null-ct", null);
        configured.put("valid", configToken(Role.ADMIN, "admin-token"));
        manager.updateFromConfig(configured);

        assertNotNull(manager.authenticate("admin-token"));
        assertNull(manager.authenticate("secret"));
    }

    @Test
    void createRejectsInvalidInputs() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        assertThrows(IllegalArgumentException.class, () -> manager.create("bad name", Role.OPS));
        assertThrows(IllegalArgumentException.class, () -> manager.create("toolong:" + "x".repeat(60), Role.OPS));
        assertThrows(IllegalArgumentException.class, () -> manager.create("alice", null));
        assertThrows(IllegalArgumentException.class, () -> manager.create("", Role.OPS));
    }

    @Test
    void nullRuntimeFileSkipsPersistence() {
        TokenManager manager = new TokenManager(null);
        String token = manager.create("alice", Role.ADMIN);
        assertNotNull(manager.authenticate(token));
        manager.updateFromConfig(Map.of());
        assertNull(manager.authenticate(token));
        assertFalse(manager.revoke("alice"));
        assertFalse(manager.disable("missing"));
    }

    @Test
    void runtimeFileLoadsEveryLineShape() throws Exception {
        Path file = tempDir.resolve("complex.yml");
        java.nio.file.Files.writeString(
                file,
                "# comment\n"
                        + "\n"
                        + "disabled: ops,not-a name\n"
                        + "alice: ops 0011223344556677\n"
                        + "no-colon-line\n"
                        + "bad name: ops 0011223344556677\n"
                        + "empty: \n"
                        + "ops: 8899aabbccddeeff\n"
                        + "bob: 0011223344556677\n");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of());

        assertFalse(manager.hasRole(Role.ADMIN));
        assertTrue(manager.hasRole(Role.OPS));
        assertFalse(manager.hasRole(Role.READER));
        assertEquals("alice", manager.snapshot().get("alice").name());
        assertNull(manager.snapshot().get("bob"));
        assertTrue(manager.snapshot().get("ops").disabled());
        assertFalse(manager.snapshot().get("alice").disabled());
    }

    @Test
    void corruptRuntimeFileFallsBackToConfig() throws Exception {
        Path file = tempDir.resolve("corrupt.yml");
        java.nio.file.Files.writeString(file, "alice: ops nothex\n");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));

        assertEquals(Role.READER, manager.authenticate("reader-token").role());
        assertFalse(manager.hasRole(Role.OPS));
        assertTrue(manager.snapshot().get("reader").configured());
    }

    @Test
    void roleKeyedSingleTokenLineCorruptsFile() throws Exception {
        Path file = tempDir.resolve("role-as-hex.yml");
        java.nio.file.Files.writeString(file, "ops: ops\n");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));

        assertEquals(Role.READER, manager.authenticate("reader-token").role());
        assertFalse(manager.hasRole(Role.OPS));
    }

    @Test
    void disabledStatePrunedWhenConfigEntryRemoved() throws Exception {
        Path file = tempDir.resolve("prune.yml");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));
        manager.disable("reader");

        manager.updateFromConfig(Map.of());
        assertNull(manager.authenticate("reader-token"));
        assertFalse(manager.enable("reader"));
    }

    @Test
    void legacyReaderFormatDisabledFlag() throws Exception {
        Path file = tempDir.resolve("legacy-disabled.yml");
        java.nio.file.Files.writeString(file, "reader: 0011223344556677\ndisabled: reader\n");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of());

        assertTrue(manager.snapshot().get("reader").disabled());
        assertFalse(manager.hasRole(Role.READER));
        assertTrue(manager.enable("reader"));
    }

    @Test
    void persistFailureThrows() throws Exception {
        Path dir = tempDir.resolve("as-dir");
        Files.createDirectories(dir);
        TokenManager manager = new TokenManager(dir);
        assertThrows(IllegalStateException.class, () -> manager.create("alice", Role.OPS));
    }

    @Test
    void persistedFileUsesNameRoleHashFormat() throws Exception {
        Path file = tempDir.resolve("format.yml");
        TokenManager manager = new TokenManager(file);
        String token = manager.create("deploy", Role.OPS);
        String persisted = java.nio.file.Files.readString(file);
        assertTrue(persisted.startsWith("# mc2p tokens"));
        assertTrue(persisted.contains("deploy: ops "));
        assertFalse(persisted.contains("disabled:"));

        manager.disable("deploy");
        String persisted2 = java.nio.file.Files.readString(file);
        assertTrue(persisted2.contains("disabled: deploy"));
        assertNull(manager.authenticate(token));
    }

    @Test
    void disabledNameSurvivesConfigUpdateUntilEnabled() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of("reader", configToken(Role.READER, "reader-token")));
        manager.disable("reader");
        manager.updateFromConfig(Map.of("reader", configToken(Role.ADMIN, "admin-token")));

        assertNull(manager.authenticate("admin-token"));
        assertNull(manager.authenticate("reader-token"));
        assertTrue(manager.snapshot().get("reader").disabled());

        assertTrue(manager.enable("reader"));
        assertEquals(Role.ADMIN, manager.authenticate("admin-token").role());
        assertFalse(manager.snapshot().get("reader").disabled());
    }
}
