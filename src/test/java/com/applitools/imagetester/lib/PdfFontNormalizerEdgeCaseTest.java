package com.applitools.imagetester.lib;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.applitools.imagetester.lib.testdata.NfTestPdfBuilder;

public class PdfFontNormalizerEdgeCaseTest {

    private static final float TEST_DPI = 72f;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // --- Fixture controls: prove each tricky fixture behaves on its own
    // --- (no normalizer involved) before any test points it at -nf.

    @Test
    public void differences_fixture_extracts_remapped_text_without_normalization() throws IOException {
        File pdf = NfTestPdfBuilder.createDifferencesEncoded(tempFolder.getRoot(), "differences.pdf");

        assertEquals(NfTestPdfBuilder.DIFFERENCES_TEXT, extractFirstPage(pdf));
    }

    @Test
    public void differences_fixture_renders_like_plain_winansi_twin_without_normalization() throws IOException {
        File differences = NfTestPdfBuilder.createDifferencesEncoded(tempFolder.getRoot(), "differences.pdf");
        File plain = NfTestPdfBuilder.createHelloWinAnsi(tempFolder.getRoot(), "plain.pdf");

        PdfImageAssertions.assertImagesMatch(renderFirstPage(differences), renderFirstPage(plain));
    }

    @Test
    public void subset_identity_h_fixture_extracts_source_text_without_normalization() throws IOException {
        File pdf = NfTestPdfBuilder.createSubsetIdentityH(tempFolder.getRoot(), "subset.pdf");

        assertEquals(NfTestPdfBuilder.SUBSET_TEXT, extractFirstPage(pdf));
    }

    // --- Defect pins: each @Ignore'd test states the CORRECT behavior and
    // --- fails against today's normalizer. Remove @Ignore when fixing.

    @Ignore("FIXME(chris): -nf drops /Rotate, normalized page renders sideways (#44)")
    @Test
    public void normalized_page_preserves_rotation() throws IOException {
        File pdf = NfTestPdfBuilder.createRotated(tempFolder.getRoot(), "rotated.pdf");

        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage normalized = PdfFontNormalizer.normalize(doc.getPage(0));
            assertEquals(90, normalized.getRotation());
        }
    }

    @Ignore("FIXME(chris): -nf drops /Rotate, normalized page renders sideways (#44)")
    @Test
    public void normalized_render_of_rotated_page_keeps_original_dimensions() throws IOException {
        File pdf = NfTestPdfBuilder.createRotated(tempFolder.getRoot(), "rotated.pdf");

        BufferedImage plain = renderFirstPage(pdf);
        BufferedImage normalized = renderNormalizedFirstPage(pdf);
        assertEquals(plain.getWidth(), normalized.getWidth());
        assertEquals(plain.getHeight(), normalized.getHeight());
    }

    @Ignore("FIXME(chris): -nf drops CropBox, render falls back to MediaBox size (#45)")
    @Test
    public void normalized_page_preserves_cropbox_dimensions() throws IOException {
        File pdf = NfTestPdfBuilder.createCropBoxed(tempFolder.getRoot(), "cropbox.pdf");

        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage normalized = PdfFontNormalizer.normalize(doc.getPage(0));
            assertEquals(306f, normalized.getCropBox().getWidth(), 0.01f);
            assertEquals(396f, normalized.getCropBox().getHeight(), 0.01f);
        }
    }

    @Ignore("FIXME(chris): -nf drops CropBox, render falls back to MediaBox size (#45)")
    @Test
    public void normalized_render_matches_cropped_dimensions() throws IOException {
        File pdf = NfTestPdfBuilder.createCropBoxed(tempFolder.getRoot(), "cropbox.pdf");

        BufferedImage plain = renderFirstPage(pdf);
        BufferedImage normalized = renderNormalizedFirstPage(pdf);
        assertEquals(plain.getWidth(), normalized.getWidth());
        assertEquals(plain.getHeight(), normalized.getHeight());
    }

    @Test
    public void normalized_differences_encoded_page_preserves_text() throws IOException {
        File pdf = NfTestPdfBuilder.createDifferencesEncoded(tempFolder.getRoot(), "differences.pdf");

        assertEquals(NfTestPdfBuilder.DIFFERENCES_TEXT, extractNormalizedFirstPage(pdf));
    }

    @Test
    public void normalized_subset_identity_h_page_preserves_text() throws IOException {
        File pdf = NfTestPdfBuilder.createSubsetIdentityH(tempFolder.getRoot(), "subset.pdf");

        assertEquals(NfTestPdfBuilder.SUBSET_TEXT, extractNormalizedFirstPage(pdf));
    }

    /**
     * Characterization, not aspiration: -nf rewrites Tf/TL/TD only, so
     * Tc (char spacing), Tz (horizontal scale) and Ts (rise) still cause
     * diffs between otherwise-identical documents.
     */
    @Test
    public void spacing_operators_still_differ_after_normalization() throws IOException {
        File plain = NfTestPdfBuilder.createSpacingDoc(tempFolder.getRoot(), "spacing-plain.pdf", false);
        File spaced = NfTestPdfBuilder.createSpacingDoc(tempFolder.getRoot(), "spacing-ops.pdf", true);

        PdfImageAssertions.assertImagesDiffer(
                renderNormalizedFirstPage(plain), renderNormalizedFirstPage(spaced));
    }

    // --- helpers ---

    private String extractFirstPage(File pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            return stripper.getText(doc).trim();
        }
    }

    private BufferedImage renderFirstPage(File pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFRenderer(doc).renderImageWithDPI(0, TEST_DPI);
        }
    }

    private String extractNormalizedFirstPage(File pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDPage normalized = PdfFontNormalizer.normalize(doc.getPage(0));
            try (PDDocument tmp = new PDDocument()) {
                tmp.addPage(normalized);
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(1);
                return stripper.getText(tmp).trim();
            }
        }
    }

    private BufferedImage renderNormalizedFirstPage(File pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return PdfFontNormalizer.renderNormalized(doc.getPage(0), TEST_DPI);
        }
    }
}
