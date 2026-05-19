package com.applitools.imagetester.lib;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Implements the -rwo runtime mode: walks an input directory, writes a
 * watermark-cleaned copy of every PDF into an output directory using the same
 * filename, and skips non-PDF files with a logged warning. Eyes is not
 * contacted in this mode.
 */
public final class PdfWatermarkOutMode {

    private PdfWatermarkOutMode() {
    }

    public static int run(File inputRoot, String hint, File outDir, Logger logger) {
        if (!inputRoot.exists()) {
            logger.printMessage("ERROR: input path does not exist: " + inputRoot.getAbsolutePath());
            return 1;
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            logger.printMessage("ERROR: could not create output directory: " + outDir.getAbsolutePath());
            return 1;
        }

        int processed = walk(inputRoot, hint, outDir, logger);
        logger.printMessage(String.format("Watermark removal complete: %d PDF(s) written to %s",
                processed, outDir.getAbsolutePath()));
        return 0;
    }

    private static int walk(File entry, String hint, File outDir, Logger logger) {
        if (entry.isDirectory()) {
            File[] children = entry.listFiles();
            if (children == null) return 0;
            int total = 0;
            for (File child : children) total += walk(child, hint, outDir, logger);
            return total;
        }
        if (!isPdf(entry)) {
            logger.printMessage("Skipping non-PDF file: " + entry.getAbsolutePath());
            return 0;
        }
        try {
            cleanPdf(entry, hint, new File(outDir, entry.getName()));
            return 1;
        } catch (IOException e) {
            logger.printMessage("Failed to clean " + entry.getAbsolutePath() + ": " + e.getMessage());
            return 0;
        }
    }

    private static void cleanPdf(File input, String hint, File output) throws IOException {
        try (PDDocument source = PDDocument.load(input);
             PDDocument cleaned = new PDDocument()) {
            for (int i = 0; i < source.getNumberOfPages(); i++) {
                PDPage cleanedPage = PdfWatermarkRemover.remove(source.getPage(i), hint);
                cleaned.addPage(cleanedPage);
            }
            cleaned.save(output);
        }
    }

    private static boolean isPdf(File f) {
        return f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }
}
