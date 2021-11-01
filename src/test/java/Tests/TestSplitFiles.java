package Tests;

import com.applitools.eyes.RectangleSize;
import com.yanirta.Suite;
import com.yanirta.lib.Config;
import com.yanirta.lib.EyesFactory;
import com.yanirta.lib.Logger;
import com.yanirta.lib.TestExecutor;
import org.junit.Test;

import java.io.File;

public class TestSplitFiles {

    @Test
    public void testPDF() {
        Config conf = new Config();
        conf.appName = "FilesTestsApp";
        conf.viewport = new RectangleSize(1, 1);
        conf.splitSteps = true;
        conf.logger = new Logger(System.out, true);

        EyesFactory factory = new EyesFactory("1.0", conf.logger).apiKey(System.getenv("APPLITOOLS_API_KEY"));
        TestExecutor executor = new TestExecutor(3, factory, conf);
        Suite suite = Suite.create(new File("src/test/TestData/b/c/JustPDF/Lorem2.pdf"), conf, executor);
        suite.run();
    }

    @Test
    public void testPDFSpecificPages() {
    }

    @Test
    public void testPostscript() {
        Config conf = new Config();
        conf.appName = "FilesTestsApp";
        conf.viewport = new RectangleSize(1, 1);
        conf.splitSteps = true;
        conf.logger.setDebug();

        EyesFactory factory = new EyesFactory("1.0", conf.logger).apiKey(System.getenv("APPLITOOLS_API_KEY"));
        TestExecutor executor = new TestExecutor(3, factory, conf);
        Suite suite = Suite.create(new File("src/test/TestData/b/c/JustPostscript/Lorem2.ps"), conf, executor);
        suite.run();
    }

    @Test
    public void testFolder() {
        Config conf = new Config();
        conf.appName = "FilesTestsApp";
        conf.viewport = new RectangleSize(1, 1);
        conf.splitSteps = true;
        conf.logger.setDebug();

        EyesFactory factory = new EyesFactory("1.0", conf.logger).apiKey(System.getenv("APPLITOOLS_API_KEY"));
        TestExecutor executor = new TestExecutor(3, factory, conf);
        Suite suite = Suite.create(new File("src/test/TestData/b/c/d"), conf, executor);
        suite.run();
    }

    @Test
    public void testPostscriptSpecificPages() {
    }
}
