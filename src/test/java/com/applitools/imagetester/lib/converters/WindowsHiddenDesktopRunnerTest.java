package com.applitools.imagetester.lib.converters;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class WindowsHiddenDesktopRunnerTest {

    @Test
    public void simpleArgumentIsNotQuoted() {
        assertEquals("--headless", WindowsHiddenDesktopRunner.quoteArgument("--headless"));
    }

    @Test
    public void argumentWithSpaceIsQuoted() {
        assertEquals("\"Program Files\"", WindowsHiddenDesktopRunner.quoteArgument("Program Files"));
    }

    @Test
    public void embeddedQuoteIsEscaped() {
        assertEquals("\"a\\\"b\"", WindowsHiddenDesktopRunner.quoteArgument("a\"b"));
    }

    @Test
    public void trailingBackslashesBeforeClosingQuoteAreDoubled() {
        // Input "a b\\" needs quoting (contains space); per CommandLineToArgvW
        // rules trailing backslashes before the closing quote must be doubled
        // so they aren't interpreted as escapes.
        assertEquals("\"a b\\\\\\\\\"", WindowsHiddenDesktopRunner.quoteArgument("a b\\\\"));
    }

    @Test
    public void commandLineJoinsWithSpacesAndQuotesIndividually() {
        String line = WindowsHiddenDesktopRunner.buildCommandLine(Arrays.asList(
                "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                "--headless",
                "--convert-to", "pdf"));
        assertEquals(
                "\"C:\\Program Files\\LibreOffice\\program\\soffice.exe\" --headless --convert-to pdf",
                line);
    }
}
