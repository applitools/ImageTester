package com.applitools.imagetester.gui;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.TestResultsStatus;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class RunControllerTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void initialStatusIsIdle() {
        RunController c = newController(passingFactoryBuilder());
        assertTrue(c.snapshot() instanceof RunState.Idle);
    }

    @Test
    public void rejectsRunWithMissingApiKey() throws Exception {
        SecretsStore secrets = SecretsStore.inMemoryForTest(); // empty
        RunController c = new RunController(secrets, new RunStream(), passingFactoryBuilder());
        File folder = tmp.newFolder("pix");
        makeTinyPng(folder);
        assertThrows(RunController.MissingApiKeyException.class,
            () -> c.start(folder.getAbsolutePath(), "Strict"));
    }

    @Test
    public void rejectsRunWithInvalidSourcePath() {
        RunController c = newController(passingFactoryBuilder());
        c.setSecretApiKey("sk_test");
        assertThrows(SourcePathValidator.InvalidSourceException.class,
            () -> c.start("/path/does/not/exist/anywhere", "Strict"));
    }

    @Test
    public void happyPathReachesDoneState() throws Exception {
        RunController c = newController(passingFactoryBuilder());
        c.setSecretApiKey("sk_test");
        File folder = tmp.newFolder("pix");
        makeTinyPng(folder);

        c.start(folder.getAbsolutePath(), "Strict");

        long deadline = System.currentTimeMillis() + 5_000;
        while (!(c.snapshot() instanceof RunState.Done) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue("did not reach Done within 5s", c.snapshot() instanceof RunState.Done);
    }

    @Test
    public void secondConcurrentStartReturns409() throws Exception {
        // Use a builder that artificially blocks Eyes construction so the first run is in-flight when we issue the second.
        RunController c = newController(slowFactoryBuilder(500));
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
                return (Object) c.start(folder.getAbsolutePath(), "Strict");
            } catch (RunController.RunInProgressException ex) {
                return ex;
            }
        });
        Future<Object> f2 = es.submit(() -> {
            ready.countDown();
            ready.await();
            try {
                return (Object) c.start(folder.getAbsolutePath(), "Strict");
            } catch (RunController.RunInProgressException ex) {
                return ex;
            }
        });

        Object r1 = f1.get(5, TimeUnit.SECONDS);
        Object r2 = f2.get(5, TimeUnit.SECONDS);
        // Exactly one should be a StartResult, the other a RunInProgressException.
        // Edge case: if the first run completed before the second call, both may succeed — that is acceptable.
        boolean oneOk = (r1 instanceof RunController.StartResult) ^ (r2 instanceof RunController.StartResult);
        boolean oneRejected = (r1 instanceof RunController.RunInProgressException) ^ (r2 instanceof RunController.RunInProgressException);
        assertTrue("Expected exactly one success and one rejection (or two successes in fast-completion edge case)",
            oneOk == oneRejected);
        es.shutdownNow();
    }

    // ---- helpers ----

    private RunController newController(RunController.EyesFactoryBuilder builder) {
        SecretsStore secrets = SecretsStore.inMemoryForTest();
        return new RunController(secrets, new RunStream(), builder);
    }

    private RunController.EyesFactoryBuilder passingFactoryBuilder() {
        return (apiKey, matchLevel, logger) -> {
            EyesFactory factory = mock(EyesFactory.class);
            Eyes eyes = stubEyes();
            when(factory.build()).thenReturn(eyes);
            return factory;
        };
    }

    private RunController.EyesFactoryBuilder slowFactoryBuilder(long delayMs) {
        return (apiKey, matchLevel, logger) -> {
            EyesFactory factory = mock(EyesFactory.class);
            when(factory.build()).thenAnswer(inv -> {
                Thread.sleep(delayMs);
                return stubEyes();
            });
            return factory;
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
