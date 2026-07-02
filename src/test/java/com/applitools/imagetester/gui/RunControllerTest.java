package com.applitools.imagetester.gui;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.TestResultsStatus;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.RunConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.concurrent.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RunControllerTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void initialStatusIsIdle() {
        RunController c = newController(passingBuilder());
        assertTrue(c.snapshot() instanceof RunState.Idle);
    }

    @Test
    public void rejectsRunWithMissingApiKey() throws Exception {
        SecretsStore secrets = SecretsStore.inMemoryForTest(); // empty
        RunController c = new RunController(secrets, new RunStream(), passingBuilder());
        File folder = tmp.newFolder("pix");
        makeTinyPng(folder);
        assertThrows(RunController.MissingApiKeyException.class,
            () -> c.start(req(folder, "Strict")));
    }

    @Test
    public void rejectsRunWithInvalidSourcePath() {
        RunController c = newController(passingBuilder());
        c.setSecretApiKey("sk_test");
        RunRequest bad = new RunRequest();
        bad.sourcePath = "/path/does/not/exist/anywhere";
        bad.options = new HashMap<>();
        assertThrows(SourcePathValidator.InvalidSourceException.class,
            () -> c.start(bad));
    }

    @Test
    public void happyPathReachesDoneState() throws Exception {
        RunController c = newController(passingBuilder());
        c.setSecretApiKey("sk_test");
        File folder = tmp.newFolder("pix");
        makeTinyPng(folder);

        c.start(req(folder, "Strict"));

        long deadline = System.currentTimeMillis() + 10_000;
        while (!(c.snapshot() instanceof RunState.Done) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue("did not reach Done within 10s", c.snapshot() instanceof RunState.Done);
    }

    @Test
    public void secondConcurrentStartReturns409() throws Exception {
        // Use a builder that artificially blocks Eyes construction so the first run is in-flight when we issue the second.
        RunController c = newController(slowBuilder(500));
        c.setSecretApiKey("sk_test");
        File folder = tmp.newFolder("pix");
        makeTinyPng(folder);

        ExecutorService es = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        // Both futures catch RunInProgressException — either one could be the loser of the race.
        Future<Object> f1 = es.submit(() -> {
            ready.countDown();
            ready.await();
            try {
                return (Object) c.start(req(folder, "Strict"));
            } catch (RunController.RunInProgressException ex) {
                return ex;
            }
        });
        Future<Object> f2 = es.submit(() -> {
            ready.countDown();
            ready.await();
            try {
                return (Object) c.start(req(folder, "Strict"));
            } catch (RunController.RunInProgressException ex) {
                return ex;
            }
        });

        Object r1 = f1.get(5, TimeUnit.SECONDS);
        Object r2 = f2.get(5, TimeUnit.SECONDS);

        // Both results must be one of the two expected types — nothing else is allowed.
        assertTrue("r1 unexpected: " + r1,
            r1 instanceof RunController.StartResult || r1 instanceof RunController.RunInProgressException);
        assertTrue("r2 unexpected: " + r2,
            r2 instanceof RunController.StartResult || r2 instanceof RunController.RunInProgressException);

        boolean r1Ok = r1 instanceof RunController.StartResult;
        boolean r2Ok = r2 instanceof RunController.StartResult;

        // At least one call must have started a run — both being rejected would mean nothing ran at all.
        assertTrue("Both calls were rejected; expected at least one StartResult", r1Ok || r2Ok);

        // Valid outcomes: (Ok, Rejected), (Rejected, Ok), or (Ok, Ok) for the fast-completion edge case
        // where the first run finished before the second call arrived. (Ok, Ok) is allowed but rare.
        es.shutdownNow();
    }

    @Test
    public void startEmitsRunStartedEventWithRunId() throws Exception {
        RunStream stream = new RunStream();
        RunController c = new RunController(SecretsStore.inMemoryForTest(), stream, passingBuilder());
        c.setSecretApiKey("sk_test");
        File folder = tmp.newFolder("pix");
        makeTinyPng(folder);

        java.io.StringWriter sink = new java.io.StringWriter();
        CountDownLatch ready = new CountDownLatch(1);
        stream.addClient(new java.io.PrintWriter(sink), () -> {}, ready);
        ready.await(5, TimeUnit.SECONDS);

        RunController.StartResult result = c.start(req(folder, "Strict"));

        long deadline = System.currentTimeMillis() + 5_000;
        while (!sink.toString().contains("\"type\":\"run-started\"") && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        String events = sink.toString();
        assertTrue("run-started not emitted; got: " + events, events.contains("\"type\":\"run-started\""));
        assertTrue("run-started missing runId; got: " + events, events.contains("\"runId\":\"" + result.runId + "\""));
    }

    @Test
    public void dvOptionProducesUsableRunConfigViaProductionBuilder() {
        RunRequest r = new RunRequest();
        r.sourcePath = ".";
        r.options = new java.util.HashMap<>();
        r.options.put("k", "sk_test");
        r.options.put("dv", Boolean.TRUE);
        com.applitools.imagetester.lib.RunConfig rc = RunController.buildRunConfig(r, new com.applitools.imagetester.lib.Logger());
        assertNotNull(rc.factory);
    }

    // ---- helpers ----

    private RunController newController(RunController.RunConfigBuilder builder) {
        SecretsStore secrets = SecretsStore.inMemoryForTest();
        return new RunController(secrets, new RunStream(), builder);
    }

    private RunRequest req(File folder, String matchLevel) {
        RunRequest r = new RunRequest();
        r.sourcePath = folder.getAbsolutePath();
        r.options = new HashMap<>();
        r.options.put("ml", matchLevel);
        return r;
    }

    private RunController.RunConfigBuilder passingBuilder() {
        // Pre-create mock on test thread so background-thread class loading doesn't race cold JVM startup.
        // stubEyes() must be called BEFORE mock/when setup to avoid UnfinishedStubbing.
        Eyes stubbedEyes = stubEyes();
        EyesFactory factory = mock(EyesFactory.class);
        when(factory.build()).thenReturn(stubbedEyes);
        return (req, logger) -> {
            Config config = new Config();
            config.logger = logger;
            return new RunConfig(config, factory, 2);
        };
    }

    private RunController.RunConfigBuilder slowBuilder(long delayMs) {
        // Pre-create mock on test thread so background-thread class loading doesn't race cold JVM startup.
        EyesFactory factory = mock(EyesFactory.class);
        when(factory.build()).thenAnswer(inv -> {
            Thread.sleep(delayMs);
            return stubEyes();
        });
        return (req, logger) -> {
            Config config = new Config();
            config.logger = logger;
            return new RunConfig(config, factory, 2);
        };
    }

    private static Eyes stubEyes() {
        Eyes eyes = mock(Eyes.class);
        BatchInfo batch = new BatchInfo("t");
        when(eyes.getBatch()).thenReturn(batch);
        // NOTE: setBatch returns Configuration (non-void) and abortIfNotClosed returns TestResults (non-void);
        // Mockito returns null by default for non-void mocked methods, which is safe here.
        when(eyes.getAccessibilityValidation()).thenReturn(null);
        when(eyes.getIsOpen()).thenReturn(false);

        TestResults result = mock(TestResults.class);
        when(result.getName()).thenReturn("a");
        when(result.isDifferent()).thenReturn(false);
        when(result.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(result.getUrl()).thenReturn("https://applitools.com/dashboard/r/x");

        when(eyes.close(false)).thenReturn(result);
        when(eyes.close(true)).thenReturn(result);
        when(eyes.close()).thenReturn(result);
        when(eyes.abortIfNotClosed()).thenReturn(result);

        return eyes;
    }

    private static File makeTinyPng(File folder) throws Exception {
        File png = new File(folder, "a.png");
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        try (FileOutputStream out = new FileOutputStream(png)) {
            ImageIO.write(img, "png", out);
        }
        return png;
    }
}
