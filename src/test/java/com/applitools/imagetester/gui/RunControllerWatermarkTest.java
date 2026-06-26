package com.applitools.imagetester.gui;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.util.HashMap;
import static org.junit.Assert.*;

public class RunControllerWatermarkTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void rwoModeReachesDoneWithOutputDir() throws Exception {
        RunController c = new RunController(SecretsStore.inMemoryForTest(), new RunStream());
        c.setSecretApiKey("sk_test");
        File in = tmp.newFolder("pdfs");
        File out = tmp.newFolder("out");

        RunRequest req = new RunRequest();
        req.sourcePath = in.getAbsolutePath();
        req.options = new HashMap<>();
        req.options.put("rw", "CONFIDENTIAL");
        req.options.put("rwo", out.getAbsolutePath());

        c.start(req);

        long deadline = System.currentTimeMillis() + 5_000;
        while (!(c.snapshot() instanceof RunState.Done) && System.currentTimeMillis() < deadline)
            Thread.sleep(50);
        assertTrue(c.snapshot() instanceof RunState.Done);
        assertEquals(out.getAbsolutePath(), ((RunState.Done) c.snapshot()).outputDir);
    }
}
