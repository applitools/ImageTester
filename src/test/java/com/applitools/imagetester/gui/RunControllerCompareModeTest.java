package com.applitools.imagetester.gui;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.TestResultsStatus;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.RunConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RunControllerCompareModeTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void compareModeRoutesToCompareRunnerAndEmitsDoc2AsTheResult() throws Exception {
        RunStream stream = new RunStream();
        RunController c = new RunController(SecretsStore.inMemoryForTest(), stream, passingBuilder());
        c.setSecretApiKey("sk_test");

        File doc1 = makeTinyPng(tmp.newFolder("doc1"), "doc1.png");
        File doc2 = makeTinyPng(tmp.newFolder("doc2"), "doc2.png");

        StringWriter sink = new StringWriter();
        CountDownLatch ready = new CountDownLatch(1);
        stream.addClient(new PrintWriter(sink), () -> {}, ready);
        ready.await(5, TimeUnit.SECONDS);

        RunRequest req = new RunRequest();
        req.doc1Path = doc1.getAbsolutePath();
        req.doc2Path = doc2.getAbsolutePath();
        req.options = new HashMap<>();
        req.options.put("fn", "compare-1");

        c.start(req);

        long deadline = System.currentTimeMillis() + 10_000;
        while (!(c.snapshot() instanceof RunState.Done) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue("did not reach Done within 10s", c.snapshot() instanceof RunState.Done);

        // The RunStream drainer flushes on its own thread — wait for run-finished to land in the
        // sink instead of reading it immediately, or a fast run races the flush and sees nothing.
        while (!sink.toString().contains("\"type\":\"run-finished\"") && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        String events = sink.toString();
        assertEquals("expected exactly one test-finished event; got: " + events,
                1, countOccurrences(events, "\"type\":\"test-finished\""));

        RunState.Done done = (RunState.Done) c.snapshot();
        assertFalse("expected a non-empty tests list", done.tests.isEmpty());
        RunState.TestRow row = done.tests.get(0);
        assertEquals(doc1.getAbsolutePath(), row.previewPath);
        assertEquals(doc2.getAbsolutePath(), row.doc2PreviewPath);
    }

    @Test
    public void compareModeWithoutForcedNameThrowsInvalidOptionsException() throws Exception {
        RunController c = new RunController(SecretsStore.inMemoryForTest(), new RunStream(), passingBuilder());
        c.setSecretApiKey("sk_test");

        File doc1 = makeTinyPng(tmp.newFolder("doc1"), "doc1.png");
        File doc2 = makeTinyPng(tmp.newFolder("doc2"), "doc2.png");

        RunRequest req = new RunRequest();
        req.doc1Path = doc1.getAbsolutePath();
        req.doc2Path = doc2.getAbsolutePath();
        req.options = new HashMap<>();

        assertThrows(RunController.InvalidOptionsException.class, () -> c.start(req));
    }

    @Test
    public void cancelDuringCompareRunMarksRowCancelled() throws Exception {
        RunStream stream = new RunStream();
        CountDownLatch doc1Closing = new CountDownLatch(1);
        CountDownLatch releaseDoc1 = new CountDownLatch(1);
        RunController c = new RunController(SecretsStore.inMemoryForTest(), stream,
                blockingCloseBuilder(doc1Closing, releaseDoc1));
        c.setSecretApiKey("sk_test");

        File doc1 = makeTinyPng(tmp.newFolder("doc1"), "doc1.png");
        File doc2 = makeTinyPng(tmp.newFolder("doc2"), "doc2.png");

        RunRequest req = new RunRequest();
        req.doc1Path = doc1.getAbsolutePath();
        req.doc2Path = doc2.getAbsolutePath();
        req.options = new HashMap<>();
        req.options.put("fn", "compare-cancel");

        c.start(req);
        assertTrue("doc1 never reached close()", doc1Closing.await(10, TimeUnit.SECONDS));
        c.cancel();
        releaseDoc1.countDown();

        long deadline = System.currentTimeMillis() + 10_000;
        while (!(c.snapshot() instanceof RunState.Done) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        RunState.Done done = (RunState.Done) c.snapshot();
        assertEquals("cancelled", done.tests.get(0).status);
    }

    // ---- helpers (mirrors RunControllerTest conventions) ----

    /** Builder whose Eyes.close() blocks until released, so a cancel can land mid-run deterministically. */
    private RunController.RunConfigBuilder blockingCloseBuilder(CountDownLatch closing, CountDownLatch release) {
        TestResults blockedResult = mock(TestResults.class);
        when(blockedResult.isDifferent()).thenReturn(false);
        when(blockedResult.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(blockedResult.getName()).thenReturn("compare-cancel");
        Eyes stubbedEyes = stubEyes();
        org.mockito.Mockito.doAnswer(inv -> {
            closing.countDown();
            release.await(10, TimeUnit.SECONDS);
            return blockedResult;
        }).when(stubbedEyes).close(org.mockito.ArgumentMatchers.anyBoolean());
        EyesFactory factory = mock(EyesFactory.class);
        when(factory.build()).thenReturn(stubbedEyes);
        return (req, logger) -> {
            Config config = new Config();
            config.logger = logger;
            return new RunConfig(config, factory, 2);
        };
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

    private static File makeTinyPng(File folder, String fileName) throws Exception {
        File png = new File(folder, fileName);
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        try (FileOutputStream out = new FileOutputStream(png)) {
            ImageIO.write(img, "png", out);
        }
        return png;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
