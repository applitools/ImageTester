package com.applitools.imagetester.lib;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PdfComparePrecheckerTest {

    private static final PdfComparePrechecker.MessageStyle GUI = PdfComparePrechecker.MessageStyle.GUI;
    private static final PdfComparePrechecker.MessageStyle CLI = PdfComparePrechecker.MessageStyle.CLI;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** Authors a PDF whose pages have the given {width,height} sizes in points. */
    private File pdf(String name, float[]... pageSizes) throws Exception {
        File f = tmp.newFile(name);
        try (PDDocument doc = new PDDocument()) {
            for (float[] size : pageSizes) doc.addPage(new PDPage(new PDRectangle(size[0], size[1])));
            doc.save(f);
        }
        return f;
    }

    private File encryptedPdf(String name) throws Exception {
        File f = tmp.newFile(name);
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(f);
        }
        return f;
    }

    private static final float[] A4 = { 595f, 842f };
    private static final float[] LETTER = { 612f, 792f };
    private static final float[] A4_ROTATED = { 842f, 595f };

    private static List<String> codes(List<PdfComparePrechecker.Finding> findings) {
        return findings.stream().map(f -> f.code).collect(Collectors.toList());
    }

    private static PdfComparePrechecker.Finding byCode(List<PdfComparePrechecker.Finding> findings, String code) {
        return findings.stream().filter(f -> f.code.equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("no finding with code " + code + " in " + codes(findings)));
    }

    @Test
    public void identicalPdfsReportOnlyIdenticalContent() throws Exception {
        File a = pdf("a.pdf", A4, A4);
        File b = tmp.newFile("b.pdf");
        Files.write(b.toPath(), Files.readAllBytes(a.toPath()));
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, b, new Config(), GUI);
        assertEquals(List.of("identical-content"), codes(findings));
    }

    @Test
    public void identicalContentIsInfoSeverity() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = tmp.newFile("b.pdf");
        Files.write(b.toPath(), Files.readAllBytes(a.toPath()));
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, b, new Config(), GUI);
        assertEquals(PdfComparePrechecker.Severity.INFO, byCode(findings, "identical-content").severity);
    }

    @Test
    public void matchingDistinctPdfsReportNothing() throws Exception {
        File c = pdf("c.pdf", A4);
        // d.pdf differs by a sub-pixel size so the two saves can never be byte-identical
        // (PDFBox can emit identical bytes for same-shaped docs, which made this test flaky)
        // while still rendering to the same pixel dimensions as A4 at 250 DPI.
        File d = pdf("d.pdf", new float[] { 594.90f, 842.10f });
        assertEquals(List.of(), codes(PdfComparePrechecker.check(c, d, new Config(), GUI)));
    }

    @Test
    public void samePathReportsSameFileWarning() throws Exception {
        File a = pdf("a.pdf", A4);
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, a, new Config(), GUI);
        assertEquals(PdfComparePrechecker.Severity.WARNING, byCode(findings, "same-file").severity);
    }

    @Test
    public void samePathDoesNotAlsoReportIdenticalContent() throws Exception {
        File a = pdf("a.pdf", A4);
        assertTrue(!codes(PdfComparePrechecker.check(a, a, new Config(), GUI)).contains("identical-content"));
    }

    @Test
    public void corruptPdfReportsUnreadableError() throws Exception {
        File a = pdf("a.pdf", A4);
        File junk = tmp.newFile("junk.pdf");
        Files.write(junk.toPath(), "not a pdf".getBytes());
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, junk, new Config(), GUI);
        assertEquals(PdfComparePrechecker.Severity.ERROR, byCode(findings, "doc-unreadable").severity);
    }

    @Test
    public void encryptedPdfWithoutPasswordReportsEncryptedError() throws Exception {
        File a = pdf("a.pdf", A4);
        File enc = encryptedPdf("enc.pdf");
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, enc, new Config(), GUI);
        assertEquals(PdfComparePrechecker.Severity.ERROR, byCode(findings, "encrypted").severity);
    }

    @Test
    public void encryptedPdfWithCorrectPasswordReportsNothing() throws Exception {
        File enc1 = encryptedPdf("enc1.pdf");
        File enc2 = encryptedPdf("enc2.pdf");
        Config config = new Config();
        config.pdfPass = "user-secret";
        assertEquals(List.of(), codes(PdfComparePrechecker.check(enc1, enc2, config, GUI)));
    }

    @Test
    public void encryptedMessageNamesThePasswordFlagInCliStyle() throws Exception {
        File a = pdf("a.pdf", A4);
        File enc = encryptedPdf("enc.pdf");
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, enc, new Config(), CLI);
        assertTrue(byCode(findings, "encrypted").message.contains("-pp"));
    }

    @Test
    public void encryptedMessageNamesOptionsInGuiStyle() throws Exception {
        File a = pdf("a.pdf", A4);
        File enc = encryptedPdf("enc.pdf");
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, enc, new Config(), GUI);
        assertTrue(byCode(findings, "encrypted").message.contains("PDF password in Options"));
    }

    @Test
    public void zeroPagePdfReportsError() throws Exception {
        File a = pdf("a.pdf", A4);
        File empty = pdf("empty.pdf");
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, empty, new Config(), GUI);
        assertEquals(PdfComparePrechecker.Severity.ERROR, byCode(findings, "zero-pages").severity);
    }

    @Test
    public void unreadableDocSkipsMismatchChecks() throws Exception {
        File junk = tmp.newFile("junk.pdf");
        Files.write(junk.toPath(), "not a pdf".getBytes());
        File b = pdf("b.pdf", A4, A4);
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(junk, b, new Config(), GUI);
        assertEquals(List.of("doc-unreadable"), codes(findings));
    }

    @Test
    public void pageCountMismatchReportsDoc1Count() throws Exception {
        File a = pdf("a.pdf", A4, A4, A4);
        File b = pdf("b.pdf", A4);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "page-count-mismatch")
                .message.contains("3 page(s)"));
    }

    @Test
    public void pageCountMismatchReportsDoc2Count() throws Exception {
        File a = pdf("a.pdf", A4, A4, A4);
        File b = pdf("b.pdf", A4);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "page-count-mismatch")
                .message.contains("1 page(s)"));
    }

    @Test
    public void pageCountMismatchNudgesSelectedPagesFlagInCliStyle() throws Exception {
        File a = pdf("a.pdf", A4, A4);
        File b = pdf("b.pdf", A4);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config(), CLI), "page-count-mismatch")
                .message.contains("-sp"));
    }

    @Test
    public void pageCountMismatchNudgesOptionsInGuiStyle() throws Exception {
        File a = pdf("a.pdf", A4, A4);
        File b = pdf("b.pdf", A4);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "page-count-mismatch")
                .message.contains("Selected pages in Options"));
    }

    @Test
    public void dimensionMismatchIsWarningByDefault() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        assertEquals(PdfComparePrechecker.Severity.WARNING,
                byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch").severity);
    }

    @Test
    public void dimensionMismatchListsThePageNumbers() throws Exception {
        File a = pdf("a.pdf", A4, A4, A4);
        File b = pdf("b.pdf", A4, LETTER, LETTER);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch")
                .message.contains("2, 3"));
    }

    @Test
    public void dimensionMismatchListsFirstFivePages() throws Exception {
        float[][] sevenA4 = { A4, A4, A4, A4, A4, A4, A4 };
        float[][] sevenLetter = { LETTER, LETTER, LETTER, LETTER, LETTER, LETTER, LETTER };
        File a = pdf("a.pdf", sevenA4);
        File b = pdf("b.pdf", sevenLetter);
        String msg = byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch").message;
        assertTrue(msg.contains("1, 2, 3, 4, 5, ..."));
    }

    @Test
    public void dimensionMismatchReportsTotalPageCount() throws Exception {
        float[][] sevenA4 = { A4, A4, A4, A4, A4, A4, A4 };
        float[][] sevenLetter = { LETTER, LETTER, LETTER, LETTER, LETTER, LETTER, LETTER };
        File a = pdf("a.pdf", sevenA4);
        File b = pdf("b.pdf", sevenLetter);
        String msg = byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch").message;
        assertTrue(msg.contains("7 page(s)"));
    }

    @Test
    public void rotatedPageGetsOrientationHint() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", A4_ROTATED);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch")
                .message.contains("rotated"));
    }

    @Test
    public void subPointDifferenceThatChangesRenderedPixelsWarns() throws Exception {
        File a = pdf("a.pdf", new float[] { 1191.00f, 842.25f });
        File b = pdf("b.pdf", new float[] { 1190.70f, 842.00f });
        assertTrue(codes(PdfComparePrechecker.check(a, b, new Config(), GUI)).contains("dimension-mismatch"));
    }

    @Test
    public void subPointDifferenceWithSameRenderedPixelsDoesNotWarn() throws Exception {
        File a = pdf("a.pdf", new float[] { 594.80f, 842.00f });
        File b = pdf("b.pdf", new float[] { 595.00f, 842.00f });
        assertTrue(!codes(PdfComparePrechecker.check(a, b, new Config(), GUI)).contains("dimension-mismatch"));
    }

    // PDFBox clips CropBox to the intersection with MediaBox (PDPage.clipToMediaBox), so the crop
    // box can never exceed the media box on either axis. To exercise "crop box governs" without
    // that clipping kicking in, the media box here is an oversized container the crop box fits
    // inside -- its exact size is irrelevant and must NOT match either LETTER or A4.
    private static final float[] OVERSIZED_MEDIA_BOX = { 1000f, 1200f };

    @Test
    public void cropBoxGovernsRenderedSizeNotMediaBox() throws Exception {
        File a = tmp.newFile("a.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(OVERSIZED_MEDIA_BOX[0], OVERSIZED_MEDIA_BOX[1]));
            page.setCropBox(new PDRectangle(LETTER[0], LETTER[1]));
            doc.addPage(page);
            doc.save(a);
        }
        File b = pdf("b.pdf", LETTER);
        assertTrue(!codes(PdfComparePrechecker.check(a, b, new Config(), GUI)).contains("dimension-mismatch"));
    }

    @Test
    public void cropBoxGovernsRenderedSizeMismatchAgainstPlainMediaBox() throws Exception {
        File a = tmp.newFile("a.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(OVERSIZED_MEDIA_BOX[0], OVERSIZED_MEDIA_BOX[1]));
            page.setCropBox(new PDRectangle(LETTER[0], LETTER[1]));
            doc.addPage(page);
            doc.save(a);
        }
        File b = pdf("b.pdf", A4);
        assertTrue(codes(PdfComparePrechecker.check(a, b, new Config(), GUI)).contains("dimension-mismatch"));
    }

    @Test
    public void pageRotationAttributeGetsOrientationHint() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = tmp.newFile("b.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(A4[0], A4[1]));
            page.setRotation(90);
            doc.addPage(page);
            doc.save(b);
        }
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch")
                .message.contains("rotated"));
    }

    @Test
    public void selectedPagesRestrictsDimensionCheck() throws Exception {
        File a = pdf("a.pdf", A4, LETTER, A4);
        File b = pdf("b.pdf", A4, A4, A4);
        Config config = new Config();
        config.pages = "1,3";
        assertTrue(!codes(PdfComparePrechecker.check(a, b, config, GUI)).contains("dimension-mismatch"));
    }

    @Test
    public void viewportOverrideDowngradesDimensionMismatchToInfo() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        Config config = new Config();
        config.setViewport("1000x600");
        assertEquals(PdfComparePrechecker.Severity.INFO,
                byCode(PdfComparePrechecker.check(a, b, config, GUI), "dimension-mismatch").severity);
    }

    @Test
    public void matchSizeOverrideDowngradesDimensionMismatchToInfo() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        Config config = new Config();
        config.setMatchSize("1000x600");
        assertEquals(PdfComparePrechecker.Severity.INFO,
                byCode(PdfComparePrechecker.check(a, b, config, GUI), "dimension-mismatch").severity);
    }

    @Test
    public void pdfTrimAddsPreTrimNote() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        Config config = new Config();
        config.setPdfTrim("auto");
        assertTrue(byCode(PdfComparePrechecker.check(a, b, config, GUI), "dimension-mismatch")
                .message.contains("before trimming"));
    }

    @Test
    public void nonPdfInputsOnlyGetFileLevelChecks() throws Exception {
        File img = tmp.newFile("a.png");
        Files.write(img.toPath(), new byte[] { 1, 2, 3 });
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(img, img, new Config(), GUI);
        assertEquals(List.of("same-file"), codes(findings));
    }

    @Test
    public void dimensionMismatchGuiMessageDoesNotMentionCliFlags() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        assertTrue(!byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch")
                .message.contains("-ms"));
    }

    @Test
    public void dimensionMessageIsAsciiSafeForConsolesInCliStyle() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        String message = byCode(PdfComparePrechecker.check(a, b, new Config(), CLI), "dimension-mismatch").message;
        assertTrue(message.chars().allMatch(c -> c < 128));
    }

    @Test
    public void dimensionMessageIsAsciiSafeForConsolesInGuiStyle() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        String message = byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch").message;
        assertTrue(message.chars().allMatch(c -> c < 128));
    }

    // A4 at 250 DPI renders 2065x2923 px, LETTER renders 2125x2750 px (floor(points * 250/72)).

    @Test
    public void dimensionMismatchCarriesDoc1SizeAsMatchSizeValue() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        assertEquals("2065x2923",
                byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch")
                        .data.get("doc1SizePx"));
    }

    @Test
    public void dimensionMismatchCarriesDoc2SizeAsMatchSizeValue() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        assertEquals("2125x2750",
                byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch")
                        .data.get("doc2SizePx"));
    }

    @Test
    public void dimensionSizesComeFromTheFirstMismatchedPage() throws Exception {
        File a = pdf("a.pdf", A4, A4);
        File b = pdf("b.pdf", A4, LETTER);
        assertEquals("2065x2923",
                byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch")
                        .data.get("doc1SizePx"));
    }

    @Test
    public void dimensionMismatchWithSizeOverrideCarriesNoSuggestedSizes() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        Config config = new Config();
        config.setMatchSize("1000x600");
        assertTrue(byCode(PdfComparePrechecker.check(a, b, config, GUI), "dimension-mismatch")
                .data.isEmpty());
    }

    @Test
    public void dimensionDataListsTheMismatchedPages() throws Exception {
        File a = pdf("a.pdf", A4, A4, A4);
        File b = pdf("b.pdf", A4, LETTER, LETTER);
        assertEquals("2, 3",
                byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "dimension-mismatch")
                        .data.get("pages"));
    }

    @Test
    public void pageCountDataCarriesDoc1Count() throws Exception {
        File a = pdf("a.pdf", A4, A4, A4);
        File b = pdf("b.pdf", A4);
        assertEquals("3",
                byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "page-count-mismatch")
                        .data.get("doc1Pages"));
    }

    @Test
    public void pageCountDataCarriesDoc2Count() throws Exception {
        File a = pdf("a.pdf", A4, A4, A4);
        File b = pdf("b.pdf", A4);
        assertEquals("1",
                byCode(PdfComparePrechecker.check(a, b, new Config(), GUI), "page-count-mismatch")
                        .data.get("doc2Pages"));
    }

    @Test
    public void errorFindingsCarryNoData() throws Exception {
        File a = pdf("a.pdf", A4);
        File enc = encryptedPdf("enc.pdf");
        assertTrue(byCode(PdfComparePrechecker.check(a, enc, new Config(), GUI), "encrypted")
                .data.isEmpty());
    }
}
