package infra;

import com.applitools.eyes.RectangleSize;
import com.applitools.imagetester.ImageTester;
import com.applitools.imagetester.Suite;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.TestExecutor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class TestBase {

    private static final String JAR_PATH = System.getProperty("jar");

    public void runImageTester(String args) {
        if (JAR_PATH != null) {
            runAsJar(args);
        } else {
            ImageTester.run(args.split(" "));
        }
    }

    private void runAsJar(String args) {
        try {
            String command = String.format("java -jar %s %s", JAR_PATH, args);
            ProcessBuilder pb = new ProcessBuilder(command.split(" "));
            pb.redirectErrorStream(true);
            pb.inheritIO();
            Process proc = pb.start();
            boolean finished = proc.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                throw new RuntimeException("JAR process timed out after 120 seconds");
            }
            assertEquals("JAR exited with non-zero code", 0, proc.exitValue());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Config config(String appName) {
        Config conf = new Config();
        conf.appName = appName;
        conf.viewport = new RectangleSize(1, 1);
        conf.logger = new Logger(System.out, true);
        return conf;
    }

    public EyesFactory eyesFactory(Config config) {
        return new EyesFactory("1.0", config.logger)
                .apiKey(System.getenv("APPLITOOLS_API_KEY"));
    }

    public void runWhitebox(String app, String file) {
        Config conf = config(app);
        EyesFactory factory = eyesFactory(conf);
        TestExecutor executor = new TestExecutor(3, factory, conf);
        Suite suite = Suite.create(new File(file), conf, executor);
        suite.run();
    }
}
