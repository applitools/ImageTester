package com.applitools.imagetester.lib;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Selects the rendering strategy for a PDF page based on Config flags:
 * removeWatermarkText (-rw) and normalizeFont (-nf). When both are set,
 * watermark removal runs first and the cleaned page is then font-normalized.
 * When neither is set, the supplied PDFRenderer fallback is used directly.
 */
public final class PdfPageRenderer {

    private PdfPageRenderer() {
    }

    public static BufferedImage render(PDPage originalPage,
                                       int zeroBasedPageIndex,
                                       PDFRenderer fallback,
                                       Config config) throws IOException {
        boolean removeWatermark = config.removeWatermarkText != null;
        boolean normalize = config.normalizeFont;

        if (!removeWatermark && !normalize) {
            return fallback.renderImageWithDPI(zeroBasedPageIndex, config.DocumentConversionDPI);
        }

        PDPage page = originalPage;
        if (removeWatermark) {
            page = PdfWatermarkRemover.remove(page, config.removeWatermarkText);
        }
        if (normalize) {
            page = PdfFontNormalizer.normalize(page);
        }
        try (PDDocument tempDoc = new PDDocument()) {
            tempDoc.addPage(page);
            return new PDFRenderer(tempDoc).renderImageWithDPI(0, config.DocumentConversionDPI);
        }
    }
}
