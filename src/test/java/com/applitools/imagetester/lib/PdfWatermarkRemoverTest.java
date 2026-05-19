package com.applitools.imagetester.lib;

import org.junit.Test;
import static org.junit.Assert.*;

import com.applitools.imagetester.ImageTester;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class PdfWatermarkRemoverTest {

    @Test
    public void config_removeWatermarkText_defaultsToNull() {
        Config config = new Config();
        assertNull(config.removeWatermarkText);
    }

    @Test
    public void config_removeWatermarkOutDir_defaultsToNull() {
        Config config = new Config();
        assertNull(config.removeWatermarkOutDir);
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void remove_returns_a_detached_page_with_same_mediaBox() throws IOException {
        File pdf = createSinglePagePdf("Hello World", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage original = doc.getPage(0);
            PDPage cleaned = PdfWatermarkRemover.remove(original, "PRE-Proof");

            assertNotNull(cleaned);
            assertNotSame(original, cleaned);
            assertEquals(original.getMediaBox().getWidth(), cleaned.getMediaBox().getWidth(), 0.01f);
            assertEquals(original.getMediaBox().getHeight(), cleaned.getMediaBox().getHeight(), 0.01f);
        }
    }

    @Test
    public void removes_simple_Tj_watermark() throws IOException {
        File pdf = createSinglePagePdf("PRE-Proof", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage cleaned = PdfWatermarkRemover.remove(doc.getPage(0), "PRE-Proof");

            assertFalse("Tj operator for the watermark should be gone",
                    containsOperator(cleaned, "Tj"));
        }
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

    @Test
    public void is_case_insensitive() throws IOException {
        File pdf = createSinglePagePdf("PRE-PROOF", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage cleaned = PdfWatermarkRemover.remove(doc.getPage(0), "pre-proof");
            assertFalse(containsOperator(cleaned, "Tj"));
        }
    }

    private boolean containsOperator(PDPage page, String opName) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(page);
        parser.parse();
        List<Object> tokens = parser.getTokens();
        for (Object token : tokens) {
            if (token instanceof Operator && opName.equals(((Operator) token).getName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void removes_TJ_array_watermark() throws IOException {
        File pdf = createPdfWithRawTJ("PRE-", "Proof", "Helv");
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage cleaned = PdfWatermarkRemover.remove(doc.getPage(0), "PRE-Proof");
            assertFalse(containsOperator(cleaned, "TJ"));
        }
    }

    private File createPdfWithRawTJ(String part1, String part2, String fontName) throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.PDResources resources = new org.apache.pdfbox.pdmodel.PDResources();
            resources.put(org.apache.pdfbox.cos.COSName.getPDFName(fontName), PDType1Font.HELVETICA);
            page.setResources(resources);

            org.apache.pdfbox.cos.COSStream stream = new org.apache.pdfbox.cos.COSStream();
            try (java.io.OutputStream out = stream.createOutputStream()) {
                String content = "BT /" + fontName + " 12 Tf 72 700 Td "
                        + "[(" + part1 + ") -100 (" + part2 + ")] TJ ET";
                out.write(content.getBytes());
            }
            page.getCOSObject().setItem(org.apache.pdfbox.cos.COSName.CONTENTS, stream);

            doc.save(file);
        }
        return file;
    }

    @Test
    public void removes_apostrophe_show_operator() throws IOException {
        File pdf = createPdfWithRawOp("'", "PRE-Proof", "Helv");
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage cleaned = PdfWatermarkRemover.remove(doc.getPage(0), "PRE-Proof");
            assertFalse(containsOperator(cleaned, "'"));
        }
    }

    @Test
    public void removes_quote_show_operator() throws IOException {
        File pdf = createPdfWithRawQuoteOp("PRE-Proof", "Helv");
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage cleaned = PdfWatermarkRemover.remove(doc.getPage(0), "PRE-Proof");
            assertFalse(containsOperator(cleaned, "\""));
        }
    }

    private File createPdfWithRawOp(String op, String text, String fontName) throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.PDResources resources = new org.apache.pdfbox.pdmodel.PDResources();
            resources.put(org.apache.pdfbox.cos.COSName.getPDFName(fontName), PDType1Font.HELVETICA);
            page.setResources(resources);

            org.apache.pdfbox.cos.COSStream stream = new org.apache.pdfbox.cos.COSStream();
            try (java.io.OutputStream out = stream.createOutputStream()) {
                String content = "BT /" + fontName + " 12 Tf 72 700 Td (" + text + ") " + op + " ET";
                out.write(content.getBytes());
            }
            page.getCOSObject().setItem(org.apache.pdfbox.cos.COSName.CONTENTS, stream);
            doc.save(file);
        }
        return file;
    }

    private File createPdfWithRawQuoteOp(String text, String fontName) throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.PDResources resources = new org.apache.pdfbox.pdmodel.PDResources();
            resources.put(org.apache.pdfbox.cos.COSName.getPDFName(fontName), PDType1Font.HELVETICA);
            page.setResources(resources);

            org.apache.pdfbox.cos.COSStream stream = new org.apache.pdfbox.cos.COSStream();
            try (java.io.OutputStream out = stream.createOutputStream()) {
                // aw=0, ac=0, string, "
                String content = "BT /" + fontName + " 12 Tf 72 700 Td 0 0 (" + text + ") \" ET";
                out.write(content.getBytes());
            }
            page.getCOSObject().setItem(org.apache.pdfbox.cos.COSName.CONTENTS, stream);
            doc.save(file);
        }
        return file;
    }

    @Test
    public void removes_watermark_in_form_xobject() throws IOException {
        File pdf = createPdfWithWatermarkInFormXObject("PRE-Proof");
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage cleaned = PdfWatermarkRemover.remove(doc.getPage(0), "PRE-Proof");
            PDFormXObject form = (PDFormXObject) cleaned.getResources()
                    .getXObject(COSName.getPDFName("Wm1"));
            assertNotNull(form);

            PDFStreamParser parser = new PDFStreamParser(form);
            parser.parse();
            List<Object> tokens = parser.getTokens();
            for (Object token : tokens) {
                assertFalse(token instanceof Operator
                        && "Tj".equals(((Operator) token).getName()));
            }
        }
    }

    @Test
    public void renderCleaned_returns_non_null_image() throws IOException {
        File pdf = createSinglePagePdf("PRE-Proof", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            BufferedImage img = PdfWatermarkRemover.renderCleaned(doc.getPage(0), "PRE-Proof", 72f);
            assertNotNull(img);
            assertTrue(img.getWidth() > 0);
            assertTrue(img.getHeight() > 0);
        }
    }

    private File createPdfWithWatermarkInFormXObject(String watermarkText) throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            PDFormXObject form = new PDFormXObject(doc);
            form.setBBox(new PDRectangle(300, 100));
            form.setResources(new org.apache.pdfbox.pdmodel.PDResources());
            form.getResources().put(COSName.getPDFName("F1"), PDType1Font.HELVETICA);

            try (java.io.OutputStream out = form.getCOSObject().createOutputStream()) {
                String content = "BT /F1 36 Tf 50 50 Td (" + watermarkText + ") Tj ET";
                out.write(content.getBytes());
            }

            if (page.getResources() == null) {
                page.setResources(new org.apache.pdfbox.pdmodel.PDResources());
            }
            page.getResources().put(COSName.getPDFName("Wm1"), form);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Body content");
                cs.endText();
                cs.saveGraphicsState();
                cs.transform(new org.apache.pdfbox.util.Matrix(1, 0, 0, 1, 100, 400));
                cs.drawForm(form);
                cs.restoreGraphicsState();
            }
            doc.save(file);
        }
        return file;
    }

    @Test
    public void does_not_modify_original_PDDocument() throws IOException {
        File pdf = createSinglePagePdf("PRE-Proof", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage original = doc.getPage(0);
            PDFStreamParser parserBefore = new PDFStreamParser(original);
            parserBefore.parse();
            String streamBefore = parserBefore.getTokens().toString();

            PdfWatermarkRemover.remove(original, "PRE-Proof");

            PDFStreamParser parserAfter = new PDFStreamParser(original);
            parserAfter.parse();
            String streamAfter = parserAfter.getTokens().toString();

            assertEquals(streamBefore, streamAfter);
        }
    }

    @Test
    public void does_not_modify_input_pdf_on_disk() throws IOException, NoSuchAlgorithmException {
        File pdf = createSinglePagePdf("PRE-Proof", PDType1Font.HELVETICA, 12f);
        byte[] before = md5(pdf);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PdfWatermarkRemover.renderCleaned(doc.getPage(0), "PRE-Proof", 72f);
        }
        byte[] after = md5(pdf);
        assertArrayEquals(before, after);
    }

    @Test
    public void leaves_non_matching_text_intact() throws IOException {
        File pdf = createSinglePagePdf("Hello World", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage cleaned = PdfWatermarkRemover.remove(doc.getPage(0), "PRE-Proof");
            assertTrue("Non-matching text should remain — Tj should still be present",
                    containsOperator(cleaned, "Tj"));
        }
    }

    @Test
    public void no_match_returns_unchanged_token_count() throws IOException {
        File pdf = createSinglePagePdf("Hello World", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage original = doc.getPage(0);
            PDFStreamParser parserBefore = new PDFStreamParser(original);
            parserBefore.parse();
            int originalCount = parserBefore.getTokens().size();

            PDPage cleaned = PdfWatermarkRemover.remove(original, "PRE-Proof");

            PDFStreamParser parserAfter = new PDFStreamParser(cleaned);
            parserAfter.parse();
            assertEquals(originalCount, parserAfter.getTokens().size());
        }
    }

    @Test
    public void removes_body_text_that_matches_hint() throws IOException {
        // Documented trade-off: matching is purely string-based, so any text
        // matching the hint is removed regardless of its role. This test pins
        // that behavior so a regression is caught.
        File pdf = createSinglePagePdf("PRE-Proof", PDType1Font.HELVETICA, 12f);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage cleaned = PdfWatermarkRemover.remove(doc.getPage(0), "PRE-Proof");
            assertFalse(containsOperator(cleaned, "Tj"));
        }
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

    @Test
    public void font_without_tounicode_does_not_crash() throws IOException {
        // Use a font where toUnicode may return null for the byte (Symbol has limited mapping).
        // We use raw content stream to bypass PDBox's encoding validation since Symbol
        // cannot encode standard ASCII characters.
        File pdf = createPdfWithRawOp("Tj", "ABC", "Sym");
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage original = doc.getPage(0);
            org.apache.pdfbox.pdmodel.PDResources resources = original.getResources();
            if (resources == null) {
                resources = new org.apache.pdfbox.pdmodel.PDResources();
                original.setResources(resources);
            }
            resources.put(org.apache.pdfbox.cos.COSName.getPDFName("Sym"), PDType1Font.SYMBOL);

            PDPage cleaned = PdfWatermarkRemover.remove(original, "ABC");
            assertNotNull(cleaned);
        }
    }

    @Test
    public void rw_flag_populates_config_field() {
        org.apache.commons.cli.Options options = invokeGetOptions();
        org.apache.commons.cli.CommandLineParser parser = new org.apache.commons.cli.DefaultParser();
        try {
            org.apache.commons.cli.CommandLine cmd = parser.parse(options,
                    new String[] { "-rw", "PRE-Proof", "-f", "." });
            assertEquals("PRE-Proof", cmd.getOptionValue("rw"));
        } catch (org.apache.commons.cli.ParseException e) {
            fail("CLI parse failed: " + e.getMessage());
        }
    }

    @Test
    public void rwo_flag_populates_config_field() {
        org.apache.commons.cli.Options options = invokeGetOptions();
        org.apache.commons.cli.CommandLineParser parser = new org.apache.commons.cli.DefaultParser();
        try {
            org.apache.commons.cli.CommandLine cmd = parser.parse(options,
                    new String[] { "-rw", "PRE-Proof", "-rwo", "out", "-f", "." });
            assertEquals("out", cmd.getOptionValue("rwo"));
        } catch (org.apache.commons.cli.ParseException e) {
            fail("CLI parse failed: " + e.getMessage());
        }
    }

    private org.apache.commons.cli.Options invokeGetOptions() {
        try {
            java.lang.reflect.Method m = ImageTester.class.getDeclaredMethod("getOptions");
            m.setAccessible(true);
            return (org.apache.commons.cli.Options) m.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void rwo_without_rw_returns_nonzero_exit() throws IOException {
        String emptyDir = tempFolder.newFolder().getAbsolutePath();
        int exit = ImageTester.run(new String[] { "-rwo", "out", "-f", emptyDir });
        assertEquals(1, exit);
    }

    @Test
    public void blank_rw_returns_nonzero_exit() throws IOException {
        String emptyDir = tempFolder.newFolder().getAbsolutePath();
        int exit = ImageTester.run(new String[] { "-rw", "   ", "-f", emptyDir });
        assertEquals(1, exit);
    }

    @Test
    public void rwo_writes_cleaned_pdf_to_outdir() throws IOException {
        File inputDir = tempFolder.newFolder("input");
        File outputDir = tempFolder.newFolder("output");
        createPdfInDir(inputDir, "doc.pdf", "REJECTED", PDType1Font.HELVETICA, 12f);

        int exit = ImageTester.run(new String[] {
                "-rw", "REJECTED",
                "-rwo", outputDir.getAbsolutePath(),
                "-f", inputDir.getAbsolutePath()
        });

        assertEquals(0, exit);
        File cleaned = new File(outputDir, "doc.pdf");
        assertTrue("Cleaned PDF should exist in outdir", cleaned.exists());
        try (PDDocument doc = PDDocument.load(cleaned)) {
            assertFalse("Watermark Tj operator should be gone",
                    pageContainsOperator(doc.getPage(0), "Tj"));
        }
    }

    @Test
    public void rwo_skips_non_pdf_files_without_failing() throws IOException {
        File inputDir = tempFolder.newFolder("input");
        File outputDir = tempFolder.newFolder("output");
        createPdfInDir(inputDir, "doc.pdf", "REJECTED", PDType1Font.HELVETICA, 12f);
        File nonPdf = new File(inputDir, "image.png");
        assertTrue(nonPdf.createNewFile());

        int exit = ImageTester.run(new String[] {
                "-rw", "REJECTED",
                "-rwo", outputDir.getAbsolutePath(),
                "-f", inputDir.getAbsolutePath()
        });

        assertEquals(0, exit);
        assertTrue(new File(outputDir, "doc.pdf").exists());
        assertFalse("Non-PDF should not be copied to outdir",
                new File(outputDir, "image.png").exists());
    }

    private File createPdfInDir(File dir, String name, String text, PDType1Font font, float size) throws IOException {
        File file = new File(dir, name);
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

    private boolean pageContainsOperator(PDPage page, String opName) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(page);
        parser.parse();
        for (Object t : parser.getTokens()) {
            if (t instanceof Operator && opName.equals(((Operator) t).getName())) return true;
        }
        return false;
    }
}
