package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.Test;

public class NotoFontProviderTest {

    private static final float TEST_DPI = 72f;
    private static final int DARK_PIXEL_THRESHOLD = 100;

    @Test
    public void should_load_bundled_font_and_encode_japanese() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font noto = NotoFontProvider.load(doc);

            byte[] encoded = noto.encode("日本語カタカナひらがな");

            assertTrue("Encoded Japanese text should be non-empty", encoded.length > 0);
        }
    }

    @Test
    public void should_render_japanese_text_with_visible_glyphs() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font noto = NotoFontProvider.load(doc);
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(noto, 24);
                cs.newLineAtOffset(72, 700);
                cs.showText("変更手続きのご案内");
                cs.endText();
            }

            BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, TEST_DPI);

            assertTrue("Rendered Japanese text should produce dark pixels",
                    darkPixelCount(img) > DARK_PIXEL_THRESHOLD);
        }
    }

    private static int darkPixelCount(BufferedImage img) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (r < 128 && g < 128 && b < 128) count++;
            }
        }
        return count;
    }
}
