package com.applitools.imagetester.lib.converters;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class RtfToPdfConverterTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void convertsMinimalRtfIntoPdfContainingText() throws Exception {
        File input = tempFolder.newFile("note.rtf");
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\deff0 Hello RTF}";
        Files.write(input.toPath(), rtf.getBytes(StandardCharsets.US_ASCII));

        File pdf = new RtfToPdfConverter().convertToPdf(input, tempFolder.getRoot().toPath());

        assertTrue(pdf.exists());
        assertTrue(pdf.getName().equals("note.pdf"));
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("expected decoded rtf text in pdf", text.contains("Hello RTF"));
        }
    }

    @Test
    public void acceptsOnlyRtfFiles() {
        RtfToPdfConverter c = new RtfToPdfConverter();
        assertTrue(c.accepts(new File("a.rtf")));
        assertTrue(c.accepts(new File("A.RTF")));
    }
}
