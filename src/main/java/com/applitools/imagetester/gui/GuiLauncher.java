package com.applitools.imagetester.gui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public final class GuiLauncher {

    private GuiLauncher() {}

    public static void open(String url) {
        if (tryDesktop(url)) return;
        tryShell(url);
    }

    private static boolean tryDesktop(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void tryShell(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win"))       new ProcessBuilder("cmd", "/c", "start", "", url).start();
            else if (os.contains("mac"))  new ProcessBuilder("open", url).start();
            else                          new ProcessBuilder("xdg-open", url).start();
        } catch (IOException e) {
            System.err.println("Could not open browser. Please open " + url + " manually.");
        }
    }
}
