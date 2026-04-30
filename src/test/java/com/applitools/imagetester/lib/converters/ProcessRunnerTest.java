package com.applitools.imagetester.lib.converters;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class ProcessRunnerTest {

    @Test
    public void isWindowsMatchesOsName() {
        boolean expected = System.getProperty("os.name", "").toLowerCase().contains("win");
        assertTrue(expected == ProcessRunner.isWindows());
    }

    @Test
    public void forPlatformReturnsDefaultOnNonWindows() {
        assumeTrue(!ProcessRunner.isWindows());
        ProcessRunner runner = ProcessRunner.forPlatform();
        assertNotNull(runner);
        assertTrue("expected Default runner on non-Windows, got " + runner.getClass().getName(),
                runner instanceof ProcessRunner.Default);
    }

    @Test
    public void forPlatformReturnsHiddenDesktopRunnerOnWindows() {
        assumeTrue(ProcessRunner.isWindows());
        ProcessRunner runner = ProcessRunner.forPlatform();
        assertNotNull(runner);
        assertTrue("expected WindowsHiddenDesktopRunner on Windows, got " + runner.getClass().getName(),
                runner instanceof WindowsHiddenDesktopRunner);
    }
}
