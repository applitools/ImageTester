package com.applitools.imagetester.lib.converters;

import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConversionRegistryTest {

    private static final class StubConverter implements FormatConverter {
        private final String name;
        private final String extension;
        StubConverter(String name, String extension) {
            this.name = name;
            this.extension = extension;
        }
        @Override public boolean accepts(File file) {
            return file.getName().toLowerCase().endsWith("." + extension);
        }
        @Override public File convertToPdf(File file, Path tempDir) {
            throw new UnsupportedOperationException("stub");
        }
        @Override public String toString() { return name; }
    }

    @Test
    public void emptyRegistryReturnsAbsent() {
        ConversionRegistry registry = new ConversionRegistry();
        assertFalse(registry.find(new File("a.docx")).isPresent());
    }

    @Test
    public void firstMatchingConverterWins() {
        StubConverter first = new StubConverter("first", "txt");
        StubConverter second = new StubConverter("second", "txt");
        ConversionRegistry registry = new ConversionRegistry();
        registry.register(first);
        registry.register(second);

        Optional<FormatConverter> match = registry.find(new File("a.txt"));
        assertTrue(match.isPresent());
        assertEquals("first", match.get().toString());
    }

    @Test
    public void nonMatchingExtensionReturnsAbsent() {
        ConversionRegistry registry = new ConversionRegistry();
        registry.register(new StubConverter("only", "txt"));
        assertFalse(registry.find(new File("a.pptx")).isPresent());
    }
}
