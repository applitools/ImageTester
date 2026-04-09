package com.applitools.imagetester;

import com.applitools.imagetester.BatchObjects.BatchBase;
import com.applitools.imagetester.TestObjects.FolderTest;
import com.applitools.imagetester.TestObjects.ImageFileTest;
import com.applitools.imagetester.TestObjects.PdfFileTest;
import com.applitools.imagetester.TestObjects.TestBase;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.TestExecutor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class SuiteTest {

    private static final String FIXTURES = "src/test/resources/fixtures";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Config config;
    private TestExecutor executor;

    @Before
    public void setUp() {
        config = new Config();
        config.appName = "TestApp";
        config.logger = new Logger();
        EyesFactory factory = new EyesFactory("test", config.logger).apiKey("fake-key");
        executor = new TestExecutor(1, factory, config);
    }

    @SuppressWarnings("unchecked")
    private List<TestBase> getTests(Suite suite) throws Exception {
        Field f = Suite.class.getDeclaredField("tests_");
        f.setAccessible(true);
        return (List<TestBase>) f.get(suite);
    }

    @SuppressWarnings("unchecked")
    private List<BatchBase> getBatches(Suite suite) throws Exception {
        Field f = Suite.class.getDeclaredField("batches_");
        f.setAccessible(true);
        return (List<BatchBase>) f.get(suite);
    }

    @Test
    public void create_singleImage_createsImageFileTest() throws Exception {
        File imageFile = new File(FIXTURES, "sample.png");
        Suite suite = Suite.create(imageFile, config, executor);
        List<TestBase> tests = getTests(suite);
        assertEquals(1, tests.size());
        assertTrue(tests.get(0) instanceof ImageFileTest);
    }

    @Test
    public void create_singleImage_forcesSplitSteps() throws Exception {
        File imageFile = new File(FIXTURES, "sample.png");
        assertFalse(config.splitSteps);
        Suite.create(imageFile, config, executor);
        assertTrue(config.splitSteps);
    }

    @Test
    public void create_singlePdf_createsPdfFileTest() throws Exception {
        config.splitSteps = false;
        File pdfFile = new File(FIXTURES, "valid-2-page.pdf");
        Suite suite = Suite.create(pdfFile, config, executor);
        List<TestBase> tests = getTests(suite);
        assertEquals(1, tests.size());
        assertTrue(tests.get(0) instanceof PdfFileTest);
    }

    @Test
    public void create_folderWithImages_createsFolderTest() throws Exception {
        File folder = tempFolder.newFolder("images");
        Files.copy(new File(FIXTURES, "sample.png").toPath(),
                new File(folder, "a.png").toPath());
        Files.copy(new File(FIXTURES, "sample.png").toPath(),
                new File(folder, "b.png").toPath());

        Suite suite = Suite.create(folder, config, executor);
        List<TestBase> tests = getTests(suite);
        assertEquals(1, tests.size());
        assertTrue(tests.get(0) instanceof FolderTest);
    }

    @Test
    public void create_emptyFolder_noTests() throws Exception {
        File folder = tempFolder.newFolder("empty");
        Suite suite = Suite.create(folder, config, executor);
        List<TestBase> tests = getTests(suite);
        assertTrue(tests.isEmpty());
    }

    @Test(expected = RuntimeException.class)
    public void create_nonExistentPath_throws() {
        Suite.create(new File("/nonexistent/path/file.png"), config, executor);
    }

    @Test
    public void create_regexFilter_excludesNonMatchingFiles() throws Exception {
        File folder = tempFolder.newFolder("filtered");
        Files.copy(new File(FIXTURES, "sample.png").toPath(),
                new File(folder, "report_2024.png").toPath());
        Files.copy(new File(FIXTURES, "sample.png").toPath(),
                new File(folder, "photo.png").toPath());

        config.regexFileNameFilter = "report_.*";
        Suite suite = Suite.create(folder, config, executor);
        List<TestBase> tests = getTests(suite);
        assertEquals(1, tests.size());
        FolderTest folderTest = (FolderTest) tests.get(0);
        assertFalse(folderTest.isEmpty());
    }
}
