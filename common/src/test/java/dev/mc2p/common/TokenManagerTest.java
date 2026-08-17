package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.tokens.TokenManager;
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
    void rejectsInvalidNames() {
        assertFalse(TokenManager.isValidName(null));
        assertFalse(TokenManager.isValidName(""));
        assertFalse(TokenManager.isValidName("has space"));
        assertFalse(TokenManager.isValidName("colon:name"));
        assertTrue(TokenManager.isValidName("alice-2"));
        assertTrue(TokenManager.isValidName("CI_bot"));
    }
}
