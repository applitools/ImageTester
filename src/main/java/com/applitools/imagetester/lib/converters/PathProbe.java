package com.applitools.imagetester.lib.converters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public interface PathProbe {

    Optional<Path> onPath(String exe);

    Optional<Path> at(Path candidate);

    final class Default implements PathProbe {
        @Override public Optional<Path> onPath(String exe) {
            String path = System.getenv("PATH");
            if (path == null) return Optional.empty();
            String exeOnWindows = exe + ".exe";
            for (String entry : path.split(File.pathSeparator)) {
                if (entry.isEmpty()) continue;
                Path p1 = Paths.get(entry, exe);
                if (Files.isExecutable(p1)) return Optional.of(p1.toAbsolutePath());
                Path p2 = Paths.get(entry, exeOnWindows);
                if (Files.isExecutable(p2)) return Optional.of(p2.toAbsolutePath());
            }
            return Optional.empty();
        }

        @Override public Optional<Path> at(Path candidate) {
            return Files.isExecutable(candidate) ? Optional.of(candidate.toAbsolutePath())
                                                  : Optional.empty();
        }
    }
}
