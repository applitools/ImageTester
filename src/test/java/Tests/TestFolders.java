package Tests;

import com.yanirta.Suite;
import com.yanirta.TestObjects.FolderTest;
import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.RectangleSize;
import infra.TestBase;
import com.yanirta.lib.Config;
import com.yanirta.lib.EyesFactory;
import com.yanirta.lib.Logger;
import com.yanirta.lib.TestExecutor;
import org.junit.Test;

import java.io.File;

public class TestFolders extends TestBase {
    @Test
    public void testDefThreadFolderA() throws Exception {
        Config conf = new Config();
        conf.appName = "FolderTestsApp";
        conf.viewport = new RectangleSize(1, 1);
        FolderTest folderTest = new FolderTest(new File("src/test/TestData/a"), conf);
        EyesFactory factory = new EyesFactory("1.0", conf.logger).apiKey(System.getenv("APPLITOOLS_API_KEY"));
        folderTest.run(factory.build());
    }

    @Test
    public void testThreadCTXFolderA() {
        Config conf = new Config();
        conf.appName = "FolderTestsApp";
        conf.viewport = new RectangleSize(1, 1);
        FolderTest folderTest = new FolderTest(new File("src/test/TestData/a"), conf);
        EyesFactory factory = new EyesFactory("1.0", conf.logger).apiKey(System.getenv("APPLITOOLS_API_KEY"));
        TestExecutor executor = new TestExecutor(3, factory, conf);
        executor.enqueue(folderTest, null);
        executor.join();
    }

    @Test
    public void testMultiHeirarchyThreadsTest() {
        runWhitebox("FolderTestsApp", "src/test/TestData/b");
    }

    @Test
    public void testSingleImage() {
        runWhitebox("FolderTestsApp", "TestData/b/c/googleforgoogle.png");
    }

    @Test(expected = RuntimeException.class)
    public void testPDFTest() {
//        ImageIO.scanForPlugins();
//        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JBIG2");
//        suiteRun("FolderTestsApp", "src/test/TestData/b/c/JustPDF/Lorem2.pdf");
//        suiteRun("FolderTestsApp", "src/test/TestData/b/c/JustPDF/Lorem1.pdf");
        runWhitebox("Split_Barcode_rotate", "/Users/yanir/Downloads/Split_Barcode_rotate_001.pdf");
    }

    @Test
    public void testPDFPageRange() {
    }

    @Test
    public void testFlatBatch() {
        Config conf = new Config();
        conf.appName = "FlatbatchApp";
        conf.viewport = new RectangleSize(1, 1);
        conf.logger = new Logger(System.out, true);
        conf.flatBatch = new BatchInfo("Flat Batch");
        EyesFactory factory = new EyesFactory("1.0.0.1", conf.logger).apiKey(System.getenv("APPLITOOLS_API_KEY"));
        TestExecutor executor = new TestExecutor(3, factory, conf);
        Suite suite = Suite.create(new File("src/test/TestData/b"), conf, executor);
        suite.run();
    }

    @Test
    public void testPostscript() {
        runWhitebox("FolderTestsApp", "src/test/TestData/b/c/JustPostscript/Lorem2.ps");
    }
}
