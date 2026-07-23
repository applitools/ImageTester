package com.applitools.imagetester.gui;

import org.junit.Test;

import javax.swing.UIManager;

import static org.junit.Assert.assertEquals;

public class NativePathChooserLookAndFeelTest {

    @Test
    public void shouldInstallFlatLafLightWhenEnsureLookAndFeelRuns() {
        NativePathChooser.ensureLookAndFeel();
        assertEquals("FlatLaf Light", UIManager.getLookAndFeel().getName());
    }
}
