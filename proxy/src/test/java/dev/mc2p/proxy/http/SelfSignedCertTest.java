package dev.mc2p.proxy.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class SelfSignedCertTest {

    private static final String PASSWORD_ENV = "MC2P_KEYSTORE_PW_TEST";

    @SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables();

    @TempDir
    Path tempDir;

    @Test
    void readsPasswordFromSidecarFile() throws Exception {
        Path keystore = tempDir.resolve("k.p12");
        Files.writeString(keystore, "existing");
        Files.writeString(tempDir.resolve("k.p12.password"), "secret123\n");
        assertEquals("secret123", SelfSignedCert.ensureKeystore(keystore, PASSWORD_ENV, "srv"));
    }

    @Test
    void prefersEnvPasswordOverSidecar() throws Exception {
        environment.set(PASSWORD_ENV, "envpw");
        Path keystore = tempDir.resolve("k.p12");
        Files.writeString(keystore, "existing");
        Files.writeString(tempDir.resolve("k.p12.password"), "sidecar\n");
        assertEquals("envpw", SelfSignedCert.ensureKeystore(keystore, PASSWORD_ENV, "srv"));
    }

    @Test
    void existingKeystoreWithoutPasswordFails() throws Exception {
        Path keystore = tempDir.resolve("k.p12");
        Files.writeString(keystore, "existing");
        IllegalStateException e = assertThrows(
                IllegalStateException.class, () -> SelfSignedCert.ensureKeystore(keystore, PASSWORD_ENV, "srv"));
        assertTrue(e.getMessage().contains("no password"));
    }

    @Test
    void blankEnvPasswordCountsAsMissing() throws Exception {
        environment.set(PASSWORD_ENV, "   ");
        Path keystore = tempDir.resolve("k.p12");
        Files.writeString(keystore, "existing");
        assertThrows(IllegalStateException.class, () -> SelfSignedCert.ensureKeystore(keystore, PASSWORD_ENV, "srv"));
    }

    @Test
    void generatesKeystoreViaKeytool() throws Exception {
        Path keystore = tempDir.resolve("gen.p12");
        String password = SelfSignedCert.ensureKeystore(keystore, PASSWORD_ENV, "main server/1!");
        assertFalse(password.isEmpty());
        assertTrue(Files.isRegularFile(keystore));
        Path passwordFile = tempDir.resolve("gen.p12.password");
        assertTrue(Files.isRegularFile(passwordFile));
        assertEquals(password, Files.readString(passwordFile));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void posixPermissionsFailureIsToleratedOnWindows() throws Exception {
        Path keystore = tempDir.resolve("win.p12");
        String password = SelfSignedCert.ensureKeystore(keystore, PASSWORD_ENV, "srv");
        assertFalse(password.isEmpty());
        assertTrue(Files.isRegularFile(keystore));
    }
}
