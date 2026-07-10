package com.applitools.imagetester.lib;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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
        // Resolve against the original page: watermark removal must not erase the crop marks first.
        PDRectangle trimCrop = PdfPageTrimmer.resolveCropBox(originalPage, config);

        if (!removeWatermark && !normalize) {
            if (trimCrop != null) originalPage.setCropBox(trimCrop);
            return MatchSizeResizer.resize(
                    fallback.renderImageWithDPI(zeroBasedPageIndex, config.DocumentConversionDPI), config);
        }

        PDPage page = originalPage;
        if (removeWatermark) {
            page = PdfWatermarkRemover.remove(page, config.removeWatermarkText);
        }
        if (normalize) {
            page = PdfFontNormalizer.normalize(page);
        }
        if (trimCrop != null) page.setCropBox(trimCrop);
        try (PDDocument tempDoc = new PDDocument()) {
            tempDoc.addPage(page);
            return MatchSizeResizer.resize(
                    new PDFRenderer(tempDoc).renderImageWithDPI(0, config.DocumentConversionDPI), config);
        }
    }
}
