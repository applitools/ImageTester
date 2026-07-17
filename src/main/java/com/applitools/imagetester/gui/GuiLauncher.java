package com.applitools.imagetester.gui;

import java.awt.Desktop;
import java.awt.Image;
import java.awt.Taskbar;
import java.io.IOException;
import java.net.URI;

import javax.imageio.ImageIO;

public final class GuiLauncher {

    private GuiLauncher() {}

    public static void open(String url) {
        if (tryDesktop(url)) return;
        tryShell(url);
    }

    /**
     * Sets the app's Dock/taskbar icon when launched directly via `java -jar ... --gui`
     * (jpackage-built installers get their icon from --icon at packaging time instead).
     */
    public static void setDockIcon() {
        try {
            if (!Taskbar.isTaskbarSupported()) return;
            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return;
            Image icon = ImageIO.read(GuiLauncher.class.getResource("/icons/app-icon.png"));
            if (icon != null) taskbar.setIconImage(icon);
        } catch (Throwable ignored) {
            // Dock icon is cosmetic; never let it block GUI startup.
        }
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
