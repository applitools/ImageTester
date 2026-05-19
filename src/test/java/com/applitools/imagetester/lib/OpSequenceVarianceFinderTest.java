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
        // Doc A and Doc B both have a body line (identical shape, same path),
        // and both have a watermark zigzag (same operator structure, different coordinates).
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

    /** Body line is identical across all calls; the "zigzag" path has the same operator
     *  sequence everywhere but its coordinates are perturbed by `perturb`. */
    private void writeBodyAndZigzag(File file, float lineX1, float lineY1, float lineX2, float lineY2,
                                    float perturb) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.moveTo(lineX1, lineY1);
                cs.lineTo(lineX2, lineY2);
                cs.stroke();

                // Long zigzag (>= 6 ops) — same op sequence, slightly different coords
                cs.moveTo(300f + perturb, 500f);
                cs.lineTo(320f, 520f + perturb);
                cs.lineTo(340f + perturb, 500f);
                cs.lineTo(360f, 520f);
                cs.lineTo(380f + perturb, 500f);
                cs.lineTo(400f, 520f);
                cs.lineTo(420f + perturb, 500f);
                cs.stroke();
            }
            doc.save(file);
        }
    }
}
