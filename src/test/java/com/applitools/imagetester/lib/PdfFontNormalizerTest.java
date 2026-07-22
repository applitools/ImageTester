package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

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
            try (PDDocument tempDoc = new PDDocument()) {
                PdfFontNormalizer.normalize(originalPage, tempDoc, true, true);
            }

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
            PdfFontNormalizer.renderNormalized(document.getPage(0), TEST_DPI, true, true);
        }

        byte[] checksumAfter = md5(pdf);
        assertArrayEquals(checksumBefore, checksumAfter);
    }

    @Test
    public void should_handle_Form_XObjects() throws IOException {
        File pdf = createPdfWithFormXObject();

        try (PDDocument document = PDDocument.load(pdf);
             PDDocument tempDoc = new PDDocument()) {
            PDPage originalPage = document.getPage(0);
            PDPage normalized = PdfFontNormalizer.normalize(originalPage, tempDoc, true, true);

            PDFormXObject form = (PDFormXObject) normalized.getResources()
                    .getXObject(COSName.getPDFName("Fm1"));
            assertNotNull(form);

            PDFStreamParser parser = new PDFStreamParser(form);
            parser.parse();
            List<Object> tokens = parser.getTokens();

            // The Tf in effect at each show operator must reference Helv
            Object governingFont = null;
            for (int i = 0; i < tokens.size(); i++) {
                Object token = tokens.get(i);
                if (!(token instanceof Operator)) continue;
                String op = ((Operator) token).getName();
                if ("Tf".equals(op)) {
                    governingFont = tokens.get(i - 2);
                } else if ("Tj".equals(op)) {
                    assertEquals(COSName.getPDFName("Helv"), governingFont);
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
            BufferedImage img = PdfFontNormalizer.renderNormalized(document.getPage(0), TEST_DPI, true, true);
            assertNotNull(img);
        }
    }

    @Test
    public void normalized_page_preserves_page_dimensions() throws IOException {
        PDRectangle customSize = new PDRectangle(400, 800);
        File pdf = createTestPdfWithSize("Hello", PDType1Font.HELVETICA, 12f, customSize);

        try (PDDocument document = PDDocument.load(pdf);
             PDDocument tempDoc = new PDDocument()) {
            PDPage original = document.getPage(0);
            PDPage normalized = PdfFontNormalizer.normalize(original, tempDoc, true, true);

            assertEquals(customSize.getWidth(), normalized.getMediaBox().getWidth(), 0.01f);
            assertEquals(customSize.getHeight(), normalized.getMediaBox().getHeight(), 0.01f);
        }
    }

    @Test
    public void should_produce_identical_images_when_japanese_sizes_differ() throws IOException {
        File pdf1 = createJapanesePdf("変更手続きのご案内", 12f);
        File pdf2 = createJapanesePdf("変更手続きのご案内", 28f);

        BufferedImage img1 = renderNormalizedFirstPage(pdf1);
        BufferedImage img2 = renderNormalizedFirstPage(pdf2);

        assertImagesMatch(img1, img2);
    }

    @Test
    public void should_produce_different_images_when_japanese_text_differs() throws IOException {
        File pdf1 = createJapanesePdf("変更手続きのご案内", 12f);
        File pdf2 = createJapanesePdf("全然違うテキストですここは", 12f);

        BufferedImage img1 = renderNormalizedFirstPage(pdf1);
        BufferedImage img2 = renderNormalizedFirstPage(pdf2);

        assertImagesDiffer(img1, img2);
    }

    @Test
    public void should_route_mixed_latin_cjk_run_to_noto() throws IOException {
        File pdf = createJapanesePdf("2025年10月31日", 18f);

        try (PDDocument document = PDDocument.load(pdf);
             PDDocument tempDoc = new PDDocument()) {
            PDPage normalized = PdfFontNormalizer.normalize(document.getPage(0), tempDoc, true, true);

            assertEquals(nfkc("2025年10月31日"), nfkc(decodedTextGovernedBy(normalized, "NotoJP")));
        }
    }

    @Test
    public void nf_alone_should_leave_japanese_runs_untouched() throws IOException {
        File pdf = createJapanesePdf("変更手続きのご案内", 18f);

        try (PDDocument document = PDDocument.load(pdf);
             PDDocument tempDoc = new PDDocument()) {
            // normalizeLatin only — Japanese must stay in its original font
            PDPage normalized = PdfFontNormalizer.normalize(document.getPage(0), tempDoc, true, false);

            assertEquals("", decodedTextGovernedBy(normalized, "NotoJP"));
            assertEquals("", decodedTextGovernedBy(normalized, "Helv"));
            assertEquals(nfkc("変更手続きのご案内"), nfkc(allDecodedText(normalized)));
        }
    }

    @Test
    public void should_substitute_geta_mark_for_glyphs_missing_from_noto() throws IOException {
        // U+1F600 (emoji) is not in Noto Sans JP; the run is Japanese because of 日本
        File pdf = createJapanesePdfWithUnrepresentableGlyph(18f);

        try (PDDocument document = PDDocument.load(pdf);
             PDDocument tempDoc = new PDDocument()) {
            PDPage normalized = PdfFontNormalizer.normalize(document.getPage(0), tempDoc, true, true);

            assertEquals(nfkc("日本〓語"), nfkc(decodedTextGovernedBy(normalized, "NotoJP")));
        }
    }

    @Test
    public void should_route_helvetica_missing_symbols_to_noto() throws IOException {
        // ● (U+25CF) is not Japanese by classification, but Helvetica has no glyph
        // for it — the run must route to Noto so both comparison sides render ●
        File pdf = createJapanesePdf("● item 1", 18f);

        try (PDDocument document = PDDocument.load(pdf);
             PDDocument tempDoc = new PDDocument()) {
            PDPage normalized = PdfFontNormalizer.normalize(document.getPage(0), tempDoc, true, true);

            assertEquals(nfkc("● item 1"), nfkc(decodedTextGovernedBy(normalized, "NotoJP")));
        }
    }

    @Test
    public void should_keep_pure_ascii_runs_in_helvetica_when_both_flags_on() throws IOException {
        File pdf = createJapanesePdf("plain ascii 123", 18f);

        try (PDDocument document = PDDocument.load(pdf);
             PDDocument tempDoc = new PDDocument()) {
            PDPage normalized = PdfFontNormalizer.normalize(document.getPage(0), tempDoc, true, true);

            assertEquals(nfkc("plain ascii 123"), nfkc(decodedTextGovernedBy(normalized, "Helv")));
        }
    }

    // --- Helper methods ---

    /**
     * PDFBox-generated ToUnicode CMaps map ideographs sharing a glyph with a
     * Kangxi radical to the radical codepoint (e.g. 日 → U+2F47). NFKC folds
     * radicals back to unified ideographs so comparisons are stable.
     */
    private static String nfkc(String s) {
        return s == null ? null : java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
    }

    private BufferedImage renderNormalizedFirstPage(File pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            return PdfFontNormalizer.renderNormalized(document.getPage(0), TEST_DPI, true, true);
        }
    }

    private void assertImagesMatch(BufferedImage a, BufferedImage b) {
        assertEquals("Width mismatch", a.getWidth(), b.getWidth());
        assertEquals("Height mismatch", a.getHeight(), b.getHeight());

        int totalPixels = a.getWidth() * a.getHeight();
        int diffPixels = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    diffPixels++;
                }
            }
        }
        double diffPercent = (diffPixels * 100.0) / totalPixels;
        assertTrue("Images differ by " + String.format("%.2f", diffPercent) + "% (threshold 1%)",
                diffPercent < 1.0);
    }

    private void assertImagesDiffer(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return; // Different dimensions = different images
        }
        int totalPixels = a.getWidth() * a.getHeight();
        int diffPixels = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    diffPixels++;
                }
            }
        }
        assertTrue("Images should differ but are identical", diffPixels > 0);
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

    /** Creates a single-page PDF whose text is drawn with embedded Noto (Type0/Identity-H). */
    private File createJapanesePdf(String text, float size) throws IOException {
        File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.font.PDType0Font noto = NotoFontProvider.load(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(noto, size);
                cs.newLineAtOffset(72, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(file);
        }
        return file;
    }

    /**
     * Creates a Japanese PDF containing a raw CID (0xFFFE) that has no glyph in
     * Noto Sans JP, whose /ToUnicode entry is hand-patched to an emoji codepoint
     * (U+1F600) flanked by real "日本"/"語" text encoded normally.
     *
     * NOTE: deviates from the brief, which built this fixture via
     * createJapanesePdf("日本😀語", size). That fails at fixture-creation time:
     * PDType0Font.encode()/PDPageContentStream.showText() throw
     * IllegalArgumentException for any codepoint missing from the font's cmap
     * (verified: Noto Sans JP has no emoji glyphs), so a PDF containing an
     * emoji encoded with Noto cannot be built through the normal text API —
     * the exact case the test needs never gets past fixture setup, regardless
     * of the normalizer's own behavior. Real-world PDFs can still end up with
     * a ToUnicode entry pointing at a codepoint the embedded font can't
     * render (e.g. post-subsetting tool bugs), so we reproduce that directly:
     * write the raw CID by hand, then patch the font's auto-generated
     * /ToUnicode CMap stream (PDFBox writes one covering the whole glyph
     * range at save time) to map it to U+1F600.
     */
    private static final int UNMAPPABLE_CID = 0xFFFE; // outside Noto Sans JP's real glyph range
    private static final String EMOJI_UTF16BE_HEX = "D83DDE00"; // surrogate pair for U+1F600

    private File createJapanesePdfWithUnrepresentableGlyph(float size) throws IOException {
        COSName fontRes = COSName.getPDFName("F1");
        File unpatched = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.font.PDType0Font noto = NotoFontProvider.load(doc);
            page.setResources(new org.apache.pdfbox.pdmodel.PDResources());
            page.getResources().put(fontRes, noto);

            byte[] before = noto.encode("日本");
            byte[] unmappableCid = { (byte) (UNMAPPABLE_CID >> 8), (byte) UNMAPPABLE_CID };
            byte[] after = noto.encode("語");

            org.apache.pdfbox.cos.COSStream contentStream = new org.apache.pdfbox.cos.COSStream();
            try (java.io.OutputStream out = contentStream.createOutputStream()) {
                out.write(("BT /F1 " + size + " Tf 72 700 Td (").getBytes("ISO-8859-1"));
                writeEscapedLiteral(out, before);
                writeEscapedLiteral(out, unmappableCid);
                writeEscapedLiteral(out, after);
                out.write(") Tj ET".getBytes("ISO-8859-1"));
            }
            page.getCOSObject().setItem(COSName.CONTENTS, contentStream);
            doc.save(unpatched);
        }

        File patched = tempFolder.newFile();
        try (PDDocument doc = PDDocument.load(unpatched)) {
            org.apache.pdfbox.pdmodel.font.PDFont font = doc.getPage(0).getResources().getFont(fontRes);
            org.apache.pdfbox.cos.COSStream toUnicode = (org.apache.pdfbox.cos.COSStream)
                    font.getCOSObject().getDictionaryObject(COSName.getPDFName("ToUnicode"));
            String cmap = readAllAsLatin1(toUnicode);
            String bfCharEntry = String.format("1 beginbfchar\n<%04X> <%s>\nendbfchar\n",
                    UNMAPPABLE_CID, EMOJI_UTF16BE_HEX);
            String withEntry = cmap.replace("endcmap", bfCharEntry + "endcmap");
            toUnicode.removeItem(COSName.FILTER);
            toUnicode.removeItem(COSName.getPDFName("DecodeParms"));
            try (java.io.OutputStream out = toUnicode.createOutputStream()) {
                out.write(withEntry.getBytes("ISO-8859-1"));
            }
            doc.save(patched);
        }
        return patched;
    }

    private void writeEscapedLiteral(java.io.OutputStream out, byte[] bytes) throws IOException {
        for (byte b : bytes) {
            int unsigned = b & 0xff;
            if (unsigned == '(' || unsigned == ')' || unsigned == '\\') {
                out.write('\\');
            }
            out.write(unsigned);
        }
    }

    private String readAllAsLatin1(org.apache.pdfbox.cos.COSStream stream) throws IOException {
        try (java.io.InputStream in = stream.createInputStream()) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) != -1) {
                bos.write(buf, 0, read);
            }
            return new String(bos.toByteArray(), "ISO-8859-1");
        }
    }

    /**
     * Walks the normalized page's content stream and returns the concatenated
     * decoded text of every show op whose governing Tf names the given font.
     * Decoding uses the page's own resources, so it works for any font.
     */
    private String decodedTextGovernedBy(PDPage page, String fontName) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(page);
        parser.parse();
        List<Object> tokens = parser.getTokens();

        StringBuilder sb = new StringBuilder();
        COSName governing = null;
        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (!(token instanceof Operator)) continue;
            String op = ((Operator) token).getName();
            if ("Tf".equals(op)) {
                governing = (COSName) tokens.get(i - 2);
            } else if (("Tj".equals(op) || "'".equals(op)) && governing != null
                    && fontName.equals(governing.getName())) {
                sb.append(PdfTextDecoder.decode(
                        (org.apache.pdfbox.cos.COSString) tokens.get(i - 1),
                        page.getResources().getFont(governing)));
            } else if ("TJ".equals(op) && governing != null
                    && fontName.equals(governing.getName())) {
                sb.append(PdfTextDecoder.decode(
                        (org.apache.pdfbox.cos.COSArray) tokens.get(i - 1),
                        page.getResources().getFont(governing)));
            }
        }
        return nfkc(sb.toString());
    }

    /** Concatenated decoded text of every show op on the page, regardless of font. */
    private String allDecodedText(PDPage page) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(page);
        parser.parse();
        List<Object> tokens = parser.getTokens();

        StringBuilder sb = new StringBuilder();
        COSName governing = null;
        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (!(token instanceof Operator)) continue;
            String op = ((Operator) token).getName();
            if ("Tf".equals(op)) {
                governing = (COSName) tokens.get(i - 2);
            } else if (("Tj".equals(op) || "'".equals(op)) && governing != null) {
                sb.append(PdfTextDecoder.decode(
                        (org.apache.pdfbox.cos.COSString) tokens.get(i - 1),
                        page.getResources().getFont(governing)));
            } else if ("TJ".equals(op) && governing != null) {
                sb.append(PdfTextDecoder.decode(
                        (org.apache.pdfbox.cos.COSArray) tokens.get(i - 1),
                        page.getResources().getFont(governing)));
            }
        }
        return nfkc(sb.toString());
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
