package com.applitools.imagetester.lib;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.TestResultsStatus;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.TestObjects.TestBase;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestExecutorHeartbeatTest {

    @Test
    public void emitsHeartbeatWhenTestRunsPastGracePeriod() throws Exception {
        Config config = new Config();
        config.shouldThrowException = false;
        CountDownLatch heartbeatSeen = new CountDownLatch(1);
        config.logger.addListener(line -> {
            if (line.startsWith("Still running...") && line.contains("lorem_20.pdf")) heartbeatSeen.countDown();
        });

        EyesFactory factory = mock(EyesFactory.class);
        Eyes eyes = mock(Eyes.class);
        when(factory.build()).thenReturn(eyes);
        when(eyes.getBatch()).thenReturn(new BatchInfo("t"));

        CountDownLatch workerEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TestBase t = mock(TestBase.class);
        TestResults r = mock(TestResults.class);
        when(r.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(t.name()).thenReturn("lorem_20.pdf");
        when(t.runSafe(any())).thenAnswer(inv -> { workerEntered.countDown(); release.await(); return r; });

        TestExecutor executor = new TestExecutor(1, factory, config);
        // join()'s first clock read captures the head's start time; every later read sees the
        // 30s grace expired. A settable clock raced here: advancing it before join()'s first
        // read made the start time itself 31s, so no heartbeat ever fired.
        AtomicLong clockReads = new AtomicLong(0);
        executor.setNanoTimeSource(() ->
                clockReads.getAndIncrement() == 0 ? 0 : TimeUnit.SECONDS.toNanos(31));

        executor.enqueue(t, null);
        Thread joiner = new Thread(executor::join, "joiner");
        joiner.setDaemon(true);
        joiner.start();

        assertTrue("worker never started", workerEntered.await(5, TimeUnit.SECONDS));

        boolean sawHeartbeat = heartbeatSeen.await(5, TimeUnit.SECONDS);
        release.countDown();
        joiner.join(5_000);
        assertTrue("expected a heartbeat for the in-flight test", sawHeartbeat);
    }

    @Test
    public void noHeartbeatWhenTestCompletesWithinGrace() throws Exception {
        Config config = new Config();
        config.shouldThrowException = false;
        List<String> log = new CopyOnWriteArrayList<>();
        config.logger.addListener(log::add);

        EyesFactory factory = mock(EyesFactory.class);
        Eyes eyes = mock(Eyes.class);
        when(factory.build()).thenReturn(eyes);
        when(eyes.getBatch()).thenReturn(new BatchInfo("t"));

        TestBase t = mock(TestBase.class);
        TestResults r = mock(TestResults.class);
        when(r.getStatus()).thenReturn(TestResultsStatus.Passed);
        when(t.name()).thenReturn("fast.png");
        when(t.runSafe(any())).thenReturn(r); // returns immediately

        TestExecutor executor = new TestExecutor(1, factory, config);
        AtomicLong fakeNanos = new AtomicLong(0); // never advances past grace
        executor.setNanoTimeSource(fakeNanos::get);

        executor.enqueue(t, null);
        executor.join();

        assertFalse("no heartbeat expected for a sub-grace test",
                log.stream().anyMatch(s -> s.startsWith("Still running...")));
    }
}
