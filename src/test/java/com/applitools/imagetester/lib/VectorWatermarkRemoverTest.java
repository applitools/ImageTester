package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VectorWatermarkRemoverTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void strips_paths_matching_fingerprint_keeps_others() throws IOException {
        File source = tempFolder.newFile("src.pdf");
        writeBodyAndStamp(source);

        File second = tempFolder.newFile("src2.pdf");
        writeDifferentBodyAndStamp(second);
        Set<String> fingerprint = PathFingerprinter.intersection(Arrays.asList(source, second));
        assertEquals("Fingerprint should hold the stamp shape", 1, fingerprint.size());

        try (PDDocument doc = PDDocument.load(source)) {
            VectorWatermarkRemover.removeFromAllPages(doc, fingerprint);

            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            java.util.List<String> remainingHashes = PathFingerprint.hashesFor(parser.getTokens());
            for (String h : remainingHashes) {
                assertFalse("Fingerprinted path should be stripped", fingerprint.contains(h));
            }
            assertEquals(1, remainingHashes.size());
        }
    }

    private void writeBodyAndStamp(File file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawUniquePolyline(cs, 72f, 720f, 540f, 720f);
                drawPolygonStamp(cs, 100f, 400f);
            }
            doc.save(file);
        }
    }

    private void writeDifferentBodyAndStamp(File file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawUniquePolyline(cs, 72f, 600f, 380f, 605f);
                drawPolygonStamp(cs, 250f, 300f);
            }
            doc.save(file);
        }
    }

    /** 101-op zigzag polyline whose normalized shape depends on dx/dy between endpoints. */
    private void drawUniquePolyline(PDPageContentStream cs,
                                    float x1, float y1, float x2, float y2) throws IOException {
        int steps = 100;
        cs.moveTo(x1, y1);
        for (int i = 1; i < steps; i++) {
            float t = (float) i / steps;
            float x = x1 + (x2 - x1) * t + (i % 2 == 0 ? 5f : -5f);
            float y = y1 + (y2 - y1) * t + (i % 3 == 0 ? 3f : -3f);
            cs.lineTo(x, y);
        }
        cs.stroke();
    }

    /** 101-op 100-gon whose normalized shape is position-invariant. */
    private void drawPolygonStamp(PDPageContentStream cs, float cx, float cy) throws IOException {
        int sides = 100;
        float r = 30f;
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides;
            float x = cx + (float) (r * Math.cos(angle));
            float y = cy + (float) (r * Math.sin(angle));
            if (i == 0) cs.moveTo(x, y);
            else cs.lineTo(x, y);
        }
        cs.closeAndStroke();
    }
}
