package com.applitools.imagetester.lib.converters;

import java.io.File;

public class SkippedFileException extends Exception {
    private final File file;
    private final String reason;

    public SkippedFileException(File file, String reason) {
        super(String.format("Skipped %s: %s", file.getName(), reason));
        this.file = file;
        this.reason = reason;
    }

    public File getFile() { return file; }
    public String getReason() { return reason; }
}
