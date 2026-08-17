package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.mc2p.common.role.Role;
import dev.mc2p.common.tokens.TokenManager;

class TokenManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void authenticatesConfiguredTokensAndRejectsUnknown() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of(
                Role.READER, "reader-token",
                Role.OPS, "ops-token",
                Role.ADMIN, "admin-token"));

        var result = manager.authenticate("reader-token");
        assertNotNull(result);
        assertEquals(Role.READER, result.role());
        assertFalse(result.tokenId().isBlank());

        assertEquals(Role.OPS, manager.authenticate("ops-token").role());
        assertEquals(Role.ADMIN, manager.authenticate("admin-token").role());
        assertNull(manager.authenticate("wrong-token"));
        assertNull(manager.authenticate(""));
        assertNull(manager.authenticate(null));
    }

    @Test
    void rotateReplacesTokenAndRevokeRestoresConfigured() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of(Role.READER, "reader-token"));

        String rotated = manager.rotate(Role.READER);
        assertNotNull(rotated);
        assertEquals(Role.READER, manager.authenticate(rotated).role());
        assertNull(manager.authenticate("reader-token"));

        assertTrue(manager.revoke(Role.READER));
        assertEquals(Role.READER, manager.authenticate("reader-token").role());
        assertNull(manager.authenticate(rotated));
    }

    @Test
    void rotatedTokensSurviveRestart() {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of(Role.OPS, "ops-token"));
        String rotated = manager.rotate(Role.OPS);

        TokenManager reloaded = new TokenManager(file);
        reloaded.updateFromConfig(Map.of(Role.OPS, "ops-token"));
        assertEquals(Role.OPS, reloaded.authenticate(rotated).role());
    }

    @Test
    void neverStoresPlaintext() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file);
        manager.updateFromConfig(Map.of(Role.ADMIN, "top-secret"));
        String rotated = manager.rotate(Role.ADMIN);

        String persisted = java.nio.file.Files.readString(file);
        assertFalse(persisted.contains("top-secret"));
        assertFalse(persisted.contains(rotated));
        assertTrue(persisted.isBlank() || !persisted.contains(rotated));
    }

    @Test
    void snapshotReflectsConfiguredAndRotated() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"));
        manager.updateFromConfig(Map.of(Role.READER, "reader-token"));
        manager.rotate(Role.READER);

        var snapshot = manager.snapshot();
        assertTrue(snapshot.containsKey(Role.READER));
        assertFalse(snapshot.get(Role.READER).configured());
        assertNotNull(snapshot.get(Role.READER).tokenId());
    }
}