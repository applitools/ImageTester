package com.applitools.imagetester.lib;

import com.applitools.imagetester.ImageTester;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.Test;
import static org.junit.Assert.*;

public class RunConfigFactoryTest {

    private CommandLine parse(String... args) throws Exception {
        Options options = ImageTester.getOptions();
        return new DefaultParser().parse(options, args);
    }

    @Test
    public void from_parentBranchWithoutBranch_failsAtParseTime() throws Exception {
        CommandLine cmd = parse("-k", "key", "-f", ".", "-pb", "garbage");
        assertThrows(org.apache.commons.cli.ParseException.class,
                () -> RunConfigFactory.from(cmd, new Logger()));
    }

    @Test
    public void from_parentBranchWithBranch_succeeds() throws Exception {
        CommandLine cmd = parse("-k", "key", "-f", ".", "-pb", "parent", "-br", "child");
        assertNotNull(RunConfigFactory.from(cmd, new Logger()).factory);
    }

    @Test
    public void mapsThreadsFromArg() throws Exception {
        CommandLine cmd = parse("-k", "key", "-f", ".", "-th", "7");
        RunConfig rc = RunConfigFactory.from(cmd, new Logger());
        assertEquals(7, rc.threads);
    }

    @Test
    public void mapsAppNameFromArg() throws Exception {
        CommandLine cmd = parse("-k", "key", "-f", ".", "-a", "MyApp");
        RunConfig rc = RunConfigFactory.from(cmd, new Logger());
        assertEquals("MyApp", rc.config.appName);
    }
}
