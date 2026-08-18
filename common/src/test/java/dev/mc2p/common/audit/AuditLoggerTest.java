package dev.mc2p.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.audit.AuditLogger.AuditWriteException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditLoggerTest {

    @TempDir
    Path tempDir;

    private static String bigLine(int targetBytes) {
        return "{\"detail\":\"" + "x".repeat(Math.max(0, targetBytes - 12)) + "\"}";
    }

    @Test
    void writesJsonLinesWithEscaping() throws IOException {
        Path file = tempDir.resolve("audit.log");
        AuditLogger logger = new AuditLogger(file, 8, 3);
        logger.log("alice", "tid-1", "srv-1", "gamemode", "execute", "{\"from\":\"a\"}");
        logger.log(null, null, "srv-1", "tp", "execute");
        logger.log("bob", "tid-2", "srv-1", "say", "execute");

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"client\":\"alice\""));
        assertTrue(content.contains("\"tokenId\":\"tid-1\""));
        assertTrue(content.contains("\"client\":\"\""));
        assertTrue(content.contains("\"detail\":{\"from\":\"a\"}"));
        assertFalse(content.contains("\"role\""));
        assertEquals(3, content.strip().lines().count());
    }

    @Test
    void quotedValuesEscapeSpecialCharacters() {
        Path file = tempDir.resolve("escape.log");
        AuditLogger logger = new AuditLogger(file, 8, 3);
        String nasty = "tab\tquote\"back\\slash\nnewline\rreturn\u0001bell";
        logger.log(nasty, "t", "s", "tool", "action");

        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertTrue(content.contains("\\t"));
        assertTrue(content.contains("\\\""));
        assertTrue(content.contains("\\\\"));
        assertTrue(content.contains("\\n"));
        assertTrue(content.contains("\\r"));
        assertTrue(content.contains("\\u0001"));
    }

    @Test
    void nullDetailRendersAsEmptyObject() throws IOException {
        Path file = tempDir.resolve("nulldetail.log");
        AuditLogger logger = new AuditLogger(file, 8, 3);
        logger.log("x", "t", "s", "tool", "action", null);
        logger.log("x", "t", "s", "tool", "action", "");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"detail\":{}"));
    }

    @Test
    void pathWithoutParentIsAccepted() throws IOException {
        String name = "mc2p-audit-test-" + System.nanoTime() + ".log";
        Path file = Path.of(name);
        try {
            AuditLogger logger = new AuditLogger(file, 8, 3);
            logger.log("alice", "t1", "s", "tool", "action");
            assertTrue(Files.exists(file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(Path.of(file + ".1"));
        }
    }

    @Test
    void detailAndNoDetailHelpers() {
        assertEquals("", AuditLogger.detail(null));
        assertEquals("hello", AuditLogger.detail("hello"));
        assertEquals("", AuditLogger.noDetail());
    }

    @Test
    void writesToExistingFileAndCreatesDirectories() throws IOException {
        Path dir = tempDir.resolve("nested").resolve("deep");
        Path file = dir.resolve("audit.log");
        Files.createDirectories(dir);
        Files.writeString(file, "pre-existing line\n");

        AuditLogger logger = new AuditLogger(file, 8, 3);
        logger.log("carol", "t3", "s", "tool", "action");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("pre-existing line\n"));
        assertTrue(content.contains("carol"));
    }

    @Test
    void rotatesWhenExceedingSizeLimit() throws IOException {
        Path file = tempDir.resolve("rotating.log");
        AuditLogger logger = new AuditLogger(file, 1, 3);

        for (int i = 0; i < 4; i++) {
            logger.log("bulk-" + i, "t" + i, "s", "tool", "action", bigLine(1_100_000));
        }

        assertTrue(Files.exists(Path.of(file + ".1")), "rotated file .1 should exist");
        assertTrue(Files.exists(Path.of(file + ".2")));
        assertTrue(Files.exists(Path.of(file + ".3")));
        assertTrue(Files.size(Path.of(file + ".1")) < 2_000_000, "rotated file should hold a single line");
    }

    @Test
    void writeFailureThrowsAuditWriteException() {
        Path dir = tempDir.resolve("as-file");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        AuditLogger logger = new AuditLogger(dir, 8, 3);
        assertThrows(AuditWriteException.class, () -> logger.log("x", "t", "s", "tool", "action"));
    }

    @Test
    void constructorFailureThrowsAuditWriteException() throws IOException {
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "i am a file");
        Path log = blocker.resolve("sub").resolve("audit.log");
        assertThrows(AuditWriteException.class, () -> new AuditLogger(log, 8, 3));
    }

    @Test
    void maxFilesAtLeastOne() throws IOException {
        Path file = tempDir.resolve("single.log");
        AuditLogger logger = new AuditLogger(file, 1, 0);
        logger.log("alice", "t1", "s", "tool", "action", bigLine(1_100_000));
        logger.log("alice", "t1", "s", "tool", "action", bigLine(1_100_000));
        assertTrue(Files.exists(Path.of(file + ".1")));
    }

    @Test
    void rotateFailureThrowsAuditWriteException() throws IOException {
        Path file = tempDir.resolve("rotatefail.log");
        Path blocker = Path.of(file + ".1");
        Files.createDirectories(blocker);
        Files.writeString(blocker.resolve("keep.txt"), "x");
        AuditLogger logger = new AuditLogger(file, 1, 1);
        assertThrows(
                AuditWriteException.class, () -> logger.log("alice", "t1", "s", "tool", "action", bigLine(1_100_000)));
    }

    @Test
    void exceptionCarriesMessageAndCause() {
        RuntimeException cause = new RuntimeException("boom");
        AuditWriteException ex = new AuditWriteException("failed", cause);
        assertEquals("failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertNotNull(ex);
    }
}
