package com.applitools.imagetester.gui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LogRedactorTest {

    @Test
    public void redactsApiKeySubstring() {
        String redacted = LogRedactor.redact("uploading with key sk_live_AbCdEf123456", "sk_live_AbCdEf123456");
        assertEquals("uploading with key ***", redacted);
    }

    @Test
    public void redactsApiKeyOccurringMultipleTimes() {
        String redacted = LogRedactor.redact("key=sk_x sk_x again", "sk_x");
        assertEquals("key=*** *** again", redacted);
    }

    @Test
    public void redactsProxyUrlCredentials() {
        String redacted = LogRedactor.redact("Using proxy https://alice:s3cret@proxy.local:8080", null);
        assertEquals("Using proxy https://***:***@proxy.local:8080", redacted);
    }

    @Test
    public void redactsBothApiKeyAndProxyInSameLine() {
        String redacted = LogRedactor.redact("key=sk_x via http://u:p@host", "sk_x");
        assertEquals("key=*** via http://***:***@host", redacted);
    }

    @Test
    public void returnsLineUnchangedWhenNoSecrets() {
        String s = "ordinary log line";
        assertEquals(s, LogRedactor.redact(s, "sk_unused"));
    }

    @Test
    public void emptyOrNullApiKeyIsHarmless() {
        assertEquals("hello", LogRedactor.redact("hello", null));
        assertEquals("hello", LogRedactor.redact("hello", ""));
    }
}
