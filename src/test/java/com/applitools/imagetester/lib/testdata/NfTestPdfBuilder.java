package com.applitools.imagetester.lib.testdata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Builds the synthetic PDF corpus used to exercise -nf font normalization.
 * Construction only: no assertions, no rendering. Layout coordinates are
 * fixed constants so document pairs differ ONLY in typography.
 */
public final class NfTestPdfBuilder {

    /** What the differences-encoded fixture SAYS when its encoding is honored. */
    public static final String DIFFERENCES_TEXT = "Hello";
    /** The byte codes actually written to its content stream. */
    public static final String DIFFERENCES_BYTES = "ABCDE";

    /** English text with the punctuation that corrupts in the field:
     *  U+2019 right single quote plus the ASCII apostrophe. */
    public static final String SUBSET_TEXT =
            "We\u2019re verifying 'subset' text survives normalization.";

    private static final int DIFFERENCES_FIRST_CODE = 65; // 'A'
    private static final String[] DIFFERENCES_GLYPHS = {"H", "e", "l", "l", "o"};
    private static final String FONT_RESOURCE = "/fonts/NotoSans-Regular.ttf";

    private NfTestPdfBuilder() {
    }

    /**
     * Standard-14 Helvetica with an /Encoding /Differences map: byte codes
     * 65-69 ("ABCDE") draw the glyphs H,e,l,l,o. Readers honoring the
     * encoding see "Hello"; reinterpreting the bytes as WinAnsi sees "ABCDE".
     * Built by hand via COSDictionary because PDFBox 2.0.x has no public
     * constructor for a standard-14 font with a custom Differences map.
     */
    public static File createDifferencesEncoded(File dir, String fileName) throws IOException {
        COSDictionary encoding = new COSDictionary();
        encoding.setItem(COSName.TYPE, COSName.getPDFName("Encoding"));
        encoding.setItem(COSName.BASE_ENCODING, COSName.WIN_ANSI_ENCODING);
        COSArray differences = new COSArray();
        differences.add(COSInteger.get(DIFFERENCES_FIRST_CODE));
        for (String glyph : DIFFERENCES_GLYPHS) {
            differences.add(COSName.getPDFName(glyph));
        }
        encoding.setItem(COSName.DIFFERENCES, differences);

        COSDictionary fontDict = new COSDictionary();
        fontDict.setItem(COSName.TYPE, COSName.FONT);
        fontDict.setItem(COSName.SUBTYPE, COSName.TYPE1);
        fontDict.setItem(COSName.BASE_FONT, COSName.getPDFName("Helvetica"));
        fontDict.setItem(COSName.ENCODING, encoding);

        return writeRawTextPdf(dir, fileName, PDFontFactory.createFont(fontDict),
                "BT /F1 12 Tf 72 700 Td (" + DIFFERENCES_BYTES + ") Tj ET");
    }

    /** Plain WinAnsi Helvetica drawing the literal word at the same position and size. */
    public static File createHelloWinAnsi(File dir, String fileName) throws IOException {
        return writeRawTextPdf(dir, fileName, PDType1Font.HELVETICA,
                "BT /F1 12 Tf 72 700 Td (" + DIFFERENCES_TEXT + ") Tj ET");
    }

    /**
     * Loads the bundled Noto Sans as an embedded subset - PDFBox emits it as
     * Type0/Identity-H, the shape Word and LaTeX produce for real documents.
     */
    public static PDType0Font loadNotoSans(PDDocument doc) throws IOException {
        return PDType0Font.load(doc, new ByteArrayInputStream(readResource(FONT_RESOURCE)), true);
    }

    /** One page of Identity-H subset text; extraction works via the ToUnicode CMap. */
    public static File createSubsetIdentityH(File dir, String fileName) throws IOException {
        File file = new File(dir, fileName);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType0Font noto = loadNotoSans(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(noto, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(SUBSET_TEXT);
                cs.endText();
            }
            doc.save(file);
        }
        return file;
    }

