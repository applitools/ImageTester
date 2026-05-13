package com.applitools.imagetester.gui;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SourcePathValidatorTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void acceptsExistingReadableFile() throws Exception {
        File f = tmp.newFile("ok.png");
        Path resolved = SourcePathValidator.validate(f.getAbsolutePath());
        assertEquals(f.toPath().toRealPath(), resolved);
    }

    @Test
    public void acceptsExistingReadableFolder() throws Exception {
        File d = tmp.newFolder("ok");
        Path resolved = SourcePathValidator.validate(d.getAbsolutePath());
        assertEquals(d.toPath().toRealPath(), resolved);
    }

    @Test
    public void rejectsMissingPath() {
        File missing = new File(tmp.getRoot(), "does-not-exist");
        assertThrows(SourcePathValidator.InvalidSourceException.class,
            () -> SourcePathValidator.validate(missing.getAbsolutePath()));
    }

    @Test
    public void rejectsBlankPath() {
        assertThrows(SourcePathValidator.InvalidSourceException.class,
            () -> SourcePathValidator.validate(""));
        assertThrows(SourcePathValidator.InvalidSourceException.class,
            () -> SourcePathValidator.validate(null));
    }

    @Test
    public void resolvesAndAcceptsRelativePath() throws Exception {
        File f = tmp.newFile("rel.png");
        Path resolved = SourcePathValidator.validate(f.getPath());
        assertEquals(f.toPath().toRealPath(), resolved);
    }
}
