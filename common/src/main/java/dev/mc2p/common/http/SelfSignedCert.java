package dev.mc2p.common.http;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Generates a self-signed PKCS12 keystore on first startup using the JDK's
 * {@code keytool}
 * (no systemd, no external tooling). The keystore password comes from the
 * configured env
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
    public static String ensureKeystore(final Path keystore, final String passwordEnv, final String serverId) {
        try {
            Files.createDirectories(keystore.toAbsolutePath().getParent());
            final boolean exists = Files.isRegularFile(keystore);

            String password = envOrNull(passwordEnv);
            final Path passwordFile = keystore.resolveSibling(keystore.getFileName() + ".password");

            if (exists) {
                if (password == null && Files.isRegularFile(passwordFile)) {
                    password = Files.readString(passwordFile).trim();
                }
                if (password == null) {
                    throw new IllegalStateException(
                            "keystore " + keystore + " exists but no password is available (set " + passwordEnv
                                    + " or keep the .password file)");
                }
                return password;
            }

            if (password == null) {
                password = generatePassword();
            }

            final String dname = "CN=" + sanitize(serverId) + ", OU=MC2P, O=MC2P";
            final Path javaHome = Path.of(System.getProperty("java.home"));
            final List<String> command = List.of(
                    javaHome.resolve("bin/keytool").toString(),
                    "-genkeypair",
                    "-alias",
                    "mc2p",
                    "-keyalg",
                    "RSA",
                    "-keysize",
                    "2048",
                    "-validity",
                    "3650",
                    "-dname",
                    dname,
                    "-keystore",
                    keystore.toAbsolutePath().toString(),
                    "-storetype",
                    "PKCS12",
                    "-storepass",
                    password,
                    "-keypass",
                    password,
                    "-ext",
                    "SAN=dns:localhost,ip:127.0.0.1");

            final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            final int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("keytool failed with exit code " + exit);
            }

            Files.writeString(passwordFile, password);
            trySetOwnerOnly(passwordFile);
            trySetOwnerOnly(keystore);
            return password;
        } catch (final Exception e) {
            throw new IllegalStateException("cannot generate self-signed keystore: " + e.getMessage(), e);
        }
    }

    private static String envOrNull(final String env) {
        if (env == null || env.isBlank()) {
            return null;
        }
        final String value = System.getenv(env);
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String generatePassword() {
        final byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sanitize(final String value) {
        return value.replaceAll("[^A-Za-z0-9_\\- ]", "_");
    }

    private static void trySetOwnerOnly(final Path path) {
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (final UnsupportedOperationException ignored) {
            // non-POSIX filesystem
        } catch (final Exception ignored) {
            // best effort
        }
    }
}
