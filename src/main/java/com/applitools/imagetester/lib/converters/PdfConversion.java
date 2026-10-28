package com.applitools.imagetester.lib.converters;

import com.applitools.imagetester.lib.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The single place that knows how a non-image, non-PDF input becomes a PDF ImageTester can
 * render. Both the folder-scan path ({@code Suite}) and the two-document compare path
 * ({@code CompareRunner}) go through here so they support exactly the same set of formats —
 * before this existed, compare mode had its own image/PDF-only branch and rejected every
 * document type the normal run converted happily.
 */
public final class PdfConversion {

    private static final ConversionRegistry DEFAULT_REGISTRY = buildDefaultRegistry();

    private PdfConversion() {}

    public static ConversionRegistry buildDefaultRegistry() {
        ConversionRegistry r = new ConversionRegistry();
        r.register(new TxtToPdfConverter());
        r.register(new MarkdownToPdfConverter());
        r.register(new RtfToPdfConverter());
        r.register(new LibreOfficeConverter());
        return r;
    }

    public static ConversionRegistry defaultRegistry() {
        return DEFAULT_REGISTRY;
    }

    /**
     * Converts {@code file} to a PDF in a fresh temp directory (both marked delete-on-exit).
     * Returns empty when no registered converter accepts the file's extension.
     *
     * @throws SkippedFileException when a converter accepts the file but can't convert it
     *         (LibreOffice missing, unsupported PS/XPS, soffice failure)
     */
    public static Optional<File> convertToTempPdf(File file, ConversionRegistry registry, Logger logger)
            throws SkippedFileException, IOException {
        Optional<FormatConverter> match = registry.find(file);
        if (!match.isPresent()) return Optional.empty();
        logger.printMessage(String.format("Converting %s to PDF...%n", file.getName()));
        Path tempDir = Files.createTempDirectory("imagetester-");
        tempDir.toFile().deleteOnExit();
        File pdfTemp = match.get().convertToPdf(file, tempDir);
        pdfTemp.deleteOnExit();
        return Optional.of(pdfTemp);
    }
}
