package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PathFingerprinterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void intersection_keeps_paths_drawn_in_every_input() throws IOException {
        File a = tempFolder.newFile("a.pdf");
        writePdfWithPolylineAndStamp(a, 100f, 100f, 300f, 150f, 50f, 700f);

        File b = tempFolder.newFile("b.pdf");
        writePdfWithPolylineAndStamp(b, 200f, 200f, 350f, 280f, 250f, 600f);

        Set<String> fingerprint = PathFingerprinter.intersection(Arrays.asList(a, b));

        assertEquals("Intersection should hold exactly one shape (the shared stamp)",
                1, fingerprint.size());
    }

    @Test
    public void intersection_of_one_pdf_returns_all_its_paths() throws IOException {
        File a = tempFolder.newFile("a.pdf");
        writePdfWithPolylineAndStamp(a, 100f, 100f, 300f, 100f, 50f, 700f);

        Set<String> fingerprint = PathFingerprinter.intersection(Arrays.asList(a));

        // 2 paths: body polyline + stamp
        assertEquals(2, fingerprint.size());
    }

    /**
     * Writes a 1-page PDF with two paths, each with > MIN_OPS_FOR_WATERMARK_CANDIDATE
     * operators so both qualify as fingerprint candidates:
     *
     *  - A unique 101-op body polyline whose normalized shape differs between calls
     *    (different dx/dy → different coord-hash → excluded from intersection).
     *  - A fixed 101-op 100-gon stamp whose relative shape is identical across calls
     *    (same normalized coords regardless of stampOrigin → included in intersection).
     */
    private void writePdfWithPolylineAndStamp(File file,
                                              float lineX1, float lineY1,
                                              float lineX2, float lineY2,
                                              float stampOriginX, float stampOriginY) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawUniquePolyline(cs, lineX1, lineY1, lineX2, lineY2);
                drawPolygonStamp(cs, stampOriginX + 30f, stampOriginY + 30f);
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
