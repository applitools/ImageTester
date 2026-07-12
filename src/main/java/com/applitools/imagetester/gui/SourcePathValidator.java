package com.applitools.imagetester.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SourcePathValidator {

    public static final class InvalidSourceException extends RuntimeException {
        public InvalidSourceException(String message) {
            super(message);
        }
    }

    private SourcePathValidator() {}

    public static Path validate(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) {
            throw new InvalidSourceException("Source path is empty.");
        }

        Path resolved;
        try {
            resolved = Paths.get(pathStr).toAbsolutePath().toRealPath();
        } catch (IOException | java.nio.file.InvalidPathException e) {
            throw new InvalidSourceException("Source path could not be resolved: " + e.getMessage());
        }

        if (!Files.exists(resolved)) {
            throw new InvalidSourceException("Source path does not exist: " + resolved);
        }

        if (!Files.isReadable(resolved)) {
            throw new InvalidSourceException("Source path is not readable: " + resolved);
        }

        return resolved;
    }
}
