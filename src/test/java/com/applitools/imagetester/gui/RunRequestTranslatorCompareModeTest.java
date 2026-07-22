package com.applitools.imagetester.gui;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunRequestTranslatorCompareModeTest {

    @Test
    public void doc1AndDoc2ProduceDocFlagsInstily() {
        RunRequest req = new RunRequest();
        req.doc1Path = "/a/doc1.png";
        req.doc2Path = "/a/doc2.png";
        req.options = Map.of("fn", "compare-1");

        String[] argv = RunRequestTranslator.toArgv(req);
        java.util.List<String> asList = Arrays.asList(argv);

        assertTrue(asList.contains("-doc1"));
        assertEquals("/a/doc1.png", asList.get(asList.indexOf("-doc1") + 1));
        assertTrue(asList.contains("-doc2"));
        assertEquals("/a/doc2.png", asList.get(asList.indexOf("-doc2") + 1));
    }

    @Test
    public void doc1PathOmitsDashF() {
        RunRequest req = new RunRequest();
        req.doc1Path = "/a/doc1.png";
        req.doc2Path = "/a/doc2.png";

        String[] argv = RunRequestTranslator.toArgv(req);

        assertFalse(Arrays.asList(argv).contains("-f"));
    }

    @Test
    public void sourcePathStillProducesDashFWhenDocPathsAbsent() {
        RunRequest req = new RunRequest();
        req.sourcePath = "/a/folder";

        String[] argv = RunRequestTranslator.toArgv(req);

        assertTrue(Arrays.asList(argv).contains("-f"));
    }
}
