package com.applitools.imagetester.lib.converters;

import java.io.File;
import java.nio.file.Path;

/** Output-path helpers shared by the format converters. */
final class ConverterPaths {

    private ConverterPaths() {}

    /** The input file's name with its extension swapped for ".pdf". */
    static String basenameWithPdfExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        return base + ".pdf";
    }

    /**
     * Resolves fileName under tempDir, verifying containment. Every caller passes a name derived
     * from File.getName(), which can never contain a path separator, so this can't fire today —
     * it's a hard backstop against a future caller passing an unsanitized name.
     */
    static Path resolveWithinTempDir(Path tempDir, String fileName) {
        Path dir = tempDir.normalize();
        Path resolved = dir.resolve(fileName).normalize();
        if (!resolved.startsWith(dir)) {
            throw new IllegalArgumentException("Resolved output path escapes temp directory: " + fileName);
        }
        return resolved;
    }
}
