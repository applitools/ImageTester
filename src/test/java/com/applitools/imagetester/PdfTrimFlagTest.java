package com.applitools.imagetester;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.Test;

import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.RunConfigFactory;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The GUI checkbox sends a bare {@code -tp}, which must mean auto-detect;
 * an explicit size remains available for files whose marks are rasterized.
 */
public class PdfTrimFlagTest {

    private static Config configFor(String... args) throws Exception {
        Method getOptions = ImageTester.class.getDeclaredMethod("getOptions");
        getOptions.setAccessible(true);
        Options options = (Options) getOptions.invoke(null);
        CommandLine cmd = new DefaultParser().parse(options, args);
        return RunConfigFactory.from(cmd, new Logger()).config;
    }

    @Test
    public void bareTpFlagEnablesAutoTrim() throws Exception {
        assertEquals(Config.PDF_TRIM_AUTO, configFor("-tp").pdfTrim);
    }

    @Test
    public void tpWithSizeKeepsTheExplicitValue() throws Exception {
        assertEquals("603x774", configFor("-tp", "603x774").pdfTrim);
    }

    @Test
    public void noTpFlagLeavesTrimOff() throws Exception {
        assertNull(configFor().pdfTrim);
    }
}