    /** Lines containing U+2019 and the ASCII apostrophe - the exact
     *  punctuation that corrupts in the field. */
    private static final String[] MISMATCH_LINES = {
            "Dear customer, we\u2019ve reviewed your 'priority' request.",
            "You\u2019ll receive the updated schedule this week.",
    };

    /** MISMATCH_LINES repeated 4x (8 lines total) so the inked area is
     *  dense enough to clear the 1% match threshold - two lines alone
     *  differ by under 1%, making the pin near-vacuous. */
    private static final float[] MISMATCH_LINE_Y_POSITIONS =
            {700, 680, 660, 640, 620, 600, 580, 560};

    /** WinAnsi member of the encoding-mismatch pair. */
    public static File createEncodingMismatchWinAnsi(File dir, String fileName) throws IOException {
        File file = new File(dir, fileName);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < MISMATCH_LINE_Y_POSITIONS.length; i++) {
                    textAt(cs, PDType1Font.HELVETICA, 12, 72, MISMATCH_LINE_Y_POSITIONS[i],
                            MISMATCH_LINES[i % MISMATCH_LINES.length]);
                }
            }
            doc.save(file);
        }
        return file;
    }

    /** Identity-H member: same text and anchors through the embedded subset font. */
    public static File createEncodingMismatchIdentityH(File dir, String fileName) throws IOException {
        File file = new File(dir, fileName);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType0Font noto = loadNotoSans(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < MISMATCH_LINE_Y_POSITIONS.length; i++) {
                    cs.beginText();
                    cs.setFont(noto, 13);
                    cs.newLineAtOffset(72, MISMATCH_LINE_Y_POSITIONS[i]);
                    cs.showText(MISMATCH_LINES[i % MISMATCH_LINES.length]);
                    cs.endText();
                }
            }
            doc.save(file);
        }
        return file;
    }

    /**
     * Writes a single-page PDF whose content stream is the given raw bytes,
     * with the font registered as /F1. Raw streams are used where showText
     * would encode through the very font the fixture needs to control.
     */
    private static File writeRawTextPdf(File dir, String fileName, PDFont font,
                                        String rawContent) throws IOException {
        File file = new File(dir, fileName);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("F1"), font);
            page.setResources(resources);
            PDStream contents = new PDStream(doc);
            try (OutputStream out = contents.createOutputStream()) {
                out.write(rawContent.getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(contents);
            doc.save(file);
        }
        return file;
    }

    /** LETTER page with /Rotate 90 - -nf currently loses the rotation. */
    public static File createRotated(File dir, String fileName) throws IOException {
        File file = new File(dir, fileName);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setRotation(90);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Rotated page fixture");
                cs.endText();
            }
            doc.save(file);
        }
        return file;
    }

    /** MediaBox 612x792 with a centered 306x396 CropBox - -nf currently loses the crop. */
    public static File createCropBoxed(File dir, String fileName) throws IOException {
        File file = new File(dir, fileName);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setCropBox(new PDRectangle(153, 198, 306, 396));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(200, 500);
                cs.showText("CropBox fixture");
                cs.endText();
            }
            doc.save(file);
        }
        return file;
    }

    /**
     * Raw-stream twin documents for the spacing characterization: identical
     * text, one with Tc/Tz/Ts spacing operators the normalizer does not touch.
     */
    public static File createSpacingDoc(File dir, String fileName,
                                        boolean withSpacingOps) throws IOException {
        String ops = withSpacingOps ? "1.5 Tc 90 Tz 3 Ts " : "";
        return writeRawTextPdf(dir, fileName, PDType1Font.HELVETICA,
                "BT /F1 12 Tf " + ops + "72 700 Td (Spacing operators test line) Tj ET");
    }

    /**
     * Typography bundle for realism pairs. Layout coordinates are fixed
     * constants in each create* method - ONLY these values differ between
     * the -a and -b member of a pair.
     */
    public static final class Theme {
        public final PDType1Font body;
        public final PDType1Font bold;
        public final float bodySize;
        public final float headingSize;
        public final float leading;

        public Theme(PDType1Font body, PDType1Font bold,
                     float bodySize, float headingSize, float leading) {
            this.body = body;
            this.bold = bold;
            this.bodySize = bodySize;
            this.headingSize = headingSize;
            this.leading = leading;
        }
    }

    public static final Theme THEME_A =
            new Theme(PDType1Font.HELVETICA, PDType1Font.HELVETICA_BOLD, 10f, 16f, 12f);
    public static final Theme THEME_B =
            new Theme(PDType1Font.TIMES_ROMAN, PDType1Font.TIMES_BOLD, 11f, 20f, 16.5f);

    private static final String[][] INVOICE_ROWS = {
            {"Precision vise, 4 inch", "2", "158.00"},
            {"Carbide end mill set", "1", "214.50"},
            {"Dial indicator, 0.001", "3", "87.25"},
            {"Machinist square set", "1", "129.99"},
            {"Parallel bar pair", "4", "42.00"},
            {"Way oil, 1 gallon", "2", "31.75"},
    };

    /**
     * Dense single page: heading, address block, six-row table at fixed
     * column positions, vector logo, footer rule. The amount column sits at
     * a fixed x rather than being width-aligned - width alignment would move
     * glyph positions with the theme font and break pair comparability.
     */
    public static File createInvoice(File dir, String fileName, Theme t) throws IOException {
        File file = new File(dir, fileName);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                textAt(cs, t.bold, t.headingSize, 72, 730,
                        "ACME Tooling \u2014 Invoice 10247");

                textAt(cs, t.body, t.bodySize, 72, 690, "Meridian Fabrication Ltd.");
                textAt(cs, t.body, t.bodySize, 72, 676, "410 Foundry Row, Building C");
                textAt(cs, t.body, t.bodySize, 72, 662, "Dayton, OH 45402");

                textAt(cs, t.bold, t.bodySize, 72, 600, "Description");
                textAt(cs, t.bold, t.bodySize, 340, 600, "Qty");
                textAt(cs, t.bold, t.bodySize, 430, 600, "Amount (USD)");
                for (int i = 0; i < INVOICE_ROWS.length; i++) {
                    float y = 584 - i * 16;
                    textAt(cs, t.body, t.bodySize, 72, y, INVOICE_ROWS[i][0]);
                    textAt(cs, t.body, t.bodySize, 340, y, INVOICE_ROWS[i][1]);
                    textAt(cs, t.body, t.bodySize, 430, y, INVOICE_ROWS[i][2]);
                }
                textAt(cs, t.bold, t.bodySize, 340, 460, "Total");
                textAt(cs, t.bold, t.bodySize, 430, 460, "663.49");

                // Vector logo: teal block plus triangle, top right.
                cs.setNonStrokingColor(0, 128, 128);
                cs.addRect(470, 705, 70, 28);
                cs.fill();
                cs.moveTo(470, 705);
                cs.lineTo(455, 719);
                cs.lineTo(470, 733);
                cs.closePath();
                cs.fill();
                cs.setNonStrokingColor(0, 0, 0);

                // Footer rule and terms line.
                cs.moveTo(72, 90);
                cs.lineTo(540, 90);
                cs.stroke();
                textAt(cs, t.body, t.bodySize, 72, 74,
                        "Payment due within 30 days. Quote invoice number on remittance.");
            }
            doc.save(file);
        }
        return file;
    }

    private static final String[] REPORT_P1_BODY = {
            "This report summarizes the quarterly calibration cycle for the",
            "machining floor. All twelve stations completed the cycle inside",
            "the scheduled maintenance window, and no station required more",
            "than one adjustment pass to return to tolerance.",
            "",
            "Measurement drift remained within the accepted band on every",
            "station except grinder G-4, which exceeded its axial tolerance",
            "and was recalibrated on site the same day.",
    };

    private static final String[] REPORT_P1_BULLETS = {
            "\u2022 Twelve stations calibrated, one adjustment pass maximum",
            "\u2022 G-4 axial drift corrected and re-verified same day",
            "\u2022 Reference gauge set due for renewal next quarter",
            "\u2022 No out-of-tolerance parts shipped during the cycle",
    };

    private static final String[] REPORT_P2_BODY = {
            "Follow-up actions concentrate on the aging reference gauges.",
            "Replacement blocks are on order and arrive before the next",
            "cycle begins. Until then, stations verify against the secondary",
            "set after every shift change.",
            "",
            "The calibration procedure itself remains unchanged from the",
            "revision approved in March.",
    };

    private static final String IMAGE_RESOURCE = "/fixtures/sample.png";

    private static final String[] LETTER_BODY = {
            "Thank you for the site visit last Tuesday. The revised drawings",
            "you left with our team answered the remaining questions about",
            "the mounting rail tolerances.",
            "",
            "We will confirm the delivery schedule by the end of the week.",
    };

    /** Two pages: heading, TL/T*-driven paragraphs, bullets, running footer. */
    public static File createReport(File dir, String fileName, Theme t) throws IOException {
        File file = new File(dir, fileName);
        try (PDDocument doc = new PDDocument()) {
            PDPage page1 = new PDPage(PDRectangle.LETTER);
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                textAt(cs, t.bold, t.headingSize, 72, 730, "Quarterly Calibration Report");
                paragraphAt(cs, t, 72, 690, REPORT_P1_BODY);
                paragraphAt(cs, t, 90, 540, REPORT_P1_BULLETS);
                textAt(cs, t.body, t.bodySize, 72, 40, "Page 1 of 2");
            }

            PDPage page2 = new PDPage(PDRectangle.LETTER);
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                textAt(cs, t.bold, t.headingSize, 72, 730, "Follow-up Actions");
                paragraphAt(cs, t, 72, 690, REPORT_P2_BODY);
                textAt(cs, t.body, t.bodySize, 72, 40, "Page 2 of 2");
            }
            doc.save(file);
        }
        return file;
    }

    /** Short letter with an embedded raster image above the salutation. */
    public static File createLetter(File dir, String fileName, Theme t) throws IOException {
        File file = new File(dir, fileName);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDImageXObject image = PDImageXObject.createFromByteArray(
                    doc, readResource(IMAGE_RESOURCE), "sample");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(image, 72, 620, 120, 90);
                textAt(cs, t.body, t.bodySize, 72, 580, "Dear Ms. Okafor,");
                paragraphAt(cs, t, 72, 550, LETTER_BODY);
                textAt(cs, t.body, t.bodySize, 72, 430, "Kind regards,");
                textAt(cs, t.bold, t.bodySize, 72, 402, "R. Halvorsen, Accounts");
            }
            doc.save(file);
        }
        return file;
    }

    /** One BT/ET text run at an absolute position - keeps pair layouts fixed. */
    private static void textAt(PDPageContentStream cs, PDType1Font font, float size,
                               float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    /** Multi-line text run driven by TL/T* - the operators -nf normalizes. */
    private static void paragraphAt(PDPageContentStream cs, Theme t,
                                    float x, float y, String[] lines) throws IOException {
        cs.beginText();
        cs.setFont(t.body, t.bodySize);
        cs.setLeading(t.leading);
        cs.newLineAtOffset(x, y);
        for (String line : lines) {
            cs.showText(line);
            cs.newLine();
        }
        cs.endText();
    }

    private static byte[] readResource(String classpathPath) throws IOException {
        try (InputStream in = NfTestPdfBuilder.class.getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IOException("Resource not on test classpath: " + classpathPath);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }
}
