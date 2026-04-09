package com.applitools.imagetester.demo;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;

/**
 * Generates sample PDFs for the BatchMapper demo.
 *
 * Run from the project root with:
 *   mvn exec:java -Dexec.mainClass="com.applitools.imagetester.demo.GenerateDemoPDFs"
 *
 * Output files:
 *   demo/batchmapper/before/contract.pdf  — original 4-page contract
 *   demo/batchmapper/after/contract.pdf   — updated 5-page contract (page inserted after page 2)
 */
public class GenerateDemoPDFs {

    public static void main(String[] args) throws IOException {
        createBeforePdf("demo/batchmapper/before/contract.pdf");
        createAfterPdf("demo/batchmapper/after/contract.pdf");
        System.out.println("Done. PDFs written to demo/batchmapper/before/ and demo/batchmapper/after/");
    }

    // -----------------------------------------------------------------------
    // Before: 4 pages
    //   Page 1 — Cover
    //   Page 2 — Introduction
    //   Page 3 — Details
    //   Page 4 — Conclusion
    // -----------------------------------------------------------------------
    private static void createBeforePdf(String outputPath) throws IOException {
        new File(outputPath).getParentFile().mkdirs();
        try (PDDocument doc = new PDDocument()) {
            addPage(doc, "CONTRACT",          "Page 1 of 4",
                "Service Agreement",
                "This document outlines the terms and conditions between",
                "Acme Corp and its client for professional services rendered.");

            addPage(doc, "INTRODUCTION",      "Page 2 of 4",
                "1. Background",
                "Acme Corp has been providing enterprise solutions since 2010.",
                "This agreement governs the scope of work described herein.");

            addPage(doc, "DETAILS",           "Page 3 of 4",
                "2. Scope of Work",
                "The vendor shall deliver software integration services",
                "as outlined in Exhibit A, within the agreed timeline.");

            addPage(doc, "CONCLUSION",        "Page 4 of 4",
                "3. Signatures",
                "By signing below, both parties agree to the terms set forth",
                "in this contract. Effective date: January 1, 2025.");

            doc.save(outputPath);
        }
        System.out.println("Created: " + outputPath);
    }

    // -----------------------------------------------------------------------
    // After: 5 pages — new page inserted between Introduction and Details
    //   Page 1 — Cover         (unchanged)
    //   Page 2 — Introduction  (unchanged)
    //   Page 3 — NEW: Compliance Notice  <-- inserted here
    //   Page 4 — Details       (was page 3)
    //   Page 5 — Conclusion    (was page 4)
    // -----------------------------------------------------------------------
    private static void createAfterPdf(String outputPath) throws IOException {
        new File(outputPath).getParentFile().mkdirs();
        try (PDDocument doc = new PDDocument()) {
            addPage(doc, "CONTRACT",          "Page 1 of 5",
                "Service Agreement",
                "This document outlines the terms and conditions between",
                "Acme Corp and its client for professional services rendered.");

            addPage(doc, "INTRODUCTION",      "Page 2 of 5",
                "1. Background",
                "Acme Corp has been providing enterprise solutions since 2010.",
                "This agreement governs the scope of work described herein.");

            addPage(doc, "COMPLIANCE NOTICE", "Page 3 of 5  *** NEW PAGE ***",
                "1a. Regulatory Compliance",
                "All services are subject to compliance with applicable data",
                "protection regulations including GDPR and CCPA.");

            addPage(doc, "DETAILS",           "Page 4 of 5",
                "2. Scope of Work",
                "The vendor shall deliver software integration services",
                "as outlined in Exhibit A, within the agreed timeline.");

            addPage(doc, "CONCLUSION",        "Page 5 of 5",
                "3. Signatures",
                "By signing below, both parties agree to the terms set forth",
                "in this contract. Effective date: January 1, 2025.");

            doc.save(outputPath);
        }
        System.out.println("Created: " + outputPath);
    }

    private static void addPage(PDDocument doc, String heading, String subheading,
                                String section, String line1, String line2) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        doc.addPage(page);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float margin = 72;
            float width  = page.getMediaBox().getWidth() - 2 * margin;
            float y      = page.getMediaBox().getHeight() - margin;

            // Heading
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 28);
            cs.newLineAtOffset(margin, y - 40);
            cs.showText(heading);
            cs.endText();

            // Subheading
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.newLineAtOffset(margin, y - 70);
            cs.showText(subheading);
            cs.endText();

            // Divider line (drawn as a thin rectangle)
            cs.setLineWidth(0.5f);
            cs.moveTo(margin, y - 85);
            cs.lineTo(margin + width, y - 85);
            cs.stroke();

            // Body text
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
            cs.newLineAtOffset(margin, y - 120);
            cs.showText(section);
            cs.endText();

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.setLeading(20f);
            cs.newLineAtOffset(margin, y - 150);
            cs.showText(line1);
            cs.newLine();
            cs.showText(line2);
            cs.endText();
        }
    }
}
