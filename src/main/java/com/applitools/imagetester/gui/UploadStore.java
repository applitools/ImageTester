package com.applitools.imagetester.gui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Persists files uploaded from the GUI's drag-and-drop and hands back their temp paths. */
public final class UploadStore {

    static final long MAX_UPLOAD_BYTES = 1024L * 1024 * 1024;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    public static final class InvalidNameException extends RuntimeException {
        InvalidNameException(String message) { super(message); }
    }

    public static final class TooLargeException extends RuntimeException {
        TooLargeException(String message) { super(message); }
    }

    private final long maxBytes;
    private Path root;
    private int uploadCount;

    public UploadStore() { this(MAX_UPLOAD_BYTES); }

    UploadStore(long maxBytes) { this.maxBytes = maxBytes; }

    /**
     * Streams the body into a fresh numbered subdirectory (so Doc 1 and Doc 2 may share a
     * filename) and returns the stored file's absolute path.
     */
    public synchronized Path save(String name, InputStream body) throws IOException {
        validateName(name);
        if (root == null) root = Files.createTempDirectory("imagetester-uploads-");
        Path dir = root.resolve(String.valueOf(++uploadCount));
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(file)) {
            long total = 0;
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int read;
            while ((read = body.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new TooLargeException("Upload exceeds " + maxBytes + " bytes");
                out.write(buffer, 0, read);
            }
        } catch (TooLargeException e) {
            Files.deleteIfExists(file);
            throw e;
        }
        return file.toAbsolutePath();
    }

    /** Recursively removes every uploaded file; called when the server stops. */
    public synchronized void deleteAll() throws IOException {
        if (root == null) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) { /* best effort */ }
            });
        }
        root = null;
        uploadCount = 0;
    }

    private static void validateName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\")) {
            throw new InvalidNameException("Invalid file name");
        }
    }
}
