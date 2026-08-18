package dev.mc2p.common.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetupSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void readSecretFileMissingReturnsNull() {
        assertNull(SetupSupport.readSecretFile(tempDir, "nope"));
    }

    @Test
    void readSecretFileTrimsAndReturnsValue() throws IOException {
        Path dir = tempDir.resolve("data");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("secret"), "  abc123  \n");
        assertEquals("abc123", SetupSupport.readSecretFile(dir, "secret"));
    }

    @Test
    void readSecretFileBlankReturnsNull() throws IOException {
        Path dir = tempDir.resolve("data");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("secret"), "   \n");
        assertNull(SetupSupport.readSecretFile(dir, "secret"));
    }

    @Test
    void writeSecretFileCreatesParentAndContent() throws IOException {
        Path dir = tempDir.resolve("nested").resolve("data");
        SetupSupport.writeSecretFile(dir, "proxy-secret", "s3cr3t");
        assertEquals("s3cr3t", Files.readString(dir.resolve("proxy-secret")));
    }

    @Test
    void writeSecretFileOverwritesExisting() throws IOException {
        Path dir = tempDir.resolve("data");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("proxy-secret"), "old");
        SetupSupport.writeSecretFile(dir, "proxy-secret", "new");
        assertEquals("new", Files.readString(dir.resolve("proxy-secret")));
    }

    @Test
    void clientConfigTemplateEmbedsPort() {
        String template = SetupSupport.clientConfigTemplate(8443);
        assertTrue(template.contains("https://<HOST>:8443/mcp"));
        assertTrue(template.contains("Bearer <TOKEN>"));
        assertTrue(template.contains("streamable-http"));
    }
}