package dev.mc2p.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class ConfigSupportTest {

    @SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables();

    @TempDir
    Path tempDir;

    @Test
    void loadYamlMissingFileReturnsEmpty() throws IOException {
        assertTrue(ConfigSupport.loadYaml(tempDir.resolve("nope.yml")).isEmpty());
    }

    @Test
    void loadYamlEmptyFileReturnsEmpty() throws IOException {
        Path file = tempDir.resolve("empty.yml");
        Files.writeString(file, "");
        assertTrue(ConfigSupport.loadYaml(file).isEmpty());
    }

    @Test
    void loadYamlParsesMapping() throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, "a: 1\nb: hello\nnested:\n  c: true\n");
        Map<String, Object> parsed = ConfigSupport.loadYaml(file);
        assertEquals(1, parsed.get("a"));
        assertEquals("hello", parsed.get("b"));
        assertEquals(Map.of("c", true), parsed.get("nested"));
    }

    @Test
    void loadYamlNonMappingRootThrows() {
        Path file = tempDir.resolve("scalar.yml");
        assertThrows(IOException.class, () -> {
            Files.writeString(file, "just a scalar\n");
            ConfigSupport.loadYaml(file);
        });
    }

    @Test
    void loadYamlMalformedYamlThrows() {
        Path file = tempDir.resolve("malformed.yml");
        assertThrows(RuntimeException.class, () -> {
            Files.writeString(file, "a: [unclosed");
            ConfigSupport.loadYaml(file);
        });
    }

    @Test
    void resolveSecretPlaintextAndWhitespace() {
        ConfigSupport.Secret plain = ConfigSupport.resolveSecret("  hunter2  ", tempDir);
        assertEquals("hunter2", plain.value());
        assertEquals("config", plain.source());
        assertFalse(plain.fromEnvironment());
        assertNull(ConfigSupport.resolveSecret(null, tempDir));
        assertNull(ConfigSupport.resolveSecret("   ", tempDir));
    }

    @Test
    void resolveSecretEnvSource() {
        ConfigSupport.Secret env = ConfigSupport.resolveSecret("env:PATH", tempDir);
        assertTrue(env.fromEnvironment());
        assertTrue(env.source().startsWith("env:"));
        assertFalse(env.value().isEmpty());
        assertNull(ConfigSupport.resolveSecret("env:MC2P_UNDEFINED_VARIABLE_xyz", tempDir));
    }

    @Test
    void resolveSecretEnvBlankValueReturnsNull() {
        environment.set("MC2P_BLANK_ENV", "   ");
        assertNull(ConfigSupport.resolveSecret("env:MC2P_BLANK_ENV", tempDir));
        assertNull(ConfigSupport.resolveSecret("env:   ", tempDir));
    }

    @Test
    void resolveSecretFileSourceRelativeAndAbsolute() throws IOException {
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "  file-secret  \n");
        ConfigSupport.Secret rel = ConfigSupport.resolveSecret("file:secret.txt", tempDir);
        assertEquals("file-secret", rel.value());
        assertFalse(rel.fromEnvironment());

        ConfigSupport.Secret abs = ConfigSupport.resolveSecret("file:" + secret, tempDir);
        assertEquals("file-secret", abs.value());

        assertNull(ConfigSupport.resolveSecret("file:missing.txt", tempDir));
        Path blank = tempDir.resolve("blank.txt");
        Files.writeString(blank, "   \n");
        assertNull(ConfigSupport.resolveSecret("file:blank.txt", tempDir));
    }

    @Test
    void resolveSecretFileSourceAbsoluteIgnoresBaseDir() throws IOException {
        Path secret = tempDir.resolve("abs-secret.txt");
        Files.writeString(secret, "abs");
        Path other = tempDir.resolve("other");
        Files.createDirectories(other);
        ConfigSupport.Secret result = ConfigSupport.resolveSecret("file:" + secret, other);
        assertEquals("abs", result.value());
    }

    @Test
    void mapConvertsAndNonMapFallsBack() {
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("k", 1);
        assertEquals(1, ConfigSupport.map(source).get("k"));
        assertTrue(ConfigSupport.map("not-a-map").isEmpty());
        assertTrue(ConfigSupport.map(null).isEmpty());
        assertTrue(ConfigSupport.map(List.of(1, 2)).isEmpty());
    }

    @Test
    void strUsesFallbackAndStringifies() {
        Map<String, Object> map = Map.of("key", 42, "str", "value");
        assertEquals("value", ConfigSupport.str(map, "str", "fb"));
        assertEquals("42", ConfigSupport.str(map, "key", "fb"));
        assertEquals("fb", ConfigSupport.str(map, "missing", "fb"));
    }

    @Test
    void integerParsesNumbersStringsAndFallsBack() {
        Map<String, Object> map = Map.of("n", 7, "s", " 12 ", "bad", "abc");
        assertEquals(7, ConfigSupport.integer(map, "n", 0));
        assertEquals(12, ConfigSupport.integer(map, "s", 0));
        assertEquals(0, ConfigSupport.integer(map, "bad", 0));
        assertEquals(9, ConfigSupport.integer(map, "missing", 9));
    }

    @Test
    void boolParsesAllFormsAndFallsBack() {
        Map<String, Object> map = Map.of(
                "t", true,
                "f", false,
                "s1", "true",
                "s2", "YES",
                "s3", "on",
                "s4", "false",
                "s5", "No",
                "s6", "Off",
                "bad", "maybe",
                "n", 1);
        assertTrue(ConfigSupport.bool(map, "t", false));
        assertFalse(ConfigSupport.bool(map, "f", true));
        assertTrue(ConfigSupport.bool(map, "s1", false));
        assertTrue(ConfigSupport.bool(map, "s2", false));
        assertTrue(ConfigSupport.bool(map, "s3", false));
        assertFalse(ConfigSupport.bool(map, "s4", true));
        assertFalse(ConfigSupport.bool(map, "s5", true));
        assertFalse(ConfigSupport.bool(map, "s6", true));
        assertFalse(ConfigSupport.bool(map, "bad", false));
        assertTrue(ConfigSupport.bool(map, "bad", true));
        assertTrue(ConfigSupport.bool(map, "n", true));
        assertFalse(ConfigSupport.bool(map, "missing", false));
    }

    @Test
    void stringsConvertsListItemsAndFallsBack() {
        Map<String, Object> map = Map.of("list", Arrays.asList("a", 2, null, "d"));
        assertEquals(List.of("a", "2", "d"), ConfigSupport.strings(map, "list"));
        assertTrue(ConfigSupport.strings(map, "missing").isEmpty());
        assertTrue(ConfigSupport.strings(Map.of("x", "not-a-list"), "x").isEmpty());
    }

    @Test
    void secretRecordIsExposed() {
        ConfigSupport.Secret secret = ConfigSupport.resolveSecret("plain", tempDir);
        assertInstanceOf(ConfigSupport.Secret.class, secret);
        assertEquals("plain", secret.value());
    }
}