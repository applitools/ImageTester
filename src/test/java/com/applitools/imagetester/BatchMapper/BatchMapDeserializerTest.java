package com.applitools.imagetester.BatchMapper;

import org.junit.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

public class BatchMapDeserializerTest {

    private static final String FIXTURES = "src/test/resources/fixtures";

    @Test
    public void readFile_parsesCorrectNumberOfRows() throws IOException {
        List<BatchMapPojo> result = BatchMapDeserializer.readFile(FIXTURES + "/batchmap-test.csv");
        assertEquals(2, result.size());
    }

    @Test
    public void readFile_parsesFirstRowCorrectly() throws IOException {
        List<BatchMapPojo> result = BatchMapDeserializer.readFile(FIXTURES + "/batchmap-test.csv");
        BatchMapPojo first = result.get(0);
        assertEquals("/path/to/file.pdf", first.getFilePath());
        assertEquals("Test One", first.getTestName());
        assertEquals("MyApp", first.getApp());
        assertEquals("Windows", first.getOs());
        assertEquals("Chrome", first.getBrowser());
        assertEquals("1024x768", first.getViewport());
        assertEquals("1000x", first.getMatchsize());
        assertEquals("1-3", first.getPages());
        assertEquals("Strict", first.getMatchLevel());
    }

    // NOTE: Jackson's CsvMapper uses public field access when setters are broken,
    // so ignoreRegions is populated correctly from the CSV even though
    // setIgnoreRegions() is a no-arg setter.
    @Test
    public void readFile_parsesIgnoreRegionsFromCsv() throws IOException {
        List<BatchMapPojo> result = BatchMapDeserializer.readFile(FIXTURES + "/batchmap-test.csv");
        assertEquals("10,20,100,50", result.get(0).getIgnoreRegions());
    }

    @Test
    public void readFile_parsesSecondRowMatchLevel() throws IOException {
        List<BatchMapPojo> result = BatchMapDeserializer.readFile(FIXTURES + "/batchmap-test.csv");
        assertEquals("Layout", result.get(1).getMatchLevel());
    }

    @Test(expected = IOException.class)
    public void readFile_missingFile_throwsIOException() throws IOException {
        BatchMapDeserializer.readFile("/nonexistent/path/file.csv");
    }
}
