package com.applitools.imagetester.gui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Image;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public final class NativePathChooser {

    // The Windows common dialog is not an AWT window: it ignores setAlwaysOnTop and doesn't
    // inherit the anchor's topmost state, so it opens behind the browser (focus-stealing
    // prevention blocks a background process from raising it). Swing dialogs inherit topmost
    // from the anchor, so Windows routes files through JFileChooser like folders.
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");

    private static final boolean IS_MAC =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");

    /** Switches the macOS native panel from picking files to picking directories. */
    private static final String MAC_DIRECTORY_DIALOG_PROPERTY = "apple.awt.fileDialogForDirectories";

    private static final String ICON_RESOURCE = "/icons/app-icon.png";

    private static volatile boolean isLookAndFeelInstalled;
    private static volatile Image dialogIcon;

    /** Loads the app icon once for the chooser dialogs; on failure the default Java icon still shows. */
    private static Image dialogIcon() {
        if (dialogIcon == null) {
            try {
                java.net.URL url = NativePathChooser.class.getResource(ICON_RESOURCE);
                if (url != null) dialogIcon = ImageIO.read(url);
            } catch (Throwable ignored) { /* icon is cosmetic; best effort */ }
        }
        return dialogIcon;
    }

    /** Installs FlatLaf once for the chooser dialogs; on failure the default L&F still works. */
    static void ensureLookAndFeel() {
        if (isLookAndFeelInstalled) return;
        isLookAndFeelInstalled = true;
        try {
            // FlatLaf's custom window decorations mis-place dialogs on multi-monitor
            // setups (dialog opens on the wrong screen); keep native title bars.
            System.setProperty("flatlaf.useWindowDecorations", "false");
            if (SwingUtilities.isEventDispatchThread()) FlatLightLaf.setup();
            else SwingUtilities.invokeAndWait(FlatLightLaf::setup);
        } catch (Throwable ignored) { /* styling is best-effort; Metal fallback still works */ }
    }

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
                ensureLookAndFeel();
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

    /** Last directory a pick succeeded in; session-scoped so consecutive picks start where the user left off. */
    private static volatile File lastDir;

    /** The hint wins so re-picking a filled field starts at that field's path; otherwise the last visited directory. */
    static File startDir(String hint) {
        File fromHint = resolveStartDir(hint);
        if (fromHint != null) return fromHint;
        File last = lastDir;
        return (last != null && last.isDirectory()) ? last : null;
    }

    /** Remembers where a pick succeeded: the chosen folder itself, or the chosen file's parent. */
    static void rememberLastDir(String chosenPath) {
        lastDir = resolveStartDir(chosenPath);
    }

    static void resetLastDirForTest() { lastDir = null; }

    private static String choose(boolean folder, String startPath) {
        ensureLookAndFeel();
        AtomicReference<String> result = new AtomicReference<>();
        File startDir = startDir(startPath);
        Runnable task = () -> {
            JFrame anchor = createAnchor();
            try {
                result.set(isSwingChooserRequired(folder)
                        ? pickWithSwingChooser(anchor, folder, startDir)
                        : pickWithNativeDialog(anchor, folder, startDir));
            } finally {
                anchor.dispose();
            }
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) task.run();
            else SwingUtilities.invokeAndWait(task);
        } catch (Exception e) {
            System.err.println("Could not open the " + (folder ? "folder" : "file") + " chooser: " + e);
            return null;
        }
        String chosen = result.get();
        if (chosen != null) rememberLastDir(chosen);
        return chosen;
    }

    /**
     * Swing owns Windows (see IS_WINDOWS) and non-macOS folder picks, where the native AWT
     * dialog cannot select directories at all. macOS folder picks must NOT come here: a
     * JFileChooser opened while the browser is frontmost becomes key for the keyboard but
     * silently drops every mouse click, because macOS 14+ no longer lets a background process
     * activate itself on request (requestForeground is effectively a no-op there).
     */
    private static boolean isSwingChooserRequired(boolean folder) {
        return IS_WINDOWS || (folder && !IS_MAC);
    }

    /**
     * Anchors a 1px, always-on-top, focusable frame so the file dialog inherits its z-order and
     * comes to the foreground — without a real owner the OS leaves the dialog behind the browser
     * window that just made this HTTP call. Centered on screen so the dialog (which centers on
     * its parent) opens centered, not in a corner.
     */
    private static JFrame createAnchor() {
        JFrame anchor = new JFrame();
        // The chooser dialog inherits the owner frame's icon into its title bar.
        Image icon = dialogIcon();
        if (icon != null) anchor.setIconImage(icon);
        anchor.setUndecorated(true);
        anchor.setSize(1, 1);
        anchor.setLocationRelativeTo(null);
        anchor.setAlwaysOnTop(true);
        anchor.setType(java.awt.Window.Type.UTILITY);
        anchor.setVisible(true);
        anchor.toFront();
        requestForeground();
        return anchor;
    }

    private static String dialogTitle(boolean folder) {
        return folder ? "Choose source folder" : "Choose source file";
    }

    private static String pickWithSwingChooser(JFrame anchor, boolean folder, File startDir) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(folder ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        fc.setDialogTitle(dialogTitle(folder));
        if (startDir != null) fc.setCurrentDirectory(startDir);
        if (fc.showOpenDialog(anchor) != JFileChooser.APPROVE_OPTION) return null;
        return fc.getSelectedFile().getAbsolutePath();
    }

    /**
     * NOTE: the AWT peer reads MAC_DIRECTORY_DIALOG_PROPERTY when the dialog is shown, so
     * directory mode has to be toggled around this show and restored after. Safe because the
     * dialog is modal and this runs on the EDT: only one chooser is ever open at a time.
     */
    private static String pickWithNativeDialog(JFrame anchor, boolean folder, File startDir) {
        String previousDirectoryMode = System.getProperty(MAC_DIRECTORY_DIALOG_PROPERTY);
        if (folder) System.setProperty(MAC_DIRECTORY_DIALOG_PROPERTY, "true");
        try {
            FileDialog fd = new FileDialog((Frame) anchor, dialogTitle(folder), FileDialog.LOAD);
            fd.setAlwaysOnTop(true);
            if (startDir != null) fd.setDirectory(startDir.getAbsolutePath());
            fd.setVisible(true);
            if (fd.getFile() == null) return null;
            return new File(fd.getDirectory(), fd.getFile()).getAbsolutePath();
        } finally {
            if (folder) restoreDirectoryMode(previousDirectoryMode);
        }
    }

    private static void restoreDirectoryMode(String previousValue) {
        if (previousValue == null) System.clearProperty(MAC_DIRECTORY_DIALOG_PROPERTY);
        else System.setProperty(MAC_DIRECTORY_DIALOG_PROPERTY, previousValue);
    }
}
