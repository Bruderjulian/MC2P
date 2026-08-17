package dev.mc2p.plugin.http;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Generates a self-signed PKCS12 keystore on first startup using the JDK's {@code keytool}
 * (no systemd, no external tooling). The keystore password comes from the configured env
 * var, or a generated password persisted next to the keystore.
 */
public final class SelfSignedCert {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SelfSignedCert() {
    }

    /**
     * Ensures a keystore exists at {@code keystore}.
     *
     * @return the keystore password, or null if the operation failed
     */
    public static String ensureKeystore(Path keystore, String passwordEnv, String serverId) {
        try {
            Files.createDirectories(keystore.toAbsolutePath().getParent());
            boolean exists = Files.isRegularFile(keystore);

            String password = envOrNull(passwordEnv);
            Path passwordFile = keystore.resolveSibling(keystore.getFileName() + ".password");

            if (exists) {
                if (password == null && Files.isRegularFile(passwordFile)) {
                    password = Files.readString(passwordFile).trim();
                }
                if (password == null) {
                    throw new IllegalStateException(
                            "keystore " + keystore + " exists but no password is available (set "
                                    + passwordEnv + " or keep the .password file)");
                }
                return password;
            }

            if (password == null) {
                password = generatePassword();
            }

            String dname = "CN=" + sanitize(serverId) + ", OU=MC2P, O=MC2P";
            Path javaHome = Path.of(System.getProperty("java.home"));
            List<String> command = List.of(
                    javaHome.resolve("bin/keytool").toString(),
                    "-genkeypair",
                    "-alias", "mc2p",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "3650",
                    "-dname", dname,
                    "-keystore", keystore.toAbsolutePath().toString(),
                    "-storetype", "PKCS12",
                    "-storepass", password,
                    "-keypass", password,
                    "-ext", "SAN=dns:localhost,ip:127.0.0.1");

            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("keytool failed with exit code " + exit);
            }

            Files.writeString(passwordFile, password);
            trySetOwnerOnly(passwordFile);
            trySetOwnerOnly(keystore);
            return password;
        } catch (Exception e) {
            throw new IllegalStateException("cannot generate self-signed keystore: " + e.getMessage(), e);
        }
    }

    private static String envOrNull(String env) {
        if (env == null || env.isBlank()) {
            return null;
        }
        String value = System.getenv(env);
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String generatePassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_\\- ]", "_");
    }

    private static void trySetOwnerOnly(Path path) {
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX filesystem
        } catch (Exception ignored) {
            // best effort
        }
    }
}