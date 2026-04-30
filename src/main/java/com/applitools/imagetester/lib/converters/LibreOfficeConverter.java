package com.applitools.imagetester.lib.converters;

import com.applitools.imagetester.lib.Patterns;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class LibreOfficeConverter implements FormatConverter {

    private static final long DEFAULT_TIMEOUT_SECONDS = 120L;

    private final LibreOfficeLocator locator;
    private final ProcessRunner runner;
    private volatile Path userProfileDir;

    public LibreOfficeConverter() {
        this(new LibreOfficeLocator(), ProcessRunner.forPlatform());
    }

    LibreOfficeConverter(LibreOfficeLocator locator, ProcessRunner runner) {
        this.locator = locator;
        this.runner = runner;
    }

    @Override
    public boolean accepts(File file) {
        String name = file.getName();
        return Patterns.WORD.matcher(name).matches()
            || Patterns.POWERPOINT.matcher(name).matches()
            || Patterns.SPREADSHEET.matcher(name).matches()
            || Patterns.VECTOR.matcher(name).matches();
    }

    @Override
    public File convertToPdf(File file, Path tempDir) throws SkippedFileException, IOException {
        Optional<Path> soffice = locator.locate();
        if (!soffice.isPresent()) {
            throw new SkippedFileException(file, SkipTracker.REASON_LIBREOFFICE_MISSING);
        }

        List<String> command = Arrays.asList(
                soffice.get().toString(),
                "--headless",
                "--norestore",
                "--nolockcheck",
                "--nofirststartwizard",
                "--nologo",
                "--nodefault",
                "-env:UserInstallation=" + profileDir().toUri(),
                "--convert-to", "pdf",
                "--outdir", tempDir.toAbsolutePath().toString(),
                file.getAbsolutePath()
        );

        int exit = runner.run(command, DEFAULT_TIMEOUT_SECONDS);
        if (exit != 0) {
            throw new SkippedFileException(file,
                    String.format("soffice exited %d for %s", exit, file.getName()));
        }

        File produced = tempDir.resolve(basenameWithPdfExtension(file)).toFile();
        if (!produced.exists() || produced.length() == 0) {
            throw new SkippedFileException(file,
                    "soffice produced no output pdf for " + file.getName());
        }
        return produced;
    }

    private Path profileDir() throws IOException {
        Path dir = userProfileDir;
        if (dir != null) return dir;
        synchronized (this) {
            if (userProfileDir == null) {
                Path created = Files.createTempDirectory("imagetester-soffice-profile-");
                created.toFile().deleteOnExit();
                userProfileDir = created;
            }
            return userProfileDir;
        }
    }

    private static String basenameWithPdfExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        return base + ".pdf";
    }
}
