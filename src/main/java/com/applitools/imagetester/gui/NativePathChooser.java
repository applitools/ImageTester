package com.applitools.imagetester.gui;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public final class NativePathChooser {

    // The Windows common dialog is not an AWT window: it ignores setAlwaysOnTop and doesn't
    // inherit the anchor's topmost state, so it opens behind the browser (focus-stealing
    // prevention blocks a background process from raising it). Swing dialogs inherit topmost
    // from the anchor, so Windows routes files through JFileChooser like folders.
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");

    private NativePathChooser() {}

    /**
     * Asynchronously initialize AWT/Swing on a background thread so the first user click on
     * "Choose file"/"Choose folder" isn't delayed by cold-start: Toolkit native init, Swing UI
     * delegate loading, and (on Windows) the FileSystemView drive scan all happen eagerly here.
     */
    public static void prewarm() {
        Thread t = new Thread(() -> {
            try {
                java.awt.Toolkit.getDefaultToolkit();
                javax.swing.filechooser.FileSystemView.getFileSystemView().getRoots();
                SwingUtilities.invokeAndWait(JFileChooser::new);
            } catch (Throwable ignored) { /* prewarm is best-effort */ }
        }, "NativePathChooser-prewarm");
        t.setDaemon(true);
        t.start();
    }

    public static String chooseFile() { return choose(false, null); }
    public static String chooseFolder() { return choose(true, null); }
    public static String chooseFile(String startPath) { return choose(false, startPath); }
    public static String chooseFolder(String startPath) { return choose(true, startPath); }

    /**
     * macOS opens the native NSOpenPanel behind the browser unless the app is activated first;
     * the always-on-top anchor frame is enough for Swing dialogs but ignored by native panels.
     */
    private static void requestForeground() {
        try {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (desktop.isSupported(java.awt.Desktop.Action.APP_REQUEST_FOREGROUND)) {
                desktop.requestForeground(true);
            }
        } catch (Throwable ignored) { /* focus is best-effort; the dialog still opens */ }
    }

    /** Returns an existing directory derived from the hint (the path itself if a dir, else its parent), or null. */
    private static File resolveStartDir(String hint) {
        if (hint == null || hint.isEmpty()) return null;
        File f = new File(hint);
        if (f.isDirectory()) return f;
        File parent = f.getParentFile();
        return (parent != null && parent.isDirectory()) ? parent : null;
    }

    private static String choose(boolean folder, String startPath) {
        System.out.println("[NativePathChooser] choose folder=" + folder + " edt=" + SwingUtilities.isEventDispatchThread() + " start=" + startPath);
        AtomicReference<String> result = new AtomicReference<>();
        File startDir = resolveStartDir(startPath);
        Runnable task = () -> {
            // Anchor a 1px, always-on-top, focusable frame so the file dialog inherits
            // its z-order and comes to the foreground — without a real owner the OS leaves the
            // dialog behind the browser window that just made this HTTP call. Centered on screen
            // so the dialog (which centers on its parent) opens centered, not in a corner.
            JFrame anchor = new JFrame();
            anchor.setUndecorated(true);
            anchor.setSize(1, 1);
            anchor.setLocationRelativeTo(null);
            anchor.setAlwaysOnTop(true);
            anchor.setType(java.awt.Window.Type.UTILITY);
            anchor.setVisible(true);
            anchor.toFront();
            requestForeground();
            try {
                if (folder || IS_WINDOWS) {
                    JFileChooser fc = new JFileChooser();
                    fc.setFileSelectionMode(folder ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
                    fc.setDialogTitle(folder ? "Choose source folder" : "Choose source file");
                    if (startDir != null) fc.setCurrentDirectory(startDir);
                    System.out.println("[NativePathChooser] showing JFileChooser");
                    int rc = fc.showOpenDialog(anchor);
                    System.out.println("[NativePathChooser] JFileChooser returned " + rc);
                    if (rc == JFileChooser.APPROVE_OPTION) {
                        result.set(fc.getSelectedFile().getAbsolutePath());
                    }
                } else {
                    FileDialog fd = new FileDialog((Frame) anchor, "Choose source file", FileDialog.LOAD);
                    fd.setAlwaysOnTop(true);
                    if (startDir != null) fd.setDirectory(startDir.getAbsolutePath());
                    System.out.println("[NativePathChooser] showing FileDialog");
                    fd.setVisible(true);
                    System.out.println("[NativePathChooser] FileDialog closed, file=" + fd.getFile());
                    if (fd.getFile() != null) {
                        result.set(new File(fd.getDirectory(), fd.getFile()).getAbsolutePath());
                    }
                }
            } finally {
                anchor.dispose();
            }
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) task.run();
            else SwingUtilities.invokeAndWait(task);
        } catch (Exception e) {
            System.out.println("[NativePathChooser] exception: " + e);
            return null;
        }
        return result.get();
    }
}
