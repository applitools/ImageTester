package com.applitools.imagetester.gui;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NativePathChooserStartDirTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void resetCache() {
        NativePathChooser.resetLastDirForTest();
    }

    @Test
    public void shouldStartInHintDirectoryWhenHintResolves() throws Exception {
        File hintDir = tmp.newFolder("hint");
        assertEquals(hintDir, NativePathChooser.startDir(hintDir.getAbsolutePath()));
    }

    @Test
    public void shouldStartNowhereWhenNoHintAndNothingRemembered() {
        assertNull(NativePathChooser.startDir(null));
    }

    @Test
    public void shouldStartInParentOfLastChosenFileWhenHintIsNull() throws Exception {
        File dir = tmp.newFolder("TestData");
        File chosen = new File(dir, "picked.pdf");
        NativePathChooser.rememberLastDir(chosen.getAbsolutePath());
        assertEquals(dir, NativePathChooser.startDir(null));
    }

    @Test
    public void shouldStartInLastChosenFolderWhenHintIsNull() throws Exception {
        File dir = tmp.newFolder("TestData");
        NativePathChooser.rememberLastDir(dir.getAbsolutePath());
        assertEquals(dir, NativePathChooser.startDir(null));
    }

    @Test
    public void shouldPreferHintOverRememberedDirectory() throws Exception {
        File remembered = tmp.newFolder("remembered");
        File hintDir = tmp.newFolder("hint");
        NativePathChooser.rememberLastDir(remembered.getAbsolutePath());
        assertEquals(hintDir, NativePathChooser.startDir(hintDir.getAbsolutePath()));
    }

    @Test
    public void shouldFallBackToRememberedDirectoryWhenHintDoesNotResolve() throws Exception {
        File remembered = tmp.newFolder("remembered");
        NativePathChooser.rememberLastDir(remembered.getAbsolutePath());
        assertEquals(remembered, NativePathChooser.startDir("Z:\\no\\such\\path"));
    }

    @Test
    public void shouldIgnoreRememberedDirectoryThatNoLongerExists() throws Exception {
        File dir = tmp.newFolder("gone");
        NativePathChooser.rememberLastDir(dir.getAbsolutePath());
        dir.delete();
        assertNull(NativePathChooser.startDir(null));
    }
}
