package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Test;

public class PdfPageTrimmerTest {

    private static final float PAGE_WIDTH = 673.2f;
    private static final float PAGE_HEIGHT = 846f;
    private static final PDRectangle TRIM = new PDRectangle(35.1f, 36f, 603f, 774f);
    private static final float TOLERANCE = 0.5f;

    @Test
    public void resolveCropBox_returnsNullWhenTrimNotConfigured() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            assertNull(PdfPageTrimmer.resolveCropBox(newPage(doc), new Config()));
        }
    }

    @Test
    public void resolveCropBox_autoPrefersTrimBoxMetadata() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = newPage(doc);
            page.setTrimBox(TRIM);

            PDRectangle box = PdfPageTrimmer.resolveCropBox(page, autoConfig());

            assertBoxEquals(TRIM, box);
        }
    }

    @Test
    public void resolveCropBox_autoDetectsCropMarksWhenTrimBoxAbsent() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = newPage(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawCropMarks(cs);
            }

            PDRectangle box = PdfPageTrimmer.resolveCropBox(page, autoConfig());

            assertBoxEquals(TRIM, box);
        }
    }

    @Test
    public void resolveCropBox_autoReturnsNullWhenNoTrimSignal() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            assertNull(PdfPageTrimmer.resolveCropBox(newPage(doc), autoConfig()));
        }
    }

    @Test
    public void resolveCropBox_manualCentersRequestedBox() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(200f, 300f));
            doc.addPage(page);

            PDRectangle box = PdfPageTrimmer.resolveCropBox(page, trimConfig("100x150"));

            assertBoxEquals(new PDRectangle(50f, 75f, 100f, 150f), box);
        }
    }

    @Test
    public void resolveCropBox_manualOversizeClampsToMediaBox() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(200f, 300f));
            doc.addPage(page);

            PDRectangle box = PdfPageTrimmer.resolveCropBox(page, trimConfig("999x999"));

            assertBoxEquals(new PDRectangle(0f, 0f, 200f, 300f), box);
        }
    }

    private PDPage newPage(PDDocument doc) {
        PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
        doc.addPage(page);
        return page;
    }

    private Config autoConfig() {
        return trimConfig("auto");
    }

    private Config trimConfig(String value) {
        Config config = new Config();
        config.setPdfTrim(value);
        return config;
    }

    private void drawCropMarks(PDPageContentStream cs) throws IOException {
        float left = TRIM.getLowerLeftX(), right = TRIM.getUpperRightX();
        float bottom = TRIM.getLowerLeftY(), top = TRIM.getUpperRightY();
        float gap = 4f, len = 18f;
        for (float x : new float[] { left, right }) {
            drawLine(cs, x, top + gap, x, top + gap + len);
            drawLine(cs, x, bottom - gap, x, bottom - gap - len);
        }
        for (float y : new float[] { bottom, top }) {
            drawLine(cs, left - gap, y, left - gap - len, y);
            drawLine(cs, right + gap, y, right + gap + len, y);
        }
    }

    private void drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.setLineWidth(0.3f);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void assertBoxEquals(PDRectangle expected, PDRectangle actual) {
        assertNotNull("Expected a crop box", actual);
        assertEquals("Left edge", expected.getLowerLeftX(), actual.getLowerLeftX(), TOLERANCE);
        assertEquals("Bottom edge", expected.getLowerLeftY(), actual.getLowerLeftY(), TOLERANCE);
        assertEquals("Width", expected.getWidth(), actual.getWidth(), TOLERANCE);
        assertEquals("Height", expected.getHeight(), actual.getHeight(), TOLERANCE);
    }
}
