package com.applitools.imagetester.lib;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.applitools.imagetester.lib.testdata.NfTestPdfBuilder;

/**
 * Realistic document pairs: identical text at identical fixed positions,
 * different typography. After -nf both sides must render the same; before
 * -nf they must differ (the negative test keeps the positive one honest).
 */
public class PdfFontNormalizerRealismTest {

    private static final float TEST_DPI = 72f;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void invoice_pair_renders_identically_after_normalization() throws IOException {
        File a = NfTestPdfBuilder.createInvoice(tempFolder.getRoot(), "invoice-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createInvoice(tempFolder.getRoot(), "invoice-b.pdf", NfTestPdfBuilder.THEME_B);

        PdfImageAssertions.assertImagesMatch(renderNormalizedPage(a, 0), renderNormalizedPage(b, 0));
    }

    @Test
    public void invoice_pair_renders_differently_without_normalization() throws IOException {
        File a = NfTestPdfBuilder.createInvoice(tempFolder.getRoot(), "invoice-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createInvoice(tempFolder.getRoot(), "invoice-b.pdf", NfTestPdfBuilder.THEME_B);

        PdfImageAssertions.assertImagesClearlyDiffer(renderPage(a, 0), renderPage(b, 0));
    }

    @Test
    public void report_pair_renders_identically_after_normalization_on_both_pages() throws IOException {
        File a = NfTestPdfBuilder.createReport(tempFolder.getRoot(), "report-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createReport(tempFolder.getRoot(), "report-b.pdf", NfTestPdfBuilder.THEME_B);

        PdfImageAssertions.assertImagesMatch(renderNormalizedPage(a, 0), renderNormalizedPage(b, 0));
        PdfImageAssertions.assertImagesMatch(renderNormalizedPage(a, 1), renderNormalizedPage(b, 1));
    }

    @Test
    public void report_pair_renders_differently_without_normalization() throws IOException {
        File a = NfTestPdfBuilder.createReport(tempFolder.getRoot(), "report-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createReport(tempFolder.getRoot(), "report-b.pdf", NfTestPdfBuilder.THEME_B);

        PdfImageAssertions.assertImagesClearlyDiffer(renderPage(a, 0), renderPage(b, 0));
    }

    @Test
    public void letter_pair_renders_identically_after_normalization() throws IOException {
        File a = NfTestPdfBuilder.createLetter(tempFolder.getRoot(), "letter-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createLetter(tempFolder.getRoot(), "letter-b.pdf", NfTestPdfBuilder.THEME_B);

        PdfImageAssertions.assertImagesMatch(renderNormalizedPage(a, 0), renderNormalizedPage(b, 0));
    }

    @Test
    public void letter_pair_renders_differently_without_normalization() throws IOException {
        File a = NfTestPdfBuilder.createLetter(tempFolder.getRoot(), "letter-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createLetter(tempFolder.getRoot(), "letter-b.pdf", NfTestPdfBuilder.THEME_B);

        PdfImageAssertions.assertImagesClearlyDiffer(renderPage(a, 0), renderPage(b, 0));
    }

    @Test
    public void encoding_mismatch_pair_renders_differently_without_normalization() throws IOException {
        File a = NfTestPdfBuilder.createEncodingMismatchWinAnsi(tempFolder.getRoot(), "mismatch-a.pdf");
        File b = NfTestPdfBuilder.createEncodingMismatchIdentityH(tempFolder.getRoot(), "mismatch-b.pdf");

        PdfImageAssertions.assertImagesClearlyDiffer(renderPage(a, 0), renderPage(b, 0));
    }

    /**
     * The README's headline use case for -nf: two pipelines embedding fonts
     * differently. Fails today because the Identity-H side's bytes are
     * reinterpreted as WinAnsi.
     */
    @Test
    public void encoding_mismatch_pair_renders_identically_after_normalization() throws IOException {
        File a = NfTestPdfBuilder.createEncodingMismatchWinAnsi(tempFolder.getRoot(), "mismatch-a.pdf");
        File b = NfTestPdfBuilder.createEncodingMismatchIdentityH(tempFolder.getRoot(), "mismatch-b.pdf");

        PdfImageAssertions.assertImagesMatch(renderNormalizedPage(a, 0), renderNormalizedPage(b, 0));
    }

    @Test
    public void corpus_writer_emits_every_shape() throws IOException {
        File dir = tempFolder.newFolder("corpus");

        java.util.List<File> written = com.applitools.imagetester.lib.testdata.NfCorpusWriter.writeAll(dir);

        org.junit.Assert.assertEquals(15, written.size());
    }

    // --- helpers ---

    private BufferedImage renderPage(File pdf, int pageIndex) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFRenderer(doc).renderImageWithDPI(pageIndex, TEST_DPI);
        }
    }

    private BufferedImage renderNormalizedPage(File pdf, int pageIndex) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return PdfFontNormalizer.renderNormalized(doc.getPage(pageIndex), TEST_DPI);
        }
    }
}
