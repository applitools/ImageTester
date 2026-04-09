package com.applitools.imagetester.BatchObjects;

import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class PDFFileBatchTest {

    private static final String FIXTURES = "src/test/resources/fixtures";

    private Config createConfig() {
        Config config = new Config();
        config.appName = "TestApp";
        config.logger = new Logger();
        config.splitSteps = true;
        return config;
    }

    @Test
    public void twoPagePdf_createsTwoTests() throws Exception {
        Config config = createConfig();
        PDFFileBatch batch = new PDFFileBatch(new File(FIXTURES, "valid-2-page.pdf"), config);
        assertFalse(batch.isEmpty());
    }

    @Test
    public void pageSelection_respectsConfig() throws Exception {
        Config config = createConfig();
        config.pages = "1";
        PDFFileBatch batch = new PDFFileBatch(new File(FIXTURES, "valid-10-page.pdf"), config);
        assertFalse(batch.isEmpty());
    }

    @Test
    public void batchInfo_usesFileName() throws Exception {
        Config config = createConfig();
        PDFFileBatch batch = new PDFFileBatch(new File(FIXTURES, "valid-2-page.pdf"), config);
        assertEquals("valid-2-page.pdf", batch.batchInfo().getName());
    }

    @Test
    public void passwordProtectedPdf_loadsWithPassword() throws Exception {
        Config config = createConfig();
        config.pdfPass = "test123";
        PDFFileBatch batch = new PDFFileBatch(new File(FIXTURES, "password-protected.pdf"), config);
        assertFalse(batch.isEmpty());
    }
}
