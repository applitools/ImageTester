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

        File a = new File(inputDir, "a.pdf");
        writeBodyAndStamp(a, /*bx*/ 100f, /*by*/ 700f, /*bx2*/ 250f, /*by2*/ 705f, /*sx*/ 80f, /*sy*/ 400f);

        File b = new File(inputDir, "b.pdf");
        writeBodyAndStamp(b, /*bx*/ 60f, /*by*/ 600f, /*bx2*/ 300f, /*by2*/ 660f, /*sx*/ 200f, /*sy*/ 350f);

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
        writeBodyAndStamp(only, 100f, 700f, 200f, 700f, 80f, 400f);

        int exit = ImageTester.run(new String[] {
                "-rwauto",
                "-rwo", outputDir.getAbsolutePath(),
                "-f", inputDir.getAbsolutePath()
        });
        assertEquals(1, exit);
    }

    private void writeBodyAndStamp(File file, float bx, float by, float bx2, float by2,
                                    float sx, float sy) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Unique body line
                cs.moveTo(bx, by);
                cs.lineTo(bx2, by2);
                cs.stroke();
                // Shared stamp shape
                cs.moveTo(sx, sy);
                cs.lineTo(sx + 50f, sy + 80f);
                cs.lineTo(sx + 100f, sy);
                cs.closeAndStroke();
            }
            doc.save(file);
        }
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
