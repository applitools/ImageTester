package com.applitools.imagetester.gui;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.TestResultsStatus;
import com.applitools.imagetester.TestObjects.TestBase;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.ExecutorResult;
import com.applitools.imagetester.lib.TestExecutor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestExecutorListenerTest {

    @Test
    public void listenerFiresOncePerEnqueuedTest() throws Exception {
        Config config = new Config();
        config.shouldThrowException = false;

        EyesFactory factory = mock(EyesFactory.class);
        com.applitools.eyes.images.Eyes eyes = mock(com.applitools.eyes.images.Eyes.class);
        when(factory.build()).thenReturn(eyes);
        when(eyes.getBatch()).thenReturn(new com.applitools.eyes.BatchInfo("t"));

        TestBase t1 = mock(TestBase.class);
        TestBase t2 = mock(TestBase.class);
        TestResults r1 = mock(TestResults.class);
        TestResults r2 = mock(TestResults.class);
        when(r1.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(r2.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(t1.runSafe(any())).thenReturn(r1);
        when(t2.runSafe(any())).thenReturn(r2);

        TestExecutor executor = new TestExecutor(1, factory, config);
        List<ExecutorResult> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        executor.setTestCompletionListener(result -> { received.add(result); latch.countDown(); });

        executor.enqueue(t1, null);
        executor.enqueue(t2, null);
        executor.join();

        assertTrue("listener did not fire for both tests", latch.await(5, TimeUnit.SECONDS));
        assertEquals(2, received.size());
    }

    @Test
    public void startedListenerFiresSynchronouslyBeforeWorkerRuns() throws Exception {
        Config config = new Config();
        config.shouldThrowException = false;

        EyesFactory factory = mock(EyesFactory.class);
        com.applitools.eyes.images.Eyes eyes = mock(com.applitools.eyes.images.Eyes.class);
        when(factory.build()).thenReturn(eyes);
        when(eyes.getBatch()).thenReturn(new com.applitools.eyes.BatchInfo("t"));

        CountDownLatch gate = new CountDownLatch(1);
        TestBase t = mock(TestBase.class);
        TestResults r = mock(TestResults.class);
        when(r.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(t.name()).thenReturn("foo.png");
        // Worker blocks until we release the gate; if started-listener didn't fire pre-submit, the assertion below would fail.
        when(t.runSafe(any())).thenAnswer(inv -> { gate.await(); return r; });

        TestExecutor executor = new TestExecutor(1, factory, config);
        List<String> started = new ArrayList<>();
        executor.setTestStartedListener(started::add);

        executor.enqueue(t, null);
        assertEquals(1, started.size());
        assertEquals("foo.png", started.get(0));

        gate.countDown();
        executor.join();
    }

    @Test
    public void startedListenerThatThrowsDoesNotPreventEnqueue() throws Exception {
        Config config = new Config();
        config.shouldThrowException = false;

        EyesFactory factory = mock(EyesFactory.class);
        com.applitools.eyes.images.Eyes eyes = mock(com.applitools.eyes.images.Eyes.class);
        when(factory.build()).thenReturn(eyes);
        when(eyes.getBatch()).thenReturn(new com.applitools.eyes.BatchInfo("t"));

        TestBase t = mock(TestBase.class);
        TestResults r = mock(TestResults.class);
        when(r.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(t.name()).thenReturn("foo.png");
        when(t.runSafe(any())).thenReturn(r);

        TestExecutor executor = new TestExecutor(1, factory, config);
        executor.setTestStartedListener(name -> { throw new RuntimeException("boom"); });
        executor.enqueue(t, null);
        executor.join();
    }

    @Test
    public void cancelReturnsJoinEvenWhileWorkerIsStillRunning() throws Exception {
        Config config = new Config();
        config.shouldThrowException = false;

        EyesFactory factory = mock(EyesFactory.class);
        com.applitools.eyes.images.Eyes eyes = mock(com.applitools.eyes.images.Eyes.class);
        when(factory.build()).thenReturn(eyes);
        when(eyes.getBatch()).thenReturn(new com.applitools.eyes.BatchInfo("t"));

        CountDownLatch workerEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TestBase t = mock(TestBase.class);
        TestResults r = mock(TestResults.class);
        when(r.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(t.name()).thenReturn("hangs.png");
        // Soft-cancel must NOT interrupt this worker; it should keep running until we release it.
        when(t.runSafe(any())).thenAnswer(inv -> {
            workerEntered.countDown();
            release.await();
            return r;
        });

        TestExecutor executor = new TestExecutor(1, factory, config);
        executor.enqueue(t, null);

        Thread joiner = new Thread(executor::join, "joiner");
        joiner.setDaemon(true);
        joiner.start();

        assertTrue("worker never started", workerEntered.await(5, TimeUnit.SECONDS));
        executor.cancel();
        joiner.join(5_000);
        assertTrue("join did not return after cancel", !joiner.isAlive());
        assertTrue(executor.isCancelled());

        release.countDown(); // let the background worker finish naturally so the test thread doesn't leak it
    }

    @Test
    public void listenerThatThrowsDoesNotKillTheWorker() throws Exception {
        Config config = new Config();
        config.shouldThrowException = false;

        EyesFactory factory = mock(EyesFactory.class);
        com.applitools.eyes.images.Eyes eyes = mock(com.applitools.eyes.images.Eyes.class);
        when(factory.build()).thenReturn(eyes);
        when(eyes.getBatch()).thenReturn(new com.applitools.eyes.BatchInfo("t"));

        TestBase t = mock(TestBase.class);
        TestResults r = mock(TestResults.class);
        when(r.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(t.runSafe(any())).thenReturn(r);

        TestExecutor executor = new TestExecutor(1, factory, config);
        executor.setTestCompletionListener(er -> { throw new RuntimeException("boom"); });
        executor.enqueue(t, null);
        executor.join();
        // If we got here, the throwing listener was caught and the run completed.
    }
}
