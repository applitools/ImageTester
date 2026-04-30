package com.applitools.imagetester.lib.converters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class LibreOfficeLocator {

    private static final Path[] WINDOWS_CANDIDATES = new Path[]{
            Paths.get("C:\\Program Files\\LibreOffice\\program\\soffice.exe"),
            Paths.get("C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe")
    };

    private static final Path[] MAC_CANDIDATES = new Path[]{
            Paths.get("/Applications/LibreOffice.app/Contents/MacOS/soffice")
    };

    private static final Path[] LINUX_CANDIDATES = new Path[]{
            Paths.get("/usr/bin/soffice"),
            Paths.get("/usr/lib/libreoffice/program/soffice")
    };

    private final PathProbe probe;
    private final Path[] wellKnown;
    private Optional<Path> cached;
    private boolean evaluated = false;

    public LibreOfficeLocator() {
        this(new PathProbe.Default(), defaultCandidatesForThisOs());
    }

    LibreOfficeLocator(PathProbe probe, Path[] wellKnown) {
        this.probe = probe;
        this.wellKnown = wellKnown;
    }

    public synchronized Optional<Path> locate() {
        if (evaluated) return cached;
        Optional<Path> onPath = probe.onPath("soffice");
        if (onPath.isPresent()) {
            cached = onPath;
        } else {
            cached = Optional.empty();
            for (Path candidate : wellKnown) {
                Optional<Path> hit = probe.at(candidate);
                if (hit.isPresent()) { cached = hit; break; }
            }
        }
        evaluated = true;
        return cached;
    }

    private static Path[] defaultCandidatesForThisOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return WINDOWS_CANDIDATES;
        if (os.contains("mac")) return MAC_CANDIDATES;
        return LINUX_CANDIDATES;
    }
}
