package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import com.applitools.imagetester.ImageTester;

import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Demonstrates -rwo end-to-end on a synthetic PDF with a text watermark.
 * Prints the text content of the source PDF and the cleaned output for
 * visual comparison.
 */
public class WatermarkDemoTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void demo_remove_text_watermark() throws IOException {
        File inputDir = tempFolder.newFolder("input");
        File outputDir = tempFolder.newFolder("output");
        File source = new File(inputDir, "report.pdf");
        buildPdfWithWatermark(source);

        System.out.println("============= BEFORE =============");
        System.out.println(extractText(source));

        int exit = ImageTester.run(new String[] {
                "-rw", "DRAFT - DO NOT DISTRIBUTE",
                "-rwo", outputDir.getAbsolutePath(),
                "-f", inputDir.getAbsolutePath()
        });
        assertEquals(0, exit);

        File cleaned = new File(outputDir, "report.pdf");
        System.out.println("============= AFTER  =============");
        System.out.println(extractText(cleaned));
        System.out.println("============= STATS  =============");
        System.out.println("Source:  " + source.length() + " bytes");
        System.out.println("Cleaned: " + cleaned.length() + " bytes");
    }

    private void buildPdfWithWatermark(File file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Body text
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 18);
                cs.newLineAtOffset(72, 720);
                cs.showText("Quarterly Report Q4 2025");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 690);
                cs.showText("Revenue grew 14% year over year, driven by enterprise sales.");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 670);
                cs.showText("Operating margin improved 220bps to 28.7%.");
                cs.endText();

                // Diagonal watermark across the page
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 36);
                cs.newLineAtOffset(140, 400);
                cs.showText("DRAFT - DO NOT DISTRIBUTE");
                cs.endText();
            }
            doc.save(file);
        }
    }

    private String extractText(File pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).trim();
        }
    }
}
