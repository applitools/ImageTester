package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import com.applitools.imagetester.ImageTester;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PdfVectorAutoModeTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void rwauto_strips_shared_stamp_keeps_unique_body_paths() throws IOException {
        File inputDir = tempFolder.newFolder("input");
        File outputDir = tempFolder.newFolder("output");

        // Doc A and Doc B have bodies with DIFFERENT op-counts (100 vs 107 line segments)
        // so their op-sequences differ and OpSequenceVarianceFinder doesn't conflate them.
        // The shared stamp uses a fixed op pattern present in both docs, identical
        // normalized shape, so PathFingerprinter catches it as the watermark.
        File a = new File(inputDir, "a.pdf");
        writeBodyAndStamp(a, /*bx*/ 100f, /*by*/ 700f, /*bx2*/ 250f, /*by2*/ 705f,
                /*sx*/ 80f, /*sy*/ 400f, /*bodySteps*/ 100);

        File b = new File(inputDir, "b.pdf");
        writeBodyAndStamp(b, /*bx*/ 60f, /*by*/ 600f, /*bx2*/ 300f, /*by2*/ 660f,
                /*sx*/ 200f, /*sy*/ 350f, /*bodySteps*/ 107);

        int exit = ImageTester.run(new String[] {
                "-rwauto",
                "-rwo", outputDir.getAbsolutePath(),
                "-f", inputDir.getAbsolutePath()
        });
        assertEquals(0, exit);

        File outA = new File(outputDir, "a.pdf");
        File outB = new File(outputDir, "b.pdf");
        assertTrue(outA.exists());
        assertTrue(outB.exists());

        // After cleanup, each output PDF should retain its unique body line
        // and lose the shared stamp.
        assertEquals("Output A should contain exactly 1 path (body line)", 1, pathHashCount(outA));
        assertEquals("Output B should contain exactly 1 path (body line)", 1, pathHashCount(outB));
    }

    @Test
    public void rwauto_without_rwo_returns_nonzero_exit() throws IOException {
        File inputDir = tempFolder.newFolder("input");
        int exit = ImageTester.run(new String[] {
                "-rwauto",
                "-f", inputDir.getAbsolutePath()
        });
        assertEquals(1, exit);
    }

    @Test
    public void rwauto_with_single_pdf_returns_nonzero_exit() throws IOException {
        File inputDir = tempFolder.newFolder("input");
        File outputDir = tempFolder.newFolder("output");
        File only = new File(inputDir, "only.pdf");
        writeBodyAndStamp(only, 100f, 700f, 200f, 700f, 80f, 400f, 100);

        int exit = ImageTester.run(new String[] {
                "-rwauto",
                "-rwo", outputDir.getAbsolutePath(),
                "-f", inputDir.getAbsolutePath()
        });
        assertEquals(1, exit);
    }

    private void writeBodyAndStamp(File file, float bx, float by, float bx2, float by2,
                                    float sx, float sy, int bodySteps) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawUniqueBody(cs, bx, by, bx2, by2, bodySteps);
                drawSharedStamp(cs, sx, sy);
            }
            doc.save(file);
        }
    }

    /** Zigzag polyline with {@code steps} segments. Different {@code steps} per doc
     *  yields a different operator sequence (and thus different op-seq hash), keeping
     *  bodies out of both the PathFingerprinter intersection and the
     *  OpSequenceVarianceFinder varying set. */
    private void drawUniqueBody(PDPageContentStream cs, float x1, float y1, float x2, float y2,
                                 int steps) throws IOException {
        cs.moveTo(x1, y1);
        for (int i = 1; i < steps; i++) {
            float t = (float) i / steps;
            float x = x1 + (x2 - x1) * t + (i % 2 == 0 ? 5f : -5f);
            float y = y1 + (y2 - y1) * t + (i % 3 == 0 ? 3f : -3f);
            cs.lineTo(x, y);
        }
        cs.stroke();
    }

    /** 101-op 100-gon stamp — position-invariant normalized shape, so it appears
     *  identically in every input PDF's fingerprint regardless of (sx, sy). */
    private void drawSharedStamp(PDPageContentStream cs, float cx, float cy) throws IOException {
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

    private int pathHashCount(File pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            List<String> hashes = PathFingerprint.hashesFor(parser.getTokens());
            return hashes.size();
        }
    }
}
