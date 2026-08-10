package com.applitools.imagetester.lib;

import static org.junit.Assert.*;
import static com.applitools.imagetester.lib.PdfImageAssertions.assertImagesDiffer;
import static com.applitools.imagetester.lib.PdfImageAssertions.assertImagesMatch;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PdfFontNormalizerTest {

    private static final float TEST_DPI = 72f;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void should_produce_identical_images_when_fonts_differ_but_text_matches() throws IOException {
        File baseline = createTestPdf("Hello World", "Subtitle",
                PDType1Font.HELVETICA, 12f, PDType1Font.HELVETICA, 10f);
        File checkpoint = createTestPdf("Hello World", "Subtitle",
                PDType1Font.TIMES_BOLD, 24f, PDType1Font.COURIER_OBLIQUE, 8f);

        BufferedImage baselineImg = renderNormalizedFirstPage(baseline);
        BufferedImage checkpointImg = renderNormalizedFirstPage(checkpoint);

        assertImagesMatch(baselineImg, checkpointImg);
    }

    @Test
    public void should_produce_different_images_when_text_content_differs() throws IOException {
        File pdf1 = createTestPdf("Hello World", "Subtitle",
                PDType1Font.HELVETICA, 12f, PDType1Font.HELVETICA, 10f);
        File pdf2 = createTestPdf("COMPLETELY DIFFERENT TEXT ACROSS THE ENTIRE LINE", "Also changed here",
                PDType1Font.HELVETICA, 12f, PDType1Font.HELVETICA, 10f);

        BufferedImage img1 = renderNormalizedFirstPage(pdf1);
        BufferedImage img2 = renderNormalizedFirstPage(pdf2);

        assertImagesDiffer(img1, img2);
    }

    @Test
    public void should_not_modify_original_PDDocument() throws IOException {
        File pdf = createTestPdf("Hello World", "Subtitle",
                PDType1Font.TIMES_ROMAN, 18f, PDType1Font.COURIER, 9f);

        try (PDDocument document = PDDocument.load(pdf)) {
            PDPage originalPage = document.getPage(0);

            // Capture original content stream bytes
            PDFStreamParser parserBefore = new PDFStreamParser(originalPage);
            parserBefore.parse();
            List<Object> tokensBefore = parserBefore.getTokens();
            String streamBefore = tokensBefore.toString();

            // Run normalization
            PdfFontNormalizer.normalize(originalPage);

            // Verify original unchanged
            PDFStreamParser parserAfter = new PDFStreamParser(originalPage);
            parserAfter.parse();
            List<Object> tokensAfter = parserAfter.getTokens();
            String streamAfter = tokensAfter.toString();

            assertEquals(streamBefore, streamAfter);
        }
    }

    @Test
    public void should_not_modify_original_PDF_file_on_disk() throws IOException, NoSuchAlgorithmException {
        File pdf = createTestPdf("Hello World", "Subtitle",
                PDType1Font.TIMES_ROMAN, 18f, PDType1Font.COURIER, 9f);

        byte[] checksumBefore = md5(pdf);

        // Run full rendering pipeline
        try (PDDocument document = PDDocument.load(pdf)) {
            PdfFontNormalizer.renderNormalized(document.getPage(0), TEST_DPI);
        }

        byte[] checksumAfter = md5(pdf);
        assertArrayEquals(checksumBefore, checksumAfter);
    }

    @Test
    public void should_handle_Form_XObjects() throws IOException {
        File pdf = createPdfWithFormXObject();

        try (PDDocument document = PDDocument.load(pdf)) {
            PDPage originalPage = document.getPage(0);
            PDPage normalized = PdfFontNormalizer.normalize(originalPage);

            // Verify the form XObject's content stream was normalized
            PDFormXObject form = (PDFormXObject) normalized.getResources()
                    .getXObject(COSName.getPDFName("Fm1"));
            assertNotNull(form);

            PDFStreamParser parser = new PDFStreamParser(form);
            parser.parse();
            List<Object> tokens = parser.getTokens();

            // Every Tf operator should now reference Helv at size 12
            for (int i = 0; i < tokens.size(); i++) {
                Object token = tokens.get(i);
                if (token instanceof Operator && "Tf".equals(((Operator) token).getName())) {
                    assertEquals(COSName.getPDFName("Helv"), tokens.get(i - 2));
                }
            }
        }
    }

    @Test
    public void should_produce_identical_images_when_leading_differs() throws IOException {
        File pdf1 = createPdfWithLeading("Line one", "Line two",
                PDType1Font.HELVETICA, 11f, 17.6f);
        File pdf2 = createPdfWithLeading("Line one", "Line two",
                PDType1Font.TIMES_ROMAN, 24f, 38.4f);

        BufferedImage img1 = renderNormalizedFirstPage(pdf1);
        BufferedImage img2 = renderNormalizedFirstPage(pdf2);

        assertImagesMatch(img1, img2);
    }

    @Test
    public void should_handle_page_with_no_text() throws IOException {
        // A PDF page with only graphics (rectangle), no Tf operators
        File pdf = createGraphicsOnlyPdf();

        try (PDDocument document = PDDocument.load(pdf)) {
            // Should not throw
            BufferedImage img = PdfFontNormalizer.renderNormalized(document.getPage(0), TEST_DPI);
            assertNotNull(img);
        }
    }

    @Test
    public void normalized_page_preserves_page_dimensions() throws IOException {
        PDRectangle customSize = new PDRectangle(400, 800);
        File pdf = createTestPdfWithSize("Hello", PDType1Font.HELVETICA, 12f, customSize);

        try (PDDocument document = PDDocument.load(pdf)) {
            PDPage original = document.getPage(0);
            PDPage normalized = PdfFontNormalizer.normalize(original);

            assertEquals(customSize.getWidth(), normalized.getMediaBox().getWidth(), 0.01f);
            assertEquals(customSize.getHeight(), normalized.getMediaBox().getHeight(), 0.01f);
        }
    }

    // --- Helper methods ---

    private BufferedImage renderNormalizedFirstPage(File pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            return PdfFontNormalizer.renderNormalized(document.getPage(0), TEST_DPI);
        }
    }

    private File createTestPdf(String line1, String line2,
                               PDType1Font font1, float size1,
                               PDType1Font font2, float size2) throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font1, size1);
                cs.newLineAtOffset(72, 700);
                cs.showText(line1);
                cs.setFont(font2, size2);
                cs.newLineAtOffset(0, -20);
                cs.showText(line2);
                cs.endText();

                // Blue rectangle (non-text element)
                cs.setNonStrokingColor(0, 0, 255);
                cs.addRect(72, 500, 200, 100);
                cs.fill();
            }
            doc.save(file);
        }
        return file;
    }

    private File createPdfWithLeading(String line1, String line2,
                                      PDType1Font font, float size,
                                      float leading) throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, size);
                cs.setLeading(leading);
                cs.newLineAtOffset(72, 700);
                cs.showText(line1);
                cs.newLine();
                cs.showText(line2);
                cs.endText();
            }
            doc.save(file);
        }
        return file;
    }

    private File createTestPdfWithSize(String text, PDType1Font font, float size,
                                       PDRectangle pageSize) throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(72, pageSize.getHeight() - 72);
                cs.showText(text);
                cs.endText();
            }
            doc.save(file);
        }
        return file;
    }

    private File createPdfWithFormXObject() throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            // Create a Form XObject with text using Times-Bold
            PDFormXObject form = new PDFormXObject(doc);
            form.setBBox(new PDRectangle(300, 100));
            form.setResources(page.getResources() != null ? page.getResources() : new org.apache.pdfbox.pdmodel.PDResources());
            form.getResources().put(COSName.getPDFName("F1"), PDType1Font.TIMES_BOLD);

            try (java.io.OutputStream out = form.getCOSObject().createOutputStream()) {
                out.write("BT /F1 18 Tf 10 50 Td (Form Text) Tj ET".getBytes());
            }

            // Add form to page resources and invoke it
            if (page.getResources() == null) {
                page.setResources(new org.apache.pdfbox.pdmodel.PDResources());
            }
            page.getResources().put(COSName.getPDFName("Fm1"), form);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.COURIER, 14);
                cs.newLineAtOffset(72, 700);
                cs.showText("Page text");
                cs.endText();
                // Invoke the form XObject
                cs.saveGraphicsState();
                cs.transform(new org.apache.pdfbox.util.Matrix(1, 0, 0, 1, 72, 500));
                cs.drawForm(form);
                cs.restoreGraphicsState();
            }
            doc.save(file);
        }
        return file;
    }

    private File createGraphicsOnlyPdf() throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(255, 0, 0);
                cs.addRect(50, 50, 200, 200);
                cs.fill();
            }
            doc.save(file);
        }
        return file;
    }

    private byte[] md5(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }
}
