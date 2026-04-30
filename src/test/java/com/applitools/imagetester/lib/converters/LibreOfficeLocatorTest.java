package com.applitools.imagetester.lib.converters;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LibreOfficeLocatorTest {

    private static final class StubProbe implements PathProbe {
        final Optional<Path> pathHit;
        final Set<Path> existing;
        final List<String> onPathCalls = new ArrayList<>();
        final List<Path> atCalls = new ArrayList<>();
        StubProbe(Optional<Path> pathHit, Set<Path> existing) {
            this.pathHit = pathHit;
            this.existing = existing;
        }
        @Override public Optional<Path> onPath(String exe) {
            onPathCalls.add(exe);
            return pathHit;
        }
        @Override public Optional<Path> at(Path candidate) {
            atCalls.add(candidate);
            return existing.contains(candidate) ? Optional.of(candidate) : Optional.empty();
        }
    }

    @Test
    public void prefersSofficeFoundOnPath() {
        Path onPath = Paths.get("/usr/local/bin/soffice");
        StubProbe probe = new StubProbe(Optional.of(onPath), new HashSet<>());
        LibreOfficeLocator locator = new LibreOfficeLocator(probe,
                new Path[]{Paths.get("/Applications/LibreOffice.app/Contents/MacOS/soffice")});

        assertEquals(Optional.of(onPath), locator.locate());
        assertEquals("soffice", probe.onPathCalls.get(0));
        assertTrue("should not consult well-known paths when PATH hit",
                probe.atCalls.isEmpty());
    }

    @Test
    public void fallsBackToWellKnownPathWhenNotOnPath() {
        Path candidate = Paths.get("/Applications/LibreOffice.app/Contents/MacOS/soffice");
        Set<Path> existing = new HashSet<>();
        existing.add(candidate);
        StubProbe probe = new StubProbe(Optional.empty(), existing);
        LibreOfficeLocator locator = new LibreOfficeLocator(probe, new Path[]{candidate});

        assertEquals(Optional.of(candidate), locator.locate());
    }

    @Test
    public void returnsAbsentWhenNeitherPathNorWellKnownHaveIt() {
        StubProbe probe = new StubProbe(Optional.empty(), new HashSet<>());
        LibreOfficeLocator locator = new LibreOfficeLocator(probe,
                new Path[]{Paths.get("/nonexistent")});

        assertFalse(locator.locate().isPresent());
    }

    @Test
    public void locateResultIsCached() {
        StubProbe probe = new StubProbe(Optional.of(Paths.get("/bin/soffice")), new HashSet<>());
        LibreOfficeLocator locator = new LibreOfficeLocator(probe, new Path[0]);

        locator.locate();
        locator.locate();
        locator.locate();
        assertEquals("probe should be consulted once across repeated lookups",
                1, probe.onPathCalls.size());
    }
}
