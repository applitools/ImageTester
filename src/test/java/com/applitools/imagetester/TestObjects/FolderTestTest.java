package com.applitools.imagetester.TestObjects;

import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class FolderTestTest {

    private static final String FIXTURES = "src/test/resources/fixtures";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Config createConfig() {
        Config config = new Config();
        config.appName = "TestApp";
        config.logger = new Logger();
        return config;
    }

    private File[] getSteps(FolderTest test) throws Exception {
        Field f = FolderTest.class.getDeclaredField("steps_");
        f.setAccessible(true);
        return (File[]) f.get(test);
    }

    @Test
    public void folderWithImages_containsCorrectSteps() throws Exception {
        File folder = tempFolder.newFolder("imgs");
        Files.copy(new File(FIXTURES, "sample.png").toPath(), new File(folder, "c.png").toPath());
        Files.copy(new File(FIXTURES, "sample.png").toPath(), new File(folder, "a.png").toPath());
        Files.copy(new File(FIXTURES, "sample.png").toPath(), new File(folder, "b.png").toPath());

        FolderTest test = new FolderTest(folder, createConfig());
        File[] steps = getSteps(test);

        assertEquals(3, steps.length);
        assertFalse(test.isEmpty());
    }

    @Test
    public void folderWithImages_sortedAlphabetically() throws Exception {
        File folder = tempFolder.newFolder("sorted");
        Files.copy(new File(FIXTURES, "sample.png").toPath(), new File(folder, "c.png").toPath());
        Files.copy(new File(FIXTURES, "sample.png").toPath(), new File(folder, "a.png").toPath());
        Files.copy(new File(FIXTURES, "sample.png").toPath(), new File(folder, "b.png").toPath());

        FolderTest test = new FolderTest(folder, createConfig());
        File[] steps = getSteps(test);

        assertEquals("a.png", steps[0].getName());
        assertEquals("b.png", steps[1].getName());
        assertEquals("c.png", steps[2].getName());
    }

    @Test
    public void folderWithMixedFiles_onlyImagesIncluded() throws Exception {
        File folder = tempFolder.newFolder("mixed");
        Files.copy(new File(FIXTURES, "sample.png").toPath(), new File(folder, "image.png").toPath());
        new File(folder, "document.pdf").createNewFile();
        new File(folder, "readme.txt").createNewFile();

        FolderTest test = new FolderTest(folder, createConfig());
        File[] steps = getSteps(test);

        assertEquals(1, steps.length);
        assertEquals("image.png", steps[0].getName());
    }

    @Test
    public void emptyFolder_isEmpty() throws Exception {
        File folder = tempFolder.newFolder("empty");
        FolderTest test = new FolderTest(folder, createConfig());
        assertTrue(test.isEmpty());
    }

    @Test
    public void regexFilter_excludesNonMatchingImages() throws Exception {
        File folder = tempFolder.newFolder("regex");
        Files.copy(new File(FIXTURES, "sample.png").toPath(), new File(folder, "report_01.png").toPath());
        Files.copy(new File(FIXTURES, "sample.png").toPath(), new File(folder, "photo.png").toPath());

        Config config = createConfig();
        config.regexFileNameFilter = "report_.*";
        FolderTest test = new FolderTest(folder, config);
        File[] steps = getSteps(test);

        assertEquals(1, steps.length);
        assertEquals("report_01.png", steps[0].getName());
    }

    @Test(expected = RuntimeException.class)
    public void nonDirectory_throws() {
        File file = new File(FIXTURES, "sample.png");
        new FolderTest(file, createConfig());
    }
}
