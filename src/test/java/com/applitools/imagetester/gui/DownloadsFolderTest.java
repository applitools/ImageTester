package com.applitools.imagetester.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.UnaryOperator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DownloadsFolderTest {

    private static final String REG_OUTPUT =
            "\r\nHKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders\r\n"
            + "    {374DE290-123F-4565-9164-39C4925E467B}    REG_EXPAND_SZ    %USERPROFILE%\\MyDownloads\r\n\r\n";

    private static final UnaryOperator<String> ENV =
            name -> "USERPROFILE".equals(name) ? "C:\\Users\\test" : null;

    @Test
    public void windowsResolvesFromRegistryAndExpandsUserprofile() {
        Path p = DownloadsFolder.resolve("Windows 11", ENV, cmd -> REG_OUTPUT);
        assertEquals(Paths.get("C:\\Users\\test\\MyDownloads"), p);
    }

    @Test
    public void windowsFallsBackToHomeDownloadsWhenRegQueryFails() {
        Path p = DownloadsFolder.resolve("Windows 11", ENV, cmd -> { throw new IOException("blocked"); });
        assertEquals(Paths.get(System.getProperty("user.home"), "Downloads"), p);
    }

    @Test
    public void windowsFallsBackWhenRegistryValueMissing() {
        Path p = DownloadsFolder.resolve("Windows 11", ENV, cmd -> "garbage with no value line");
        assertEquals(Paths.get(System.getProperty("user.home"), "Downloads"), p);
    }

    @Test
    public void nonWindowsUsesHomeDownloads() {
        Path p = DownloadsFolder.resolve("Mac OS X", ENV, cmd -> { throw new AssertionError("reg must not run off-Windows"); });
        assertEquals(Paths.get(System.getProperty("user.home"), "Downloads"), p);
    }
}
