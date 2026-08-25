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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestBase {

    private static final String JAR_PATH = System.getProperty("jar");
    // Matches Logger.reportResult rows ("[Passed], Existing test [...]") and nothing
    // else — progress counters ("[1/5]") and debug prefixes don't carry a status.
    private static final Pattern RESULT_LINE = Pattern.compile("\\[(Passed|Failed|Unresolved)\\],");

    /**
     * Runs ImageTester and asserts it exited 0 AND produced at least one test result
     * row. Exit code alone once kept the -bb scenario green for months while every
     * run silently failed — a clean exit with zero results is a failure here.
     */
    public String runImageTester(String args) {
        String output = run(args);
        assertTrue("Run exited 0 but produced no test result line — a silent no-op run. Output:\n" + output,
                containsResultLine(output));
        return output;
    }

    /** For modes that legitimately upload nothing (e.g. standalone -rwo): exit 0 is the whole contract. */
    public String runImageTesterExpectingNoResults(String args) {
        return run(args);
    }

    static boolean containsResultLine(String output) {
        return RESULT_LINE.matcher(output).find();
    }

    private String run(String args) {
        if (JAR_PATH != null) return runAsJar(args);
        // Listener capture, not System.setOut: the SDK and surefire both juggle stdout,
        // which made stream-level capture drop output for every run after the first.
        StringBuilder captured = new StringBuilder();
        Logger logger = new Logger();
        logger.addListener(line -> {
            synchronized (captured) { captured.append(line); }
        });
        int exitCode = ImageTester.run(args.split(" "), logger);
        assertEquals("ImageTester exited with non-zero code", 0, exitCode);
        synchronized (captured) { return captured.toString(); }
    }

    private String runAsJar(String args) {
        try {
            String command = String.format("java -jar %s %s", JAR_PATH, args);
            ProcessBuilder pb = new ProcessBuilder(command.split(" "));
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            // Drain concurrently (echoing for CI logs) — a full pipe buffer would deadlock waitFor.
            StringBuilder captured = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println(line);
                        synchronized (captured) { captured.append(line).append('\n'); }
                    }
                } catch (IOException ignored) { /* stream closes with the process */ }
            }, "jar-output-reader");
            reader.start();
            // Compare mode renders 2x20 PDF pages inside the spawned JVM — 120s flakes.
            boolean finished = proc.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                throw new RuntimeException("JAR process timed out after 300 seconds");
            }
            reader.join(10_000);
            assertEquals("JAR exited with non-zero code", 0, proc.exitValue());
            synchronized (captured) { return captured.toString(); }
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
