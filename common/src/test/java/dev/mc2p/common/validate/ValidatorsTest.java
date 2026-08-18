package dev.mc2p.common.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidatorsTest {

    @Test
    void safeWorldKeyRejectsTraversal() {
        assertFalse(Validators.isSafeWorldKey(null));
        assertFalse(Validators.isSafeWorldKey(""));
        assertFalse(Validators.isSafeWorldKey("world/../etc"));
        assertFalse(Validators.isSafeWorldKey("a\\b"));
        assertFalse(Validators.isSafeWorldKey("a:b"));
        assertFalse(Validators.isSafeWorldKey("a.b"));
        assertFalse(Validators.isSafeWorldKey("a b"));
        assertFalse(Validators.isSafeWorldKey("a\0b"));
        assertFalse(Validators.isSafeWorldKey("~tmp"));
        assertTrue(Validators.isSafeWorldKey("world_nether"));
        assertTrue(Validators.isSafeWorldKey("survival-2"));
    }

    @Test
    void parseUuidAcceptsStrictForm() {
        assertNull(Validators.parseUuid(null));
        assertNull(Validators.parseUuid("not-a-uuid"));
        UUID expected = UUID.fromString("3f8f5a2e-1f3a-4b6c-9d0e-abcdefabcdef");
        assertEquals(expected, Validators.parseUuid(" 3f8f5a2e-1f3a-4b6c-9d0e-abcdefabcdef "));
    }

    @Test
    void withinCoordinateUsesPositiveMax() {
        assertTrue(Validators.isWithinCoordinate(5, 10));
        assertTrue(Validators.isWithinCoordinate(-10, 10));
        assertFalse(Validators.isWithinCoordinate(11, 10));
        assertFalse(Validators.isWithinCoordinate(-11, 10));
        assertTrue(Validators.isWithinCoordinate(1, 1));
        assertFalse(Validators.isWithinCoordinate(2, 1));
        assertTrue(Validators.isWithinCoordinate(1, 0));
        assertFalse(Validators.isWithinCoordinate(2, 0));
    }

    @Test
    void parseCoordinatesHandlesEveryInputShape() {
        assertArrayEquals(new int[] {1, 2, 3}, Validators.parseCoordinates("1,2,3", 100));
        assertArrayEquals(new int[] {1, 2, 3}, Validators.parseCoordinates(" 1 , 2 , 3 ", 100));
        assertNull(Validators.parseCoordinates(null, 100));
        assertNull(Validators.parseCoordinates("1,2", 100));
        assertNull(Validators.parseCoordinates("1,2,3,4", 100));
        assertNull(Validators.parseCoordinates("a,b,c", 100));
        assertNull(Validators.parseCoordinates("101,2,3", 100));
        assertNull(Validators.parseCoordinates("1,-101,3", 100));
        assertNull(Validators.parseCoordinates("1,2,101", 100));
    }

    @Test
    void pageAndLimitBounds() {
        assertTrue(Validators.isValidPage(0, 100));
        assertTrue(Validators.isValidPage(99_999, 100));
        assertFalse(Validators.isValidPage(-1, 100));
        assertFalse(Validators.isValidPage(100_000, 100));

        assertTrue(Validators.isValidLimit(1, 10));
        assertTrue(Validators.isValidLimit(10, 10));
        assertFalse(Validators.isValidLimit(0, 10));
        assertFalse(Validators.isValidLimit(11, 10));
    }

    @Test
    void gamemodeNormalizationAllAliases() {
        assertNull(Validators.normalizeGamemode(null));
        assertNull(Validators.normalizeGamemode(""));
        assertEquals("SURVIVAL", Validators.normalizeGamemode("0"));
        assertEquals("SURVIVAL", Validators.normalizeGamemode("SURVIVAL"));
        assertEquals("SURVIVAL", Validators.normalizeGamemode("s"));
        assertEquals("CREATIVE", Validators.normalizeGamemode("1"));
        assertEquals("CREATIVE", Validators.normalizeGamemode("Creative"));
        assertEquals("CREATIVE", Validators.normalizeGamemode("c"));
        assertEquals("ADVENTURE", Validators.normalizeGamemode("2"));
        assertEquals("ADVENTURE", Validators.normalizeGamemode("adventure"));
        assertEquals("ADVENTURE", Validators.normalizeGamemode("a"));
        assertEquals("SPECTATOR", Validators.normalizeGamemode("3"));
        assertEquals("SPECTATOR", Validators.normalizeGamemode("Spectator"));
        assertEquals("SPECTATOR", Validators.normalizeGamemode("sp"));
        assertNull(Validators.normalizeGamemode("hardcore"));
    }

    @Test
    void safeMaterialNameEnforcesRegistryKey() {
        assertFalse(Validators.isSafeMaterialName(null));
        assertFalse(Validators.isSafeMaterialName(""));
        assertFalse(Validators.isSafeMaterialName("x".repeat(65)));
        assertFalse(Validators.isSafeMaterialName("minecraft:stone pickaxe"));
        assertFalse(Validators.isSafeMaterialName("minecraft:stone/block"));
        assertFalse(Validators.isSafeMaterialName("minecraft:stone\u0000x"));
        assertTrue(Validators.isSafeMaterialName("minecraft:stone"));
        assertTrue(Validators.isSafeMaterialName("diamond_sword"));
        assertTrue(Validators.isSafeMaterialName("my_mod:item_2"));
    }

    @Test
    void entityAndEffectDelegateToMaterialRule() {
        assertTrue(Validators.isSafeEntityType("minecraft:zombie"));
        assertFalse(Validators.isSafeEntityType("zombie pigman"));
        assertTrue(Validators.isSafeEffectName("minecraft:regeneration"));
        assertFalse(Validators.isSafeEffectName("a/b"));
    }

    @Test
    void reasonLengthBounds() {
        assertFalse(Validators.isSafeReason(null));
        assertTrue(Validators.isSafeReason(""));
        assertTrue(Validators.isSafeReason("x".repeat(256)));
        assertFalse(Validators.isSafeReason("x".repeat(257)));
    }

    @Test
    void withinRangeBounds() {
        assertTrue(Validators.isWithin(5, 1, 10));
        assertTrue(Validators.isWithin(1, 1, 10));
        assertTrue(Validators.isWithin(10, 1, 10));
        assertFalse(Validators.isWithin(0, 1, 10));
        assertFalse(Validators.isWithin(11, 1, 10));
    }

    @Test
    void allowedWorldGate() {
        Set<String> worlds = Set.of("world", "world_nether");
        assertTrue(Validators.isAllowedWorld("world", worlds));
        assertFalse(Validators.isAllowedWorld("secret", worlds));
        assertFalse(Validators.isAllowedWorld("world", null));
    }

    @Test
    void commandFirstTokenCheck() {
        assertTrue(Validators.isCommandFirstToken("gamemode creative", "gamemode"));
        assertFalse(Validators.isCommandFirstToken("gamemode creative", "give"));
        assertFalse(Validators.isCommandFirstToken(null, "gamemode"));
        assertFalse(Validators.isCommandFirstToken("gamemode", null));
    }

    @Test
    void firstTokenExtraction() {
        assertEquals("", Validators.firstToken(null));
        assertEquals("gamemode", Validators.firstToken("gamemode creative"));
        assertEquals("gamemode", Validators.firstToken("  gamemode  creative  "));
        assertEquals("tp", Validators.firstToken("tp"));
    }

    private static void assertArrayEquals(int[] expected, int[] actual) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    }
}
