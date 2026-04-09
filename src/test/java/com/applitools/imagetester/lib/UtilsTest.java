package com.applitools.imagetester.lib;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void parsePagesNotation_singlePage() {
        assertEquals(Arrays.asList(3), Utils.parsePagesNotation("3"));
    }

    @Test
    public void parsePagesNotation_multiplePages() {
        assertEquals(Arrays.asList(1, 3, 5), Utils.parsePagesNotation("1,3,5"));
    }

    @Test
    public void parsePagesNotation_range() {
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), Utils.parsePagesNotation("1-5"));
    }

    @Test
    public void parsePagesNotation_reverseRange() {
        assertEquals(Arrays.asList(5, 4, 3, 2, 1), Utils.parsePagesNotation("5-1"));
    }

    @Test
    public void parsePagesNotation_mixed() {
        assertEquals(Arrays.asList(1, 3, 5, 6, 7), Utils.parsePagesNotation("1,3,5-7"));
    }

    @Test
    public void parsePagesNotation_withSpaces() {
        assertEquals(Arrays.asList(1, 3, 5, 6, 7), Utils.parsePagesNotation(" 1 , 3 , 5 - 7 "));
    }

    @Test
    public void parsePagesNotation_null() {
        assertNull(Utils.parsePagesNotation(null));
    }

    @Test
    public void parsePagesNotation_empty() {
        assertNull(Utils.parsePagesNotation(""));
    }

    @Test(expected = NumberFormatException.class)
    public void parsePagesNotation_invalidInput() {
        Utils.parsePagesNotation("abc");
    }

    @Test
    public void generateRanage_normalRange() {
        assertEquals(Arrays.asList(1, 2, 3, 4), Utils.generateRanage(5, 1));
    }

    @Test
    public void generateRanage_emptyRange() {
        assertTrue(Utils.generateRanage(1, 1).isEmpty());
    }

    @Test
    public void generateRanage_startAtZero() {
        assertEquals(Arrays.asList(0, 1, 2), Utils.generateRanage(3, 0));
    }

    @Test
    public void parseEnum_validUppercase() {
        assertEquals(com.applitools.eyes.MatchLevel.STRICT,
                Utils.parseEnum(com.applitools.eyes.MatchLevel.class, "STRICT"));
    }

    @Test
    public void parseEnum_validMixedCase() {
        assertEquals(com.applitools.eyes.MatchLevel.STRICT,
                Utils.parseEnum(com.applitools.eyes.MatchLevel.class, "strict"));
    }

    @Test(expected = RuntimeException.class)
    public void parseEnum_invalidValue() {
        Utils.parseEnum(com.applitools.eyes.MatchLevel.class, "NONEXISTENT");
    }

    @Test
    public void parseEnumIgnoreChars_matchesWithUnderscores() {
        assertEquals(com.applitools.eyes.MatchLevel.IGNORE_COLORS,
                Utils.parseEnum(com.applitools.eyes.MatchLevel.class, "IgnoreColors", "_"));
    }

    @Test(expected = RuntimeException.class)
    public void parseEnumIgnoreChars_invalidValue() {
        Utils.parseEnum(com.applitools.eyes.MatchLevel.class, "NONEXISTENT", "_");
    }

    @Test
    public void getEnumValues_containsAllValues() {
        String result = Utils.getEnumValues(com.applitools.eyes.MatchLevel.class);
        assertTrue(result.contains("Strict"));
        assertTrue(result.contains("|"));
    }
}
