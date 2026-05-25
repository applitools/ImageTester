package com.applitools.imagetester.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PdfVectorWatermarkAutoModeTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File inputDir;
    private File outDir;
    private ByteArrayOutputStream captured;
    private Logger logger;

    @Before
    public void setUp() throws IOException {
        inputDir = tempFolder.newFolder("input");
        outDir = tempFolder.newFolder("out");
        writeBlankPdf(new File(inputDir, "only.pdf"));
        captured = new ByteArrayOutputStream();
        logger = new Logger(new PrintStream(captured, true, "UTF-8"), false);
    }

    @Test
    public void single_pdf_input_exits_with_non_zero_status() {
        int exit = PdfVectorWatermarkAutoMode.run(inputDir, outDir, null, logger);

        assertEquals(1, exit);
    }

    @Test
    public void single_pdf_input_writes_no_cleaned_output() {
        PdfVectorWatermarkAutoMode.run(inputDir, outDir, null, logger);

        assertEquals(0, outDir.list().length);
    }

    @Test
    public void single_pdf_input_prints_the_friendly_notice() throws Exception {
        PdfVectorWatermarkAutoMode.run(inputDir, outDir, null, logger);

        assertTrue(captured.toString(StandardCharsets.UTF_8.name())
                .contains(PdfVectorWatermarkAutoMode.SINGLE_PDF_NOTICE));
    }

    @Test
    public void heterogeneous_subfolders_each_fingerprint_independently() throws Exception {
        File parent = tempFolder.newFolder("input-multi");
        File preDir = new File(parent, "pre");
        File uatDir = new File(parent, "uat");
        assertTrue(preDir.mkdirs());
        assertTrue(uatDir.mkdirs());
        writeBlankPdf(new File(preDir, "a.pdf"));
        writeBlankPdf(new File(preDir, "b.pdf"));
        writeBlankPdf(new File(uatDir, "c.pdf"));
        writeBlankPdf(new File(uatDir, "d.pdf"));
        File output = tempFolder.newFolder("out-multi");

        int exit = PdfVectorWatermarkAutoMode.run(parent, output, null, logger);

        assertEquals(0, exit);
        String log = captured.toString(StandardCharsets.UTF_8.name());
        assertTrue("pre/ should be fingerprinted as its own group", log.contains("[pre]"));
        assertTrue("uat/ should be fingerprinted as its own group", log.contains("[uat]"));
    }

    @Test
    public void heterogeneous_subfolders_preserve_directory_structure_in_output() throws Exception {
        File parent = tempFolder.newFolder("input-multi");
        File preDir = new File(parent, "pre");
        File uatDir = new File(parent, "uat");
        assertTrue(preDir.mkdirs());
        assertTrue(uatDir.mkdirs());
        writeBlankPdf(new File(preDir, "a.pdf"));
        writeBlankPdf(new File(preDir, "b.pdf"));
        writeBlankPdf(new File(uatDir, "c.pdf"));
        writeBlankPdf(new File(uatDir, "d.pdf"));
        File output = tempFolder.newFolder("out-multi");

        PdfVectorWatermarkAutoMode.run(parent, output, null, logger);

        assertTrue(new File(output, "pre/a.pdf").exists());
        assertTrue(new File(output, "pre/b.pdf").exists());
        assertTrue(new File(output, "uat/c.pdf").exists());
        assertTrue(new File(output, "uat/d.pdf").exists());
    }

    private void writeBlankPdf(File target) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.LETTER));
            doc.save(target);
        }
    }
}
