package com.applitools.imagetester.lib;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.applitools.imagetester.lib.testdata.NfTestPdfBuilder;

/**
 * Realistic document pairs: identical text, layout and metrics, produced by
 * two font-embedding pipelines (simple WinAnsi TrueType vs Type0/Identity-H
 * subset — the Word-vs-Aspose shape). After -nf both sides must render the
 * same; the structural tests prove the pairs genuinely embed fonts
 * differently, keeping the image assertions honest.
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
    public void invoice_pair_embeds_fonts_differently() throws IOException {
        File a = NfTestPdfBuilder.createInvoice(tempFolder.getRoot(), "invoice-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createInvoice(tempFolder.getRoot(), "invoice-b.pdf", NfTestPdfBuilder.THEME_B);

        assertFontImplementationsDiffer(a, b);
    }

    @Test
    public void report_pair_renders_identically_after_normalization_on_both_pages() throws IOException {
        File a = NfTestPdfBuilder.createReport(tempFolder.getRoot(), "report-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createReport(tempFolder.getRoot(), "report-b.pdf", NfTestPdfBuilder.THEME_B);

        PdfImageAssertions.assertImagesMatch(renderNormalizedPage(a, 0), renderNormalizedPage(b, 0));
        PdfImageAssertions.assertImagesMatch(renderNormalizedPage(a, 1), renderNormalizedPage(b, 1));
    }

    @Test
    public void report_pair_embeds_fonts_differently() throws IOException {
        File a = NfTestPdfBuilder.createReport(tempFolder.getRoot(), "report-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createReport(tempFolder.getRoot(), "report-b.pdf", NfTestPdfBuilder.THEME_B);

        assertFontImplementationsDiffer(a, b);
    }

    @Test
    public void letter_pair_renders_identically_after_normalization() throws IOException {
        File a = NfTestPdfBuilder.createLetter(tempFolder.getRoot(), "letter-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createLetter(tempFolder.getRoot(), "letter-b.pdf", NfTestPdfBuilder.THEME_B);

        PdfImageAssertions.assertImagesMatch(renderNormalizedPage(a, 0), renderNormalizedPage(b, 0));
    }

    @Test
    public void letter_pair_embeds_fonts_differently() throws IOException {
        File a = NfTestPdfBuilder.createLetter(tempFolder.getRoot(), "letter-a.pdf", NfTestPdfBuilder.THEME_A);
        File b = NfTestPdfBuilder.createLetter(tempFolder.getRoot(), "letter-b.pdf", NfTestPdfBuilder.THEME_B);

        assertFontImplementationsDiffer(a, b);
    }

    @Test
    public void encoding_mismatch_pair_embeds_fonts_differently() throws IOException {
        File a = NfTestPdfBuilder.createEncodingMismatchWinAnsi(tempFolder.getRoot(), "mismatch-a.pdf");
        File b = NfTestPdfBuilder.createEncodingMismatchIdentityH(tempFolder.getRoot(), "mismatch-b.pdf");

        assertFontImplementationsDiffer(a, b);
    }

    /**
     * The README's headline use case for -nf: two pipelines embedding the
     * same font differently.
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

    /** The pair's two sides must implement their fonts with different PDF font types. */
    private static void assertFontImplementationsDiffer(File a, File b) throws IOException {
        org.junit.Assert.assertNotEquals(fontImplementations(a), fontImplementations(b));
    }

    private static java.util.Set<String> fontImplementations(File pdf) throws IOException {
        java.util.Set<String> implementations = new java.util.TreeSet<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            org.apache.pdfbox.pdmodel.PDResources resources = doc.getPage(0).getResources();
            for (org.apache.pdfbox.cos.COSName name : resources.getFontNames()) {
                implementations.add(resources.getFont(name).getClass().getSimpleName());
            }
        }
        return implementations;
    }

    private BufferedImage renderNormalizedPage(File pdf, int pageIndex) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return PdfFontNormalizer.renderNormalized(doc.getPage(pageIndex), TEST_DPI, true, false);
        }
    }
}
