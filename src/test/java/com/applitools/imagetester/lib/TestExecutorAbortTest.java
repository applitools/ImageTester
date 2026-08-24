package com.applitools.imagetester.lib;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.EyesException;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.TestObjects.TestBase;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestExecutorAbortTest {

    @Test
    public void join_workerBuildFailure_countsAsTestError() {
        Config config = new Config();
        config.logger = new Logger(new java.io.PrintStream(new ByteArrayOutputStream(), true), false);

        EyesFactory factory = mock(EyesFactory.class);
        when(factory.build()).thenThrow(
                new RuntimeException("Parent Branches (pb) should be combined with branches (br)."));
        TestBase test = mock(TestBase.class);
        when(test.name()).thenReturn("doc.pdf");

        TestExecutor executor = new TestExecutor(1, factory, config);
        executor.enqueue(test, null);
        executor.join();

        org.junit.Assert.assertEquals(1, config.testErrorCount.get());
    }

    @Test
    public void join_abortRethrowAfterFailedTest_printsNoDuplicateError() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Config config = new Config();
        config.logger = new Logger(new PrintStream(buffer, true), false);
        config.shouldThrowException = false;

        EyesFactory factory = mock(EyesFactory.class);
        Eyes eyes = mock(Eyes.class);
        when(factory.build()).thenReturn(eyes);
        when(eyes.getBatch()).thenReturn(new BatchInfo("t"));
        doThrow(new EyesException("Request \"openEyes\" failed due to unexpected status Bad Request(400)"))
                .when(eyes).abortIfNotClosed();

        TestBase test = mock(TestBase.class);
        when(test.name()).thenReturn("doc.pdf");
        when(test.runSafe(any())).thenReturn(null);

        TestExecutor executor = new TestExecutor(1, factory, config);
        executor.enqueue(test, null);
        executor.join();

        String output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("the failure was already reported by runSafe; the abort rethrow must not be reported again: "
                + output, output.contains("EyesException"));
    }
}
