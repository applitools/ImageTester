package com.applitools.imagetester.lib.converters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ConverterPathsTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void resolvesFileNameUnderTempDir() {
        Path tempDir = tempFolder.getRoot().toPath();

        Path resolved = ConverterPaths.resolveWithinTempDir(tempDir, "readme.pdf");

        assertEquals(tempDir.resolve("readme.pdf"), resolved);
    }

    @Test
    public void throwsWhenNameWouldEscapeTempDir() {
        Path tempDir = tempFolder.getRoot().toPath();

        assertThrows(IllegalArgumentException.class,
            () -> ConverterPaths.resolveWithinTempDir(tempDir, "../evil.pdf"));
    }

    @Test
    public void basenameWithPdfExtensionSwapsExistingExtension() {
        assertEquals("readme.pdf", ConverterPaths.basenameWithPdfExtension(new File("readme.md")));
    }

    @Test
    public void basenameWithPdfExtensionAppendsWhenNameHasNoExtension() {
        assertEquals("readme.pdf", ConverterPaths.basenameWithPdfExtension(new File("readme")));
    }
}
