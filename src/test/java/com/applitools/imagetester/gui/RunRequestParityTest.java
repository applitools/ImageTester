package com.applitools.imagetester.gui;

import com.applitools.imagetester.ImageTester;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.RunConfig;
import com.applitools.imagetester.lib.RunConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.junit.Test;
import java.util.HashMap;
import static org.junit.Assert.*;

public class RunRequestParityTest {

    private RunConfig fromArgs(String... args) throws Exception {
        CommandLine cmd = new DefaultParser().parse(ImageTester.getOptions(), args);
        return RunConfigFactory.from(cmd, new Logger());
    }

    @Test
    public void guiPayloadMatchesCliForDpi() throws Exception {
        RunConfig cli = fromArgs("-k", "key", "-f", ".", "-di", "400");

        RunRequest req = new RunRequest();
        req.sourcePath = ".";
        req.options = new HashMap<>();
        req.options.put("k", "key");
        req.options.put("di", "400");
        RunConfig gui = fromArgs(RunRequestTranslator.toArgv(req));

        assertEquals(cli.config.DocumentConversionDPI, gui.config.DocumentConversionDPI, 0.001f);
    }
}
