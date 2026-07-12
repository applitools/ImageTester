package com.applitools.imagetester;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies which CLI flag controls saveNewTests. ImageTester wires this as
 * {@code saveNewTests(!cmd.hasOption("pt"))}, so {@code -pt} is the flag that
 * makes new tests require manual review. {@code -pn} is page numbers and must
 * not affect it (regression guard for the v3.9.0 -pn -> -pt rename).
 */
public class PromptNewTestsFlagTest {

    private static boolean saveNewTestsFor(String... args) throws Exception {
        Method getOptions = ImageTester.class.getDeclaredMethod("getOptions");
        getOptions.setAccessible(true);
        Options options = (Options) getOptions.invoke(null);
        CommandLine cmd = new DefaultParser().parse(options, args);
        return !cmd.hasOption("pt");
    }

    @Test
    public void shouldSaveNewTestsAutomaticallyWhenNoFlagGiven() throws Exception {
        assertTrue(saveNewTestsFor());
    }

    @Test
    public void shouldRequireReviewWhenPtFlagGiven() throws Exception {
        assertFalse(saveNewTestsFor("-pt"));
    }

    @Test
    public void shouldStillSaveNewTestsAutomaticallyWhenPnFlagGiven() throws Exception {
        assertTrue(saveNewTestsFor("-pn"));
    }
}
