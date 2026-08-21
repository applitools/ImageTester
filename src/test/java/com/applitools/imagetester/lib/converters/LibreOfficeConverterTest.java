package com.applitools.imagetester.lib.converters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LibreOfficeConverterTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final class StubLocator extends LibreOfficeLocator {
        private final Optional<Path> hit;
        StubLocator(Optional<Path> hit) {
            super(new PathProbe() {
                @Override public Optional<Path> onPath(String exe) { return Optional.empty(); }
                @Override public Optional<Path> at(Path candidate) { return Optional.empty(); }
            }, new Path[0]);
            this.hit = hit;
        }
        @Override public Optional<Path> locate() { return hit; }
    }

    private static final class RecordingRunner implements ProcessRunner {
        List<String> lastCommand;
        int exitCode;
        boolean createOutputFile;
        File outputFile;
        RecordingRunner(int exitCode) { this.exitCode = exitCode; }

        @Override public int run(List<String> command, long timeoutSeconds) throws java.io.IOException {
            this.lastCommand = new ArrayList<>(command);
            if (createOutputFile && outputFile != null) {
                Files.write(outputFile.toPath(), new byte[]{0x25, 0x50, 0x44, 0x46});
            }
            return exitCode;
        }
    }

    @Test
    public void acceptsAllSupportedOfficeExtensions() {
        LibreOfficeConverter c = new LibreOfficeConverter(
                new StubLocator(Optional.of(Paths.get("/soffice"))), new RecordingRunner(0));
        String[] accepted = {"a.doc", "a.docx", "a.docm", "a.dot", "a.dotx", "a.dotm",
                             "a.ppt", "a.pptx", "a.pptm", "a.pps", "a.ppsx", "a.ppsm",
                             "a.pot", "a.potx", "a.potm",
                             "a.xls", "a.xlsx", "a.xlsm", "a.xlt", "a.xltx", "a.xltm",
                             "a.ods", "a.csv",
                             "a.ps", "a.eps", "a.xps"};
        for (String name : accepted) {
            assertTrue("expected accept for " + name, c.accepts(new File(name)));
        }
    }

    @Test
    public void skipsPostscriptWithUnsupportedReason() throws Exception {
        LibreOfficeConverter c = new LibreOfficeConverter(
                new StubLocator(Optional.of(Paths.get("/soffice"))), new RecordingRunner(0));
        File input = tempFolder.newFile("figure.ps");
        try {
            c.convertToPdf(input, tempFolder.getRoot().toPath());
            fail("expected SkippedFileException");
        } catch (SkippedFileException e) {
            assertEquals(SkipTracker.REASON_POSTSCRIPT_XPS_UNSUPPORTED, e.getReason());
        }
    }

    @Test
    public void skipsXpsWithUnsupportedReason() throws Exception {
        LibreOfficeConverter c = new LibreOfficeConverter(
                new StubLocator(Optional.of(Paths.get("/soffice"))), new RecordingRunner(0));
        File input = tempFolder.newFile("slides.xps");
        try {
            c.convertToPdf(input, tempFolder.getRoot().toPath());
            fail("expected SkippedFileException");
        } catch (SkippedFileException e) {
            assertEquals(SkipTracker.REASON_POSTSCRIPT_XPS_UNSUPPORTED, e.getReason());
        }
    }

    @Test
    public void postscriptSkipNeverInvokesSoffice() throws Exception {
        RecordingRunner runner = new RecordingRunner(0);
        LibreOfficeConverter c = new LibreOfficeConverter(
                new StubLocator(Optional.of(Paths.get("/soffice"))), runner);
        File input = tempFolder.newFile("figure.ps");
        try {
            c.convertToPdf(input, tempFolder.getRoot().toPath());
            fail("expected SkippedFileException");
        } catch (SkippedFileException e) {
            assertEquals(null, runner.lastCommand);
        }
    }

    @Test
    public void throwsSkippedFileExceptionWhenLibreOfficeMissing() throws Exception {
        LibreOfficeConverter c = new LibreOfficeConverter(
                new StubLocator(Optional.empty()), new RecordingRunner(0));
        File input = tempFolder.newFile("report.docx");
        try {
            c.convertToPdf(input, tempFolder.getRoot().toPath());
            fail("expected SkippedFileException");
        } catch (SkippedFileException e) {
            assertEquals(SkipTracker.REASON_LIBREOFFICE_MISSING, e.getReason());
            assertEquals(input, e.getFile());
        }
    }

    @Test
    public void invokesSofficeWithCorrectArguments() throws Exception {
        Path soffice = Paths.get("/usr/bin/soffice");
        RecordingRunner runner = new RecordingRunner(0);
        runner.createOutputFile = true;
        LibreOfficeConverter c = new LibreOfficeConverter(
                new StubLocator(Optional.of(soffice)), runner);

        File input = tempFolder.newFile("report.docx");
        runner.outputFile = tempFolder.getRoot().toPath().resolve("report.pdf").toFile();

        c.convertToPdf(input, tempFolder.getRoot().toPath());

        assertEquals(soffice.toString(), runner.lastCommand.get(0));
        assertTrue(runner.lastCommand.contains("--headless"));
        assertTrue(runner.lastCommand.contains("--convert-to"));
        assertEquals("pdf", runner.lastCommand.get(runner.lastCommand.indexOf("--convert-to") + 1));
        assertTrue(runner.lastCommand.contains("--outdir"));
        assertTrue(runner.lastCommand.contains(input.getAbsolutePath()));
    }

    @Test
    public void skipsWhenSofficeExitsNonZero() throws Exception {
        Path soffice = Paths.get("/usr/bin/soffice");
        LibreOfficeConverter c = new LibreOfficeConverter(
                new StubLocator(Optional.of(soffice)), new RecordingRunner(1));
        File input = tempFolder.newFile("report.docx");
        try {
            c.convertToPdf(input, tempFolder.getRoot().toPath());
            fail("expected SkippedFileException");
        } catch (SkippedFileException e) {
            assertTrue(e.getReason().contains("soffice"));
            assertTrue(e.getReason().contains("1"));
        }
    }

    @Test
    public void skipsWhenOutputPdfIsMissingAfterRun() throws Exception {
        Path soffice = Paths.get("/usr/bin/soffice");
        LibreOfficeConverter c = new LibreOfficeConverter(
                new StubLocator(Optional.of(soffice)), new RecordingRunner(0));
        File input = tempFolder.newFile("report.docx");
        try {
            c.convertToPdf(input, tempFolder.getRoot().toPath());
            fail("expected SkippedFileException");
        } catch (SkippedFileException e) {
            assertTrue(e.getReason().toLowerCase().contains("output"));
        }
    }
}
