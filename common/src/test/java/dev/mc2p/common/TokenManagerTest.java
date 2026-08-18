package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.config.RestrictionsConfig;
import dev.mc2p.common.tokens.TokenManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void authenticatesCreatedTokensAndRejectsUnknown() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"), tempDir);
        String token = manager.create("alice");

        var result = manager.authenticate(token);
        assertNotNull(result);
        assertEquals("alice", result.name());
        assertFalse(result.tokenId().isBlank());
        assertNotNull(result.restrictions());

        assertNull(manager.authenticate("wrong-token"));
        assertNull(manager.authenticate(""));
        assertNull(manager.authenticate(null));
    }

    @Test
    void multipleTokensAreDistinguishable() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"), tempDir);
        String alice = manager.create("alice");
        String bob = manager.create("bob");

        var aliceResult = manager.authenticate(alice);
        var bobResult = manager.authenticate(bob);
        assertNotNull(aliceResult);
        assertNotNull(bobResult);
        assertEquals("alice", aliceResult.name());
        assertEquals("bob", bobResult.name());
    }

    @Test
    void createReplacesExistingName() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"), tempDir);
        String first = manager.create("alice");
        String second = manager.create("alice");

        assertNull(manager.authenticate(first));
        assertNotNull(manager.authenticate(second));
    }

    @Test
    void createdTokensSurviveRestart() {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file, tempDir);
        String created = manager.create("alice");

        TokenManager reloaded = new TokenManager(file, tempDir);
        reloaded.load();
        var result = reloaded.authenticate(created);
        assertNotNull(result);
        assertEquals("alice", result.name());
    }

    @Test
    void legacyRoleKeyedRuntimeFileStillLoads() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        java.nio.file.Files.writeString(file, "# old format\nreader: 0011223344556677\n");
        TokenManager manager = new TokenManager(file, tempDir);
        manager.load();

        assertEquals("reader", manager.snapshot().get("reader").name());
    }

    @Test
    void neverStoresPlaintext() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file, tempDir);
        String created = manager.create("admin");

        String persisted = java.nio.file.Files.readString(file);
        assertFalse(persisted.contains("sha256:" + created));
        assertTrue(persisted.contains("sha256:"));
    }

    @Test
    void snapshotReflectsStore() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"), tempDir);
        manager.create("alice");
        RestrictionsConfig restricted = RestrictionsConfig.load(Map.of("tools", Map.of("enabled", true)));
        manager.create("restricted", restricted);

        var snapshot = manager.snapshot();
        assertEquals(2, snapshot.size());
        assertNotNull(snapshot.get("alice").tokenId());
        assertEquals(restricted, snapshot.get("restricted").restrictions());
        assertFalse(snapshot.get("alice").disabled());
    }

    @Test
    void disableAndEnable() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"), tempDir);
        String token = manager.create("alice");

        assertTrue(manager.disable("alice"));
        assertNull(manager.authenticate(token));
        assertTrue(manager.snapshot().get("alice").disabled());
        assertFalse(manager.disable("missing"));

        assertTrue(manager.enable("alice"));
        assertEquals("alice", manager.authenticate(token).name());
        assertFalse(manager.snapshot().get("alice").disabled());
        assertFalse(manager.enable("alice"));
    }

    @Test
    void disabledStateSurvivesRestart() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file, tempDir);
        String token = manager.create("alice");
        manager.disable("alice");

        TokenManager reloaded = new TokenManager(file, tempDir);
        reloaded.load();
        assertNull(reloaded.authenticate(token));
        assertTrue(reloaded.snapshot().get("alice").disabled());
    }

    @Test
    void revokeRemovesAndPersistence() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file, tempDir);
        String token = manager.create("alice");
        assertTrue(manager.revoke("alice"));
        assertFalse(manager.revoke("alice"));
        assertNull(manager.authenticate(token));

        TokenManager reloaded = new TokenManager(file, tempDir);
        reloaded.load();
        assertTrue(reloaded.snapshot().isEmpty());
    }

    @Test
    void clearRemovesEverything() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file, tempDir);
        manager.create("alice");
        manager.clear();
        assertTrue(manager.snapshot().isEmpty());

        TokenManager reloaded = new TokenManager(file, tempDir);
        reloaded.load();
        assertTrue(reloaded.snapshot().isEmpty());
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
    void createRejectsInvalidInputs() {
        TokenManager manager = new TokenManager(tempDir.resolve("tokens.yml"), tempDir);
        assertThrows(IllegalArgumentException.class, () -> manager.create("bad name"));
        assertThrows(IllegalArgumentException.class, () -> manager.create("toolong:" + "x".repeat(60)));
        assertThrows(IllegalArgumentException.class, () -> manager.create(""));
    }

    @Test
    void nullRuntimeFileSkipsPersistence() {
        TokenManager manager = new TokenManager(null, tempDir);
        String token = manager.create("alice");
        assertNotNull(manager.authenticate(token));
        assertTrue(manager.revoke("alice"));
        assertNull(manager.authenticate(token));
        assertFalse(manager.disable("missing"));
    }

    @Test
    void yamlListStoreRoundTripsRestrictions() {
        Path file = tempDir.resolve("tokens.yml");
        TokenManager manager = new TokenManager(file, tempDir);
        RestrictionsConfig restricted = RestrictionsConfig.load(
                Map.of("tools", Map.of("enabled", true, "allowlist", List.of("block_get", "server_status"))));
        manager.create("julian", restricted);

        TokenManager reloaded = new TokenManager(file, tempDir);
        reloaded.load();
        assertEquals(restricted, reloaded.snapshot().get("julian").restrictions());
    }

    @Test
    void yamlListStoreLoadsHandEditedRows() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        Files.writeString(
                file,
                """
                - name: julian
                  token: sha256:00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff
                  restrictions:
                    tools:
                      enabled: true
                      allowlist: [block_get]
                - name: disabled-token
                  token: sha256:ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100
                  disabled: true
                """);
        TokenManager manager = new TokenManager(file, tempDir);
        manager.load();

        assertTrue(manager.snapshot().containsKey("julian"));
        assertTrue(manager.snapshot().get("julian").restrictions().tools().enabled());
        assertTrue(manager.snapshot().get("disabled-token").disabled());
        assertEquals(2, manager.snapshot().size());
    }

    @Test
    void corruptStoreYieldsEmptyStore() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        Files.writeString(file, "this: [is: not: valid: yaml\n\t\t");
        TokenManager manager = new TokenManager(file, tempDir);
        manager.load();
        assertTrue(manager.snapshot().isEmpty());
    }

    @Test
    void loadIgnoresInvalidRows() throws Exception {
        Path file = tempDir.resolve("tokens.yml");
        Files.writeString(
                file,
                """
                - name: bad name!
                  token: sha256:00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff
                - name: no-spec
                - name: garbage-hash
                  token: sha256:not-hex
                - name: good
                  token: sha256:00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff
                """);
        TokenManager manager = new TokenManager(file, tempDir);
        manager.load();
        assertTrue(manager.snapshot().containsKey("good"));
        assertFalse(manager.snapshot().containsKey("bad name!"));
        assertFalse(manager.snapshot().containsKey("no-spec"));
        assertFalse(manager.snapshot().containsKey("garbage-hash"));
    }

    @Test
    void persistFailureThrows() throws Exception {
        Path dir = tempDir.resolve("as-dir");
        Files.createDirectories(dir);
        TokenManager manager = new TokenManager(dir, tempDir);
        assertThrows(IllegalStateException.class, () -> manager.create("alice"));
    }
}
