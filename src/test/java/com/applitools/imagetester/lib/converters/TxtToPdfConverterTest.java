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

public class TxtToPdfConverterTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void convertsTxtFileToPdfContainingTheText() throws Exception {
        File input = tempFolder.newFile("notes.txt");
        Files.write(input.toPath(), "hello world".getBytes(StandardCharsets.UTF_8));

        File pdf = new TxtToPdfConverter().convertToPdf(input, tempFolder.getRoot().toPath());

        assertTrue("expected pdf to exist", pdf.exists());
        assertTrue("expected pdf basename to match input", pdf.getName().equals("notes.pdf"));
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("expected rendered text in pdf", text.contains("hello world"));
        }
    }

    @Test
    public void acceptsOnlyTxtFiles() {
        TxtToPdfConverter c = new TxtToPdfConverter();
        assertTrue(c.accepts(new File("a.txt")));
        assertTrue("case-insensitive", c.accepts(new File("A.TXT")));
    }

    @Test
    public void rejectsNonTxt() {
        assertTrue(!new TxtToPdfConverter().accepts(new File("a.md")));
    }
}
