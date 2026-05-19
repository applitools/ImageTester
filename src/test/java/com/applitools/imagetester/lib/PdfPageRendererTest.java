package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PdfPageRendererTest {

    private static final float TEST_DPI = 72f;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void with_no_flags_set_matches_fallback_render() throws IOException {
        File pdf = createSinglePagePdf("Hello World", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFRenderer fallback = new PDFRenderer(doc);
            Config config = baseConfig();

            BufferedImage helperImg = PdfPageRenderer.render(doc.getPage(0), 0, fallback, config);
            BufferedImage fallbackImg = fallback.renderImageWithDPI(0, TEST_DPI);

            assertImagesMatch(fallbackImg, helperImg);
        }
    }

    @Test
    public void with_rw_set_removes_watermark_pixels() throws IOException {
        File pdf = createSinglePagePdf("REJECTED", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFRenderer fallback = new PDFRenderer(doc);
            Config configNoFlag = baseConfig();
            Config configRw = baseConfig();
            configRw.removeWatermarkText = "REJECTED";

            BufferedImage withWatermark = PdfPageRenderer.render(doc.getPage(0), 0, fallback, configNoFlag);
            BufferedImage withoutWatermark = PdfPageRenderer.render(doc.getPage(0), 0, fallback, configRw);

            assertTrue("Removing the watermark should reduce dark pixels",
                    darkPixelCount(withoutWatermark) < darkPixelCount(withWatermark));
        }
    }

    @Test
    public void with_rw_and_nf_set_composes_both_transforms() throws IOException {
        File pdf = createSinglePagePdf("REJECTED", PDType1Font.TIMES_BOLD, 24f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFRenderer fallback = new PDFRenderer(doc);
            Config configNoFlag = baseConfig();
            Config configBoth = baseConfig();
            configBoth.removeWatermarkText = "REJECTED";
            configBoth.normalizeFont = true;

            BufferedImage original = PdfPageRenderer.render(doc.getPage(0), 0, fallback, configNoFlag);
            BufferedImage transformed = PdfPageRenderer.render(doc.getPage(0), 0, fallback, configBoth);

            assertTrue("Composed transforms should remove watermark pixels",
                    darkPixelCount(transformed) < darkPixelCount(original));
        }
    }

    private Config baseConfig() {
        Config config = new Config();
        config.DocumentConversionDPI = TEST_DPI;
        return config;
    }

    private File createSinglePagePdf(String text, PDType1Font font, float size) throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(72, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(file);
        }
        return file;
    }

    private void assertImagesMatch(BufferedImage a, BufferedImage b) {
        assertEquals("Width mismatch", a.getWidth(), b.getWidth());
        assertEquals("Height mismatch", a.getHeight(), b.getHeight());
        int diffPixels = 0;
        int totalPixels = a.getWidth() * a.getHeight();
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) diffPixels++;
            }
        }
        double diffPercent = (diffPixels * 100.0) / totalPixels;
        assertTrue("Images differ by " + String.format("%.2f", diffPercent) + "% (threshold 1%)",
                diffPercent < 1.0);
    }

    private int darkPixelCount(BufferedImage img) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                if (r + g + b < 384) count++; // average channel < 128
            }
        }
        return count;
    }
}
