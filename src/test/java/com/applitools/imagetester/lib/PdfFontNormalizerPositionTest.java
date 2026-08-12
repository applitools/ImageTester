package com.applitools.imagetester.lib;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.applitools.imagetester.lib.testdata.NfTestPdfBuilder;

/**
 * Position-fidelity contract: normalization swaps glyphs, never moves them.
 * Every glyph's pen position must survive normalization even when a run's
 * start depends on the previous run's advances (cursor flow) or on the
 * text leading (TL + ').
 */
public class PdfFontNormalizerPositionTest {

    private static final float POSITION_TOLERANCE_PT = 0.5f;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void nf_preserves_pen_position_of_cursor_flow_run() throws IOException {
        File pdf = NfTestPdfBuilder.createCursorFlowLatin(tempFolder.getRoot(), "cursor-latin.pdf");

        Glyph original = originalMarkerGlyph(pdf, "S");
        Glyph normalized = normalizedMarkerGlyph(pdf, "S", true, false);

        assertEquals(original.x, normalized.x, POSITION_TOLERANCE_PT);
    }

    @Test
    public void nfj_preserves_pen_position_of_cursor_flow_run() throws IOException {
        File pdf = NfTestPdfBuilder.createCursorFlowJapaneseNarrowWidths(
                tempFolder.getRoot(), "cursor-jp.pdf");

        // the run-START glyph is the contract; 日 extracts as Kangxi radical
        // U+2F47 (PDFBox reverse-cmap ToUnicode), so match that codepoint
        Glyph original = originalMarkerGlyph(pdf, "⽇");
        Glyph normalized = normalizedMarkerGlyph(pdf, "⽇", false, true);

        assertEquals(original.x, normalized.x, POSITION_TOLERANCE_PT);
    }

    @Test
    public void nf_preserves_line_position_flowed_by_leading() throws IOException {
        File pdf = NfTestPdfBuilder.createLeadingFlow(tempFolder.getRoot(), "leading.pdf");

        Glyph original = originalMarkerGlyph(pdf, "B");
        Glyph normalized = normalizedMarkerGlyph(pdf, "B", true, false);

        assertEquals(original.y, normalized.y, POSITION_TOLERANCE_PT);
    }

    // --- helpers ---

    private static final class Glyph {
        final float x;
        final float y;

        Glyph(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private Glyph originalMarkerGlyph(File pdf, String marker) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return firstMarkerGlyph(doc, marker);
        }
    }

    private Glyph normalizedMarkerGlyph(File pdf, String marker,
                                        boolean latin, boolean japanese) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf); PDDocument tmp = new PDDocument()) {
            PDPage normalized = PdfFontNormalizer.normalize(doc.getPage(0), tmp, latin, japanese);
            tmp.addPage(normalized);
            return firstMarkerGlyph(tmp, marker);
        }
    }

    private Glyph firstMarkerGlyph(PDDocument doc, String marker) throws IOException {
        List<TextPosition> collected = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                collected.add(text);
            }
        };
        stripper.setStartPage(1);
        stripper.setEndPage(1);
        stripper.getText(doc);
        for (TextPosition position : collected) {
            if (marker.equals(position.getUnicode())) {
                return new Glyph(position.getX(), position.getY());
            }
        }
        throw new AssertionError("marker '" + marker + "' not found in extracted text");
    }
}
