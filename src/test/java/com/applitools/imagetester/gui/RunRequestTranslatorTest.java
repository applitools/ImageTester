package com.applitools.imagetester.gui;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RunRequestTranslatorTest {

    private List<String> argv(RunRequest r) {
        return Arrays.asList(RunRequestTranslator.toArgv(r));
    }

    @Test
    public void emitsSourcePathAsFolderFlag() {
        RunRequest r = new RunRequest();
        r.sourcePath = "/tmp/pix";
        r.options = new HashMap<>();
        List<String> a = argv(r);
        assertEquals("/tmp/pix", a.get(a.indexOf("-f") + 1));
    }

    @Test
    public void emitsScalarOptionWithValue() {
        RunRequest r = new RunRequest();
        r.sourcePath = ".";
        r.options = new HashMap<>();
        r.options.put("di", "300");
        List<String> a = argv(r);
        assertEquals("300", a.get(a.indexOf("-di") + 1));
    }

    @Test
    public void emitsBooleanFlagWithoutValue() {
        RunRequest r = new RunRequest();
        r.sourcePath = ".";
        r.options = new HashMap<>();
        r.options.put("nf", Boolean.TRUE);
        assertTrue(argv(r).contains("-nf"));
    }

    @Test
    public void omitsFalseBooleanFlag() {
        RunRequest r = new RunRequest();
        r.sourcePath = ".";
        r.options = new HashMap<>();
        r.options.put("nf", Boolean.FALSE);
        assertFalse(argv(r).contains("-nf"));
    }

    @Test
    public void neverEmitsThrowExceptionsFlag() {
        RunRequest r = new RunRequest();
        r.sourcePath = ".";
        r.options = new HashMap<>();
        r.options.put("te", Boolean.TRUE);
        assertFalse(argv(r).contains("-te"));
    }

    @Test
    public void neverEmitsBatchMapperFlag() {
        RunRequest r = new RunRequest();
        r.sourcePath = ".";
        r.options = new HashMap<>();
        r.options.put("mp", "/some.csv");
        assertFalse(argv(r).contains("-mp"));
    }
}
