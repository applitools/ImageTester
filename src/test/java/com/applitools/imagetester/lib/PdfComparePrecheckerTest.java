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
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, b, new Config());
        assertEquals(List.of("identical-content"), codes(findings));
    }

    @Test
    public void identicalContentIsInfoSeverity() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = tmp.newFile("b.pdf");
        Files.write(b.toPath(), Files.readAllBytes(a.toPath()));
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, b, new Config());
        assertEquals(PdfComparePrechecker.Severity.INFO, byCode(findings, "identical-content").severity);
    }

    @Test
    public void matchingDistinctPdfsReportNothing() throws Exception {
        File c = pdf("c.pdf", A4);
        File d = pdf("d.pdf", A4);
        assertEquals(List.of(), codes(PdfComparePrechecker.check(c, d, new Config())));
    }

    @Test
    public void samePathReportsSameFileWarning() throws Exception {
        File a = pdf("a.pdf", A4);
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, a, new Config());
        assertEquals(PdfComparePrechecker.Severity.WARNING, byCode(findings, "same-file").severity);
    }

    @Test
    public void samePathDoesNotAlsoReportIdenticalContent() throws Exception {
        File a = pdf("a.pdf", A4);
        assertTrue(!codes(PdfComparePrechecker.check(a, a, new Config())).contains("identical-content"));
    }

    @Test
    public void corruptPdfReportsUnreadableError() throws Exception {
        File a = pdf("a.pdf", A4);
        File junk = tmp.newFile("junk.pdf");
        Files.write(junk.toPath(), "not a pdf".getBytes());
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, junk, new Config());
        assertEquals(PdfComparePrechecker.Severity.ERROR, byCode(findings, "doc-unreadable").severity);
    }

    @Test
    public void encryptedPdfWithoutPasswordReportsEncryptedError() throws Exception {
        File a = pdf("a.pdf", A4);
        File enc = encryptedPdf("enc.pdf");
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, enc, new Config());
        assertEquals(PdfComparePrechecker.Severity.ERROR, byCode(findings, "encrypted").severity);
    }

    @Test
    public void encryptedPdfWithCorrectPasswordReportsNothing() throws Exception {
        File enc1 = encryptedPdf("enc1.pdf");
        File enc2 = encryptedPdf("enc2.pdf");
        Config config = new Config();
        config.pdfPass = "user-secret";
        assertEquals(List.of(), codes(PdfComparePrechecker.check(enc1, enc2, config)));
    }

    @Test
    public void encryptedMessageNamesThePasswordOption() throws Exception {
        File a = pdf("a.pdf", A4);
        File enc = encryptedPdf("enc.pdf");
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, enc, new Config());
        assertTrue(byCode(findings, "encrypted").message.contains("-pp"));
    }

    @Test
    public void zeroPagePdfReportsError() throws Exception {
        File a = pdf("a.pdf", A4);
        File empty = pdf("empty.pdf");
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(a, empty, new Config());
        assertEquals(PdfComparePrechecker.Severity.ERROR, byCode(findings, "zero-pages").severity);
    }

    @Test
    public void unreadableDocSkipsMismatchChecks() throws Exception {
        File junk = tmp.newFile("junk.pdf");
        Files.write(junk.toPath(), "not a pdf".getBytes());
        File b = pdf("b.pdf", A4, A4);
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(junk, b, new Config());
        assertEquals(List.of("doc-unreadable"), codes(findings));
    }

    @Test
    public void pageCountMismatchReportsDoc1Count() throws Exception {
        File a = pdf("a.pdf", A4, A4, A4);
        File b = pdf("b.pdf", A4);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config()), "page-count-mismatch")
                .message.contains("3 page(s)"));
    }

    @Test
    public void pageCountMismatchReportsDoc2Count() throws Exception {
        File a = pdf("a.pdf", A4, A4, A4);
        File b = pdf("b.pdf", A4);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config()), "page-count-mismatch")
                .message.contains("1 page(s)"));
    }

    @Test
    public void pageCountMismatchNudgesSelectedPages() throws Exception {
        File a = pdf("a.pdf", A4, A4);
        File b = pdf("b.pdf", A4);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config()), "page-count-mismatch")
                .message.contains("-sp"));
    }

    @Test
    public void dimensionMismatchIsWarningByDefault() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        assertEquals(PdfComparePrechecker.Severity.WARNING,
                byCode(PdfComparePrechecker.check(a, b, new Config()), "dimension-mismatch").severity);
    }

    @Test
    public void dimensionMismatchListsThePageNumbers() throws Exception {
        File a = pdf("a.pdf", A4, A4, A4);
        File b = pdf("b.pdf", A4, LETTER, LETTER);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config()), "dimension-mismatch")
                .message.contains("2, 3"));
    }

    @Test
    public void dimensionMismatchListsFirstFivePages() throws Exception {
        float[][] sevenA4 = { A4, A4, A4, A4, A4, A4, A4 };
        float[][] sevenLetter = { LETTER, LETTER, LETTER, LETTER, LETTER, LETTER, LETTER };
        File a = pdf("a.pdf", sevenA4);
        File b = pdf("b.pdf", sevenLetter);
        String msg = byCode(PdfComparePrechecker.check(a, b, new Config()), "dimension-mismatch").message;
        assertTrue(msg.contains("1, 2, 3, 4, 5, …"));
    }

    @Test
    public void dimensionMismatchReportsTotalPageCount() throws Exception {
        float[][] sevenA4 = { A4, A4, A4, A4, A4, A4, A4 };
        float[][] sevenLetter = { LETTER, LETTER, LETTER, LETTER, LETTER, LETTER, LETTER };
        File a = pdf("a.pdf", sevenA4);
        File b = pdf("b.pdf", sevenLetter);
        String msg = byCode(PdfComparePrechecker.check(a, b, new Config()), "dimension-mismatch").message;
        assertTrue(msg.contains("7 page(s)"));
    }

    @Test
    public void rotatedPageGetsOrientationHint() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", A4_ROTATED);
        assertTrue(byCode(PdfComparePrechecker.check(a, b, new Config()), "dimension-mismatch")
                .message.contains("rotated"));
    }

    @Test
    public void withinToleranceDimensionsDoNotWarn() throws Exception {
        File a = pdf("a.pdf", new float[] { 595f, 842f });
        File b = pdf("b.pdf", new float[] { 595.9f, 842.9f });
        assertTrue(!codes(PdfComparePrechecker.check(a, b, new Config())).contains("dimension-mismatch"));
    }

    @Test
    public void beyondToleranceDimensionsWarn() throws Exception {
        File a = pdf("a.pdf", new float[] { 595f, 842f });
        File b = pdf("b.pdf", new float[] { 596.1f, 842f });
        assertTrue(codes(PdfComparePrechecker.check(a, b, new Config())).contains("dimension-mismatch"));
    }

    @Test
    public void selectedPagesRestrictsDimensionCheck() throws Exception {
        File a = pdf("a.pdf", A4, LETTER, A4);
        File b = pdf("b.pdf", A4, A4, A4);
        Config config = new Config();
        config.pages = "1,3";
        assertTrue(!codes(PdfComparePrechecker.check(a, b, config)).contains("dimension-mismatch"));
    }

    @Test
    public void viewportOverrideDowngradesDimensionMismatchToInfo() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        Config config = new Config();
        config.setViewport("1000x600");
        assertEquals(PdfComparePrechecker.Severity.INFO,
                byCode(PdfComparePrechecker.check(a, b, config), "dimension-mismatch").severity);
    }

    @Test
    public void matchSizeOverrideDowngradesDimensionMismatchToInfo() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        Config config = new Config();
        config.setMatchSize("1000x600");
        assertEquals(PdfComparePrechecker.Severity.INFO,
                byCode(PdfComparePrechecker.check(a, b, config), "dimension-mismatch").severity);
    }

    @Test
    public void pdfTrimAddsPreTrimNote() throws Exception {
        File a = pdf("a.pdf", A4);
        File b = pdf("b.pdf", LETTER);
        Config config = new Config();
        config.setPdfTrim("auto");
        assertTrue(byCode(PdfComparePrechecker.check(a, b, config), "dimension-mismatch")
                .message.contains("before trimming"));
    }

    @Test
    public void nonPdfInputsOnlyGetFileLevelChecks() throws Exception {
        File img = tmp.newFile("a.png");
        Files.write(img.toPath(), new byte[] { 1, 2, 3 });
        List<PdfComparePrechecker.Finding> findings = PdfComparePrechecker.check(img, img, new Config());
        assertEquals(List.of("same-file"), codes(findings));
    }
}
