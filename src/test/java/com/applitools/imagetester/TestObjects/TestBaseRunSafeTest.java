package com.applitools.imagetester.TestObjects;

import com.applitools.eyes.EyesException;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class TestBaseRunSafeTest {

    private static final String OPEN_EYES_400 =
            "Request \"openEyes\" [1--b7519ef5#3] that was sent to the address "
                    + "\"[GET]https://eyesapi.applitools.com/api/sessions/running/XYZ/started-status\" "
                    + "failed due to unexpected status Bad Request(400)";

    @Test
    public void runSafe_abortRethrowAfterFailedRun_reportsTheFailureOnlyOnce() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Config config = new Config();
        config.logger = new Logger(new PrintStream(buffer, true), false);
        Eyes eyes = mock(Eyes.class);
        doThrow(new EyesException(OPEN_EYES_400)).when(eyes).abortIfNotClosed();

        failingTest(config).runSafe(eyes);

        String output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(1, countOccurrences(output, "Bad Request(400)"));
    }

    @Test
    public void runSafe_runFailure_incrementsTestErrorCount() {
        Config config = new Config();
        config.logger = new Logger(new PrintStream(new ByteArrayOutputStream(), true), false);

        failingTest(config).runSafe(mock(Eyes.class));

        assertEquals(1, config.testErrorCount.get());
    }

    private static TestBase failingTest(Config config) {
        return new TestBase(new File("dummy.png"), config) {
            @Override
            public TestResults run(Eyes eyes) {
                throw new EyesException(OPEN_EYES_400);
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length()))
            count++;
        return count;
    }
}
