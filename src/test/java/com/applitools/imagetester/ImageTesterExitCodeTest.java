package com.applitools.imagetester;

import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.converters.SkipTracker;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;

public class ImageTesterExitCodeTest {

    @Test
    public void computeExitCode_withTestErrors_returnsOne() {
        Config config = new Config();
        config.testErrorCount.incrementAndGet();

        assertEquals(1, ImageTester.computeExitCode(config, silentLogger()));
    }

    @Test
    public void computeExitCode_testErrorsTakePrecedenceOverSkips_returnsOne() {
        Config config = new Config();
        config.testErrorCount.incrementAndGet();
        config.skipTracker.record(new File("a.ps"), SkipTracker.REASON_POSTSCRIPT_XPS_UNSUPPORTED);

        assertEquals(1, ImageTester.computeExitCode(config, silentLogger()));
    }

    private static Logger silentLogger() {
        return new Logger(new PrintStream(new ByteArrayOutputStream(), true), false);
    }
}
