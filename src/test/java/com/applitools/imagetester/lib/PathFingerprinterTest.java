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
        // Both PDFs share a "stamp" triangle (same shape, different positions).
        // Each PDF also has its own unique body line.
        File a = tempFolder.newFile("a.pdf");
        writePdfWithLineAndStamp(a, 100f, 100f, 300f, 150f, /* stampOriginX */ 50f, /* stampOriginY */ 700f);

        File b = tempFolder.newFile("b.pdf");
        writePdfWithLineAndStamp(b, 200f, 200f, 350f, 280f, /* stampOriginX */ 250f, /* stampOriginY */ 600f);

        Set<String> fingerprint = PathFingerprinter.intersection(Arrays.asList(a, b));

        assertEquals("Intersection should hold exactly one shape (the shared stamp)",
                1, fingerprint.size());
    }

    @Test
    public void intersection_of_one_pdf_returns_all_its_paths() throws IOException {
        File a = tempFolder.newFile("a.pdf");
        writePdfWithLineAndStamp(a, 100f, 100f, 300f, 100f, 50f, 700f);

        Set<String> fingerprint = PathFingerprinter.intersection(Arrays.asList(a));

        // 2 paths in a single PDF: the body line + the stamp
        assertEquals(2, fingerprint.size());
    }

    /**
     * Writes a 1-page PDF containing a body line plus a triangle "stamp" path.
     * The triangle's shape is fixed; only its origin varies between calls.
     */
    private void writePdfWithLineAndStamp(File file,
                                          float lineX1, float lineY1, float lineX2, float lineY2,
                                          float stampOriginX, float stampOriginY) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Body line (different per file)
                cs.moveTo(lineX1, lineY1);
                cs.lineTo(lineX2, lineY2);
                cs.stroke();

                // Triangle stamp (same shape, different origin)
                cs.moveTo(stampOriginX, stampOriginY);
                cs.lineTo(stampOriginX + 50f, stampOriginY + 80f);
                cs.lineTo(stampOriginX + 100f, stampOriginY);
                cs.closeAndStroke();
            }
            doc.save(file);
        }
    }
}
