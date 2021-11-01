package infra;

import com.applitools.eyes.RectangleSize;
import com.yanirta.ImageTester;
import com.yanirta.Suite;
import com.yanirta.lib.Config;
import com.yanirta.lib.EyesFactory;
import com.yanirta.lib.Logger;
import com.yanirta.lib.TestExecutor;

import java.io.File;

public class TestBase {
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

    public void runBlackBox(String folder, String otherArgs) {
        runBlackBox(String.format("-f %s %s", folder, otherArgs));
    }

    public void runBlackBox(String cmd) {
        ImageTester.main(cmd.split(" "));
    }
}
