package com.applitools.imagetester.lib;

import com.applitools.eyes.EyesException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoggerTest {

    private static final String PRIVATE_CLOUD_HINT = "Are you testing against a private cloud?";

    @Test
    public void reportException_handlesVariousExceptionTypes() {
        Logger logger = new Logger();
        logger.reportException(new Exception());
        logger.reportException(new IOException());
        logger.reportException(new UnsatisfiedLinkError());
        logger.reportException(new FileNotFoundException());
    }

    @Test
    public void reportException_eyesException_printsMessageWithoutClassName() {
        String output = report(new EyesException("The provided API key ab****cd is invalid."));
        assertTrue(output.contains("The provided API key ab****cd is invalid."));
        assertFalse(output.contains("Unexpected error"));
        assertFalse(output.contains("com.applitools.eyes.EyesException"));
    }

    @Test
    public void reportException_invalidApiKey_suggestsCheckingServerUrl() {
        String output = report(new EyesException("The provided API key ab****cd is invalid. Please check your API key and try again."));
        assertTrue(output.contains(PRIVATE_CLOUD_HINT));
    }

    @Test
    public void reportException_otherEyesException_omitsPrivateCloudHint() {
        String output = report(new EyesException("Failed closing test"));
        assertFalse(output.contains(PRIVATE_CLOUD_HINT));
    }

    @Test
    public void reportException_unexpectedError_pointsToSupport() {
        String output = report(new IllegalStateException("boom"));
        assertTrue(output.contains("support@applitools.com"));
    }

    @Test
    public void reportException_unexpectedError_omitsExceptionClassName() {
        String output = report(new IllegalStateException("boom"));
        assertFalse(output.contains("java.lang.IllegalStateException"));
    }

    @Test
    public void reportResult_withoutTestResult_printsNothing() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Logger logger = new Logger(new PrintStream(buffer, true), false);
        logger.reportResult(new ExecutorResult(null, 0));
        assertEquals("", buffer.toString());
    }

    @Test
    public void reportResultAccessibility_withoutTestResult_printsNothing() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Logger logger = new Logger(new PrintStream(buffer, true), false);
        logger.reportResultAccessibility(new ExecutorResult(null, 0));
        assertEquals("", buffer.toString());
    }

    private static final String OPEN_EYES_400 =
            "Request \"openEyes\" [1--b7519ef5#3] that was sent to the address "
                    + "\"[GET]https://eyesapi.applitools.com/api/sessions/running/XYZ/started-status\" "
                    + "failed due to unexpected status Bad Request(400)";

    @Test
    public void reportException_openEyes400WithBaselineBranchContext_namesTheBranch() {
        String output = reportWithBranchContext("feature-x", new EyesException(OPEN_EYES_400));
        assertTrue(output.contains("baseline branch 'feature-x'"));
    }

    @Test
    public void reportException_openEyes400WithBaselineBranchContext_pointsAtTheOption() {
        String output = reportWithBranchContext("feature-x", new EyesException(OPEN_EYES_400));
        assertTrue(output.contains("-bb"));
    }

    @Test
    public void reportException_openEyes400WithBaselineBranchContext_alsoPointsToSupport() {
        String output = reportWithBranchContext("feature-x", new EyesException(OPEN_EYES_400));
        assertTrue(output.contains("If the problem persists"));
    }

    @Test
    public void reportException_invalidApiKey_alsoPointsToSupport() {
        String output = report(new EyesException("The provided API key ab****cd is invalid."));
        assertTrue(output.contains("If the problem persists"));
    }

    @Test
    public void reportException_fileNotFound_pointsToSupport() {
        String output = report(new FileNotFoundException("missing.pdf"));
        assertTrue(output.contains("If the problem persists"));
    }

    @Test
    public void reportException_ioError_pointsToSupport() {
        String output = report(new IOException("locked file"));
        assertTrue(output.contains("If the problem persists"));
    }

    @Test
    public void reportException_executionError_pointsToSupport() {
        String output = report(new java.util.concurrent.ExecutionException(new IllegalStateException("boom")));
        assertTrue(output.contains("If the problem persists"));
    }

    @Test
    public void reportException_openEyes400WithoutBranchContext_keepsSupportHint() {
        String output = report(new EyesException(OPEN_EYES_400));
        assertTrue(output.contains("If the problem persists"));
    }

    @Test
    public void reportException_nonOpenEyesFailureWithBranchContext_omitsBranchHint() {
        String output = reportWithBranchContext("feature-x", new EyesException("Failed closing test"));
        assertFalse(output.contains("feature-x"));
    }

    private static final String INVALID_KEY_WITH_STALE_LINK =
            "The provided API key we****df is invalid. Please check your API key and try again. "
                    + "For more details about obtaining or managing your API key, "
                    + "see https://applitools.com/docs/Default.html#cshid=api";

    @Test
    public void reportException_staleSdkDocLink_isRewrittenToTheLiveUrl() {
        String output = report(new EyesException(INVALID_KEY_WITH_STALE_LINK));
        assertTrue(output.contains("https://applitools.com/docs/topics/overview/obtain-api-key.html"));
    }

    @Test
    public void reportException_staleSdkDocLink_doesNotLeakTheDeadUrl() {
        String output = report(new EyesException(INVALID_KEY_WITH_STALE_LINK));
        assertFalse(output.contains("Default.html#cshid=api"));
    }

    private static String reportWithBranchContext(String branchName, Throwable e) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Logger logger = new Logger(new PrintStream(buffer, true), false);
        logger.setBaselineBranchContext(branchName);
        logger.reportException(e);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String report(Throwable e) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Logger logger = new Logger(new PrintStream(buffer, true), false);
        logger.reportException(e);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
