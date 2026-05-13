package com.applitools.imagetester.gui;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public final class NativePathChooser {

    private NativePathChooser() {}

    public static String chooseFile() { return choose(false); }
    public static String chooseFolder() { return choose(true); }

    private static String choose(boolean folder) {
        AtomicReference<String> result = new AtomicReference<>();
        Runnable task = () -> {
            if (folder) {
                JFileChooser fc = new JFileChooser();
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fc.setDialogTitle("Choose source folder");
                if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    result.set(fc.getSelectedFile().getAbsolutePath());
                }
            } else {
                FileDialog fd = new FileDialog((Frame) null, "Choose source file", FileDialog.LOAD);
                fd.setVisible(true);
                if (fd.getFile() != null) {
                    result.set(new File(fd.getDirectory(), fd.getFile()).getAbsolutePath());
                }
            }
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) task.run();
            else SwingUtilities.invokeAndWait(task);
        } catch (Exception e) {
            return null;
        }
        return result.get();
    }
}
