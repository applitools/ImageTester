package Tests;

import com.applitools.imagetester.lib.converters.FormatConverter;
import com.applitools.imagetester.lib.converters.LibreOfficeConverter;
import com.applitools.imagetester.lib.converters.LibreOfficeLocator;
import com.applitools.imagetester.lib.converters.MarkdownToPdfConverter;
import com.applitools.imagetester.lib.converters.RtfToPdfConverter;
import com.applitools.imagetester.lib.converters.TxtToPdfConverter;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class MultiFormatIntegrationTest {

    private static final File FIXTURE_DIR = new File("TestData/multi-format");
    private static boolean libreOfficePresent;

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void detectLibreOffice() {
        libreOfficePresent = new LibreOfficeLocator().locate().isPresent();
    }

    @Test
    public void txtFixtureConvertsToValidPdf() throws Exception {
        assertConvertsToValidPdf(new TxtToPdfConverter(), "sample.txt");
    }

    @Test
    public void markdownFixtureConvertsToValidPdf() throws Exception {
        assertConvertsToValidPdf(new MarkdownToPdfConverter(), "sample.md");
    }

    @Test
    public void rtfFixtureConvertsToValidPdf() throws Exception {
        assertConvertsToValidPdf(new RtfToPdfConverter(), "sample.rtf");
    }

    @Test
    public void docxFixtureConvertsWhenLibreOfficePresent() throws Exception {
        assumeTrue("LibreOffice not present - skipping", libreOfficePresent);
        assertConvertsToValidPdf(new LibreOfficeConverter(), "sample.docx");
    }

    @Test
    public void pptxFixtureConvertsWhenLibreOfficePresent() throws Exception {
        assumeTrue("LibreOffice not present - skipping", libreOfficePresent);
        assertConvertsToValidPdf(new LibreOfficeConverter(), "sample.pptx");
    }

    @Test
    public void psFixtureConvertsWhenLibreOfficePresent() throws Exception {
        assumeTrue("LibreOffice not present - skipping", libreOfficePresent);
        assertConvertsToValidPdf(new LibreOfficeConverter(), "sample.ps");
    }

    private void assertConvertsToValidPdf(FormatConverter converter, String fixtureName) throws Exception {
        File fixture = new File(FIXTURE_DIR, fixtureName);
        assertTrue("fixture missing: " + fixture, fixture.exists());
        Path tempDir = tempFolder.getRoot().toPath();
        File pdf = converter.convertToPdf(fixture, tempDir);
        assertTrue("pdf not produced", pdf.exists());
        assertTrue("pdf is empty", pdf.length() > 0);
        byte[] head = new byte[4];
        try (java.io.InputStream in = new java.io.FileInputStream(pdf)) {
            int read = in.read(head);
            assertTrue("could not read pdf header", read == 4);
        }
        assertTrue("missing %PDF header",
                head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46);
    }
}
