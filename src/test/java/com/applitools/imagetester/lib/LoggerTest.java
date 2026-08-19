package com.applitools.imagetester.lib;

import com.applitools.eyes.EyesException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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

    private static String report(Throwable e) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Logger logger = new Logger(new PrintStream(buffer, true), false);
        logger.reportException(e);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
