package com.applitools.imagetester.gui;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.util.HashMap;
import static org.junit.Assert.*;

public class RunControllerWatermarkTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Asserts that rwauto=true without rwo routes through the Suite path (upload), not the
     * standalone watermark-out path. The run must reach RunState.Done with outputDir == null
     * (no WatermarkCleaned event, just a normal RunFinished).
     */
    @Test
    public void rwautoWithoutRwoReachesDoneWithNullOutputDir() throws Exception {
        RunController c = new RunController(SecretsStore.inMemoryForTest(), new RunStream());
        c.setSecretApiKey("sk_test");
        File in = tmp.newFolder("pdfs"); // empty — no PDFs; PdfVectorWatermarkAutoMode no-ops

        RunRequest req = new RunRequest();
        req.sourcePath = in.getAbsolutePath();
        req.options = new HashMap<>();
        req.options.put("rwauto", Boolean.TRUE);
        // NOTE: no "rwo" key — must take the auto-clean-then-upload path, not the watermark-out path

        c.start(req);

        long deadline = System.currentTimeMillis() + 10_000;
        while (!(c.snapshot() instanceof RunState.Done) && System.currentTimeMillis() < deadline)
            Thread.sleep(50);
        assertTrue("run did not reach Done within 10s", c.snapshot() instanceof RunState.Done);
        assertNull("outputDir must be null — rwauto without rwo must reach the normal Suite done, not WatermarkCleaned",
                ((RunState.Done) c.snapshot()).outputDir);
    }

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
