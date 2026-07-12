package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Test;

public class CropMarkDetectorTest {

    // Geometry mirrors the black_card print files: 673.2x846 page, 603x774 trim, centered.
    private static final float PAGE_WIDTH = 673.2f;
    private static final float PAGE_HEIGHT = 846f;
    private static final float TRIM_LEFT = 35.1f;
    private static final float TRIM_BOTTOM = 36f;
    private static final float TRIM_RIGHT = TRIM_LEFT + 603f;
    private static final float TRIM_TOP = TRIM_BOTTOM + 774f;
    private static final float MARK_LENGTH = 18f;
    private static final float MARK_GAP = 4f;
    private static final float TOLERANCE = 0.5f;

    @Test
    public void detect_findsTrimBoxFromCropMarks() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = newPage(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawCropMarks(cs);
            }

            PDRectangle box = CropMarkDetector.detect(page);

            assertTrimBox(box);
        }
    }

    @Test
    public void detect_returnsNullWhenPageHasNoMarks() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = newPage(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12f);
                cs.newLineAtOffset(100, 400);
                cs.showText("No printer marks here");
                cs.endText();
            }

            assertNull(CropMarkDetector.detect(page));
        }
    }

    @Test
    public void detect_ignoresLongRulesAndContentStrokes() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = newPage(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawCropMarks(cs);
                // long separator rule inside the artwork
                drawLine(cs, 100, 400, 500, 400);
                // short underline inside the artwork (mark-like length, inside trim)
                drawLine(cs, 200, 300, 220, 300);
            }

            PDRectangle box = CropMarkDetector.detect(page);

            assertTrimBox(box);
        }
    }

    @Test
    public void detect_prefersTrimMarksOverOuterBleedMarks() throws IOException {
        // Real print files draw crop marks on the trim lines AND bleed marks further out,
        // and the trim box is not necessarily centered (duplex front/back offset).
        float left = 36f, right = 639f, bottom = 36f, top = 810f, bleed = 9f;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = newPage(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawMarkSet(cs, left, right, bottom, top, bleed);
                drawMarkSet(cs, left - bleed, right + bleed, bottom - bleed, top + bleed, 3f);
            }

            PDRectangle box = CropMarkDetector.detect(page);

            assertNotNull("Expected a detected trim box", box);
            assertEquals("Left edge", left, box.getLowerLeftX(), TOLERANCE);
            assertEquals("Bottom edge", bottom, box.getLowerLeftY(), TOLERANCE);
            assertEquals("Width", right - left, box.getWidth(), TOLERANCE);
            assertEquals("Height", top - bottom, box.getHeight(), TOLERANCE);
        }
    }

    @Test
    public void detect_returnsNullWhenOneEdgeHasNoMarks() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = newPage(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawCropMarksMissingRightEdge(cs);
            }

            assertNull(CropMarkDetector.detect(page));
        }
    }

    private PDPage newPage(PDDocument doc) {
        PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
        doc.addPage(page);
        return page;
    }

    /** Standard 8-mark layout: one vertical + one horizontal hairline per corner, on the trim lines. */
    private void drawCropMarks(PDPageContentStream cs) throws IOException {
        drawCropMarksMissingRightEdge(cs);
        // right-edge vertical marks
        drawLine(cs, TRIM_RIGHT, TRIM_TOP + MARK_GAP, TRIM_RIGHT, TRIM_TOP + MARK_GAP + MARK_LENGTH);
        drawLine(cs, TRIM_RIGHT, TRIM_BOTTOM - MARK_GAP, TRIM_RIGHT, TRIM_BOTTOM - MARK_GAP - MARK_LENGTH);
    }

    private void drawCropMarksMissingRightEdge(PDPageContentStream cs) throws IOException {
        // left-edge vertical marks
        drawLine(cs, TRIM_LEFT, TRIM_TOP + MARK_GAP, TRIM_LEFT, TRIM_TOP + MARK_GAP + MARK_LENGTH);
        drawLine(cs, TRIM_LEFT, TRIM_BOTTOM - MARK_GAP, TRIM_LEFT, TRIM_BOTTOM - MARK_GAP - MARK_LENGTH);
        // top-edge horizontal marks
        drawLine(cs, TRIM_LEFT - MARK_GAP, TRIM_TOP, TRIM_LEFT - MARK_GAP - MARK_LENGTH, TRIM_TOP);
        drawLine(cs, TRIM_RIGHT + MARK_GAP, TRIM_TOP, TRIM_RIGHT + MARK_GAP + MARK_LENGTH, TRIM_TOP);
        // bottom-edge horizontal marks
        drawLine(cs, TRIM_LEFT - MARK_GAP, TRIM_BOTTOM, TRIM_LEFT - MARK_GAP - MARK_LENGTH, TRIM_BOTTOM);
        drawLine(cs, TRIM_RIGHT + MARK_GAP, TRIM_BOTTOM, TRIM_RIGHT + MARK_GAP + MARK_LENGTH, TRIM_BOTTOM);
    }

    /** One full set of 8 marks whose lines sit on the given edges, offset outward by the gap. */
    private void drawMarkSet(PDPageContentStream cs, float left, float right, float bottom, float top,
                             float gap) throws IOException {
        for (float x : new float[] { left, right }) {
            drawLine(cs, x, top + gap, x, top + gap + MARK_LENGTH);
            drawLine(cs, x, bottom - gap, x, bottom - gap - MARK_LENGTH);
        }
        for (float y : new float[] { bottom, top }) {
            drawLine(cs, left - gap, y, left - gap - MARK_LENGTH, y);
            drawLine(cs, right + gap, y, right + gap + MARK_LENGTH, y);
        }
    }

    private void drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.setLineWidth(0.3f);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void assertTrimBox(PDRectangle box) {
        assertNotNull("Expected a detected trim box", box);
        assertEquals("Left edge", TRIM_LEFT, box.getLowerLeftX(), TOLERANCE);
        assertEquals("Bottom edge", TRIM_BOTTOM, box.getLowerLeftY(), TOLERANCE);
        assertEquals("Width", 603f, box.getWidth(), TOLERANCE);
        assertEquals("Height", 774f, box.getHeight(), TOLERANCE);
    }
}
