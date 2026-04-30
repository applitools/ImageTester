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

public class MarkdownToPdfConverterTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void convertsMarkdownHeadingAndBoldIntoPdfText() throws Exception {
        File input = tempFolder.newFile("readme.md");
        String md = "# Report\n\nThis is **bold** text.\n";
        Files.write(input.toPath(), md.getBytes(StandardCharsets.UTF_8));

        File pdf = new MarkdownToPdfConverter().convertToPdf(input, tempFolder.getRoot().toPath());

        assertTrue(pdf.exists());
        assertTrue("expected basename preserved", pdf.getName().equals("readme.pdf"));
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("expected heading in pdf", text.contains("Report"));
            assertTrue("expected bold word in pdf", text.contains("bold"));
        }
    }

    @Test
    public void acceptsOnlyMdFiles() {
        MarkdownToPdfConverter c = new MarkdownToPdfConverter();
        assertTrue(c.accepts(new File("a.md")));
        assertTrue(c.accepts(new File("A.MD")));
    }
}
