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

public class OpSequenceVarianceFinderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void flags_paths_with_matching_op_seq_but_differing_coords() throws IOException {
        // Both docs have an identical body polygon (same op-seq AND same coords -> excluded
        // from "varying"), plus a high-op zigzag with the same op-seq but perturbed coords.
        File a = tempFolder.newFile("a.pdf");
        writeBodyAndZigzag(a, 100f, 100f, 200f, 100f, /* zigzag perturb */ 0f);

        File b = tempFolder.newFile("b.pdf");
        writeBodyAndZigzag(b, 100f, 100f, 200f, 100f, /* zigzag perturb */ 3.7f);

        Set<String> varying = OpSequenceVarianceFinder.findVarying(Arrays.asList(a, b));

        assertEquals("Exactly one varying op-seq (the zigzag)", 1, varying.size());
    }

    @Test
    public void single_pdf_returns_empty() throws IOException {
        File a = tempFolder.newFile("a.pdf");
        writeBodyAndZigzag(a, 100f, 100f, 200f, 100f, 0f);

        Set<String> varying = OpSequenceVarianceFinder.findVarying(Arrays.asList(a));

        assertTrue(varying.isEmpty());
    }

    /**
     * Writes a page with two paths, both above {@code MIN_OPS_FOR_WATERMARK_CANDIDATE}:
     *
     *  - A 101-op 100-gon "body" stamp at a fixed origin (identical coords across all
     *    calls -> same coord-hash -> excluded from the "varying" set).
     *  - A 101-op zigzag whose vertex coordinates are perturbed by {@code perturb}
     *    (same op-sequence in every call but different coord-hashes between calls
     *    -> the only varying-op-seq result).
     */
    private void writeBodyAndZigzag(File file, float lineX1, float lineY1, float lineX2, float lineY2,
                                    float perturb) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawFixedPolygon(cs, lineX1, lineY1);
                drawPerturbedZigzag(cs, 300f, 500f, perturb);
            }
            doc.save(file);
        }
    }

    /** 101-op closed polygon — identical shape and absolute position on every call,
     *  giving the same coord-hash everywhere so it's excluded from the varying set. */
    private void drawFixedPolygon(PDPageContentStream cs, float cx, float cy) throws IOException {
        int sides = 100;
        float r = 30f;
        for (int i = 0; i < sides; i++) {
            double a = 2 * Math.PI * i / sides;
            float x = cx + (float) (r * Math.cos(a));
            float y = cy + (float) (r * Math.sin(a));
            if (i == 0) cs.moveTo(x, y);
            else cs.lineTo(x, y);
        }
        cs.closeAndStroke();
    }

    /** 101-op zigzag — same op-sequence on every call (1 moveTo + 99 lineTo + 1 stroke),
     *  but with vertex coordinates shifted by {@code perturb} so coord-hashes differ. */
    private void drawPerturbedZigzag(PDPageContentStream cs, float x0, float y0, float perturb) throws IOException {
        int steps = 100;
        cs.moveTo(x0, y0);
        for (int i = 1; i < steps; i++) {
            float dx = (i % 2 == 0 ? 4f : -4f) + perturb;
            float dy = (i % 3 == 0 ? 3f : -3f) + perturb;
            cs.lineTo(x0 + i * 1.2f + dx, y0 + dy);
        }
        cs.stroke();
    }
}
