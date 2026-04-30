package com.applitools.imagetester.lib.converters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public interface FormatConverter {

    boolean accepts(File file);

    File convertToPdf(File file, Path tempDir) throws SkippedFileException, IOException;
}
