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

        // First learn the stamp's fingerprint by intersecting two PDFs that share it
        File second = tempFolder.newFile("src2.pdf");
        writeDifferentBodyAndStamp(second);
        Set<String> fingerprint = PathFingerprinter.intersection(Arrays.asList(source, second));
        assertEquals("Fingerprint should hold the stamp shape", 1, fingerprint.size());

        // Apply removal to a fresh PDDocument loaded from source
        try (PDDocument doc = PDDocument.load(source)) {
            VectorWatermarkRemover.removeFromAllPages(doc, fingerprint);

            // After removal, the page's path-hash list should not include the fingerprint
            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            java.util.List<String> remainingHashes = PathFingerprint.hashesFor(parser.getTokens());
            for (String h : remainingHashes) {
                assertFalse("Fingerprinted path should be stripped", fingerprint.contains(h));
            }
            // The body line should still be there (1 path remains)
            assertEquals(1, remainingHashes.size());
        }
    }

    private void writeBodyAndStamp(File file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.moveTo(72f, 720f);
                cs.lineTo(540f, 720f);
                cs.stroke();
                drawStamp(cs, 100f, 400f);
            }
            doc.save(file);
        }
    }

    private void writeDifferentBodyAndStamp(File file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.moveTo(72f, 600f);
                cs.lineTo(380f, 605f);
                cs.stroke();
                drawStamp(cs, 250f, 300f);
            }
            doc.save(file);
        }
    }

    private void drawStamp(PDPageContentStream cs, float originX, float originY) throws IOException {
        cs.moveTo(originX, originY);
        cs.lineTo(originX + 50f, originY + 80f);
        cs.lineTo(originX + 100f, originY);
        cs.closeAndStroke();
    }
}
