package com.applitools.imagetester.TestObjects;

import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;

import java.io.File;
import java.nio.file.Files;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class ImageFileTestTest {

    private static final String FIXTURES = "src/test/resources/fixtures";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Config config;
    private Eyes mockEyes;

    @Before
    public void setUp() {
        config = new Config();
        config.appName = "TestApp";
        config.logger = new Logger();

        mockEyes = mock(Eyes.class);
        when(mockEyes.getIsOpen()).thenReturn(false, true);
        when(mockEyes.close(anyBoolean())).thenReturn(mock(TestResults.class));
    }

    @Test
    public void run_returnsOutcomeAsResultInsteadOfThrowing() throws Exception {
        // The SDK's throwing close turns legitimate outcomes ("new test, please approve",
        // "diffs found") into exceptions; every other test object closes with false and
        // reports outcomes as result rows — image tests must match.
        TestResults newTestResult = mock(TestResults.class);
        Eyes eyes = mock(Eyes.class);
        when(eyes.getIsOpen()).thenReturn(false, true);
        when(eyes.close(false)).thenReturn(newTestResult);
        when(eyes.close(true)).thenThrow(
                new com.applitools.eyes.EyesException("Test 'x' of 'TestApp' is new! Please approve the new baseline"));

        ImageFileTest test = new ImageFileTest(new File(FIXTURES, "sample.png"), config);

        assertEquals(newTestResult, test.run(eyes));
    }

    @Test
    public void validPng_opensChecksCloses() throws Exception {
        ImageFileTest test = new ImageFileTest(new File(FIXTURES, "sample.png"), config);
        test.run(mockEyes);

        verify(mockEyes).open(eq("TestApp"), eq("sample.png"), any(RectangleSize.class));
        verify(mockEyes).check(eq("sample.png"), ArgumentMatchers.any());
        verify(mockEyes).close(false);
    }

    @Test
    public void tiffFile_convertsAndChecks() throws Exception {
        // Copy the TIFF to a temp directory so the converted PNG does not overwrite the fixture.
        File tiffCopy = new File(tempFolder.getRoot(), "sample.tif");
        Files.copy(new File(FIXTURES, "sample.tif").toPath(), tiffCopy.toPath());

        ImageFileTest test = new ImageFileTest(tiffCopy, config);
        test.run(mockEyes);

        // TIFF is converted to a PNG in the same directory; check is called with the converted name.
        verify(mockEyes).check(eq("sample.png"), ArgumentMatchers.any());
        verify(mockEyes).close(false);
    }

    @Test
    public void forcedName_overridesTestName() {
        config.forcedName = "ForcedTestName";
        ImageFileTest test = new ImageFileTest(new File(FIXTURES, "sample.png"), config);
        assertEquals("ForcedTestName", test.name());
    }

    @Test
    public void isEmpty_returnsFalse() {
        ImageFileTest test = new ImageFileTest(new File(FIXTURES, "sample.png"), config);
        assertFalse(test.isEmpty());
    }

    @Test
    public void viewport_defaultsToNull_withNoImage() {
        ImageFileTest test = new ImageFileTest(new File(FIXTURES, "sample.png"), config);
        assertNull(test.viewport());
    }

    @Test
    public void viewport_usesConfigWhenSet() {
        config.setViewport("800x600");
        ImageFileTest test = new ImageFileTest(new File(FIXTURES, "sample.png"), config);
        RectangleSize vp = test.viewport();
        assertEquals(800, vp.getWidth());
        assertEquals(600, vp.getHeight());
    }
}
