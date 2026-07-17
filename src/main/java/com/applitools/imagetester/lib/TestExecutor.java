package com.applitools.imagetester.lib;

import com.applitools.eyes.exceptions.DiffsFoundException;
import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.TestObjects.IDisposable;
import com.applitools.imagetester.TestObjects.TestBase;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public class TestExecutor {
    private static final long SHUTDOWN_TIMEOUT_MINUTES = 5;

    private final Config config_;
    private ExecutorService executorService_;
    private ThreadLocal<Eyes> thEyes_;
    // ConcurrentLinkedQueue: enqueue() runs on the suite thread, cancel() runs on the HTTP thread,
    // join() runs on the run thread. Lock-free FIFO keeps add/poll/iterate safe across all three.
    private final Queue<Pending> results_ = new ConcurrentLinkedQueue<>();

    private static final long HEARTBEAT_GRACE_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long HEARTBEAT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private volatile LongSupplier nanoTimeSource_ = System::nanoTime;

    private static final class Pending {
        final String name;
        final Future<ExecutorResult> future;
        Pending(String name, Future<ExecutorResult> future) { this.name = name; this.future = future; }
    }

    /** name plus the source file to render as a GUI status-row thumbnail (may be null). */
    public static final class StartedInfo {
        public final String name;
        public final String previewPath;
        public StartedInfo(String name, String previewPath) { this.name = name; this.previewPath = previewPath; }
    }

    void setNanoTimeSource(LongSupplier source) { this.nanoTimeSource_ = source; }
    private final boolean hasAccessibilityValidation_;
    private volatile Consumer<ExecutorResult> completionListener_ = null;
    private volatile Consumer<StartedInfo> startedListener_ = null;
    private volatile boolean cancelled_ = false;

    public TestExecutor(int threads, EyesFactory eyesFactory, Config conf) {
        this.executorService_ = Executors.newFixedThreadPool(threads);
        this.thEyes_ = ThreadLocal.withInitial(eyesFactory::build);
        this.config_ = conf;
        this.hasAccessibilityValidation_ = eyesFactory.hasAccessibilityValidation();
    }

    public void setTestCompletionListener(Consumer<ExecutorResult> listener) {
        this.completionListener_ = listener;
    }

    public void setTestStartedListener(Consumer<StartedInfo> listener) {
        this.startedListener_ = listener;
    }

    /**
     * Soft-cancel: stops accepting new work, cancels not-yet-started futures, and frees join()
     * to return immediately. Running workers are NOT interrupted — interrupting an Eyes-SDK call
     * mid-flight corrupts the shared universal-core session and the *next* run gets
     * "For input string: 'null'" when the core returns garbage for parsed fields. We let in-flight
     * work finish in the background; the listeners are nulled so it can't leak ghost SSE events.
     */
    public void cancel() {
        cancelled_ = true;
        startedListener_ = null;
        completionListener_ = null;
        // mayInterruptIfRunning=false: only pending tasks are cancelled; running ones complete.
        for (Pending p : results_) p.future.cancel(false);
        executorService_.shutdown();
    }

    public boolean isCancelled() { return cancelled_; }

    public void enqueue(TestBase test, BatchInfo overrideBatch) {
        if (cancelled_) return;
        final String name = test.name();
        final File previewFile = test.previewFile();
        final String previewPath = previewFile != null ? previewFile.getAbsolutePath() : null;
        Consumer<StartedInfo> sl = startedListener_;
        if (sl != null) {
            try { sl.accept(new StartedInfo(name, previewPath)); } catch (Throwable ignored) { /* never let a listener disrupt enqueue */ }
        }
        Future<ExecutorResult> f = executorService_.submit(() -> {
            long startTime = System.nanoTime();
            Eyes eyes = thEyes_.get();
            //set batch
            setBatch(eyes, overrideBatch, config_);
            setTimeout(eyes, config_);
            TestResults result = test.runSafe(eyes);
            eyes.abortIfNotClosed();

            if (config_.shouldThrowException && result.isDifferent()) {
                throw new DiffsFoundException(result, result.getId(), result.getName());
            }

            //add batch to close
            config_.addBatchIdToCloseList(eyes.getBatch().getId());
            // Clear batch
            eyes.setBatch(null);
            if (test instanceof IDisposable)
                ((IDisposable) test).dispose();
            long endTime = System.nanoTime();

            ExecutorResult er = new ExecutorResult(result, (endTime - startTime), previewPath);
            Consumer<ExecutorResult> listener = completionListener_;
            if (listener != null) {
                try { listener.accept(er); } catch (Throwable ignored) { /* never let a listener disrupt the worker */ }
            }
            return er;
        });

        results_.add(new Pending(name, f));
    }

    public void join() {
        int total = results_.size();
        int curr = 1;
        RuntimeException pendingThrow = null;

        while (!results_.isEmpty()) {
            if (cancelled_) return;
            config_.logger.printProgress(curr++, total);
            Pending head = results_.poll();
            if (head == null) break;
            long headStartNanos = nanoTimeSource_.getAsLong();
            long lastBeatNanos = headStartNanos;
            ExecutorResult result = null;
            while (true) {
                if (cancelled_) return;
                try {
                    result = head.future.get(250, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException e) {
                    long now = nanoTimeSource_.getAsLong();
                    if (now - headStartNanos >= HEARTBEAT_GRACE_NANOS
                            && now - lastBeatNanos >= HEARTBEAT_INTERVAL_NANOS) {
                        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(now - headStartNanos);
                        config_.logger.printHeartbeat(head.name, elapsedSeconds);
                        lastBeatNanos = now;
                    }
                    continue;
                } catch (CancellationException e) {
                    break;
                } catch (InterruptedException e) {
                    config_.logger.reportException(e);
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException e) {
                    config_.logger.reportException(e);
                    if (config_.shouldThrowException) {
                        // Defer throw until in-flight workers drain. Interrupting them mid-RPC
                        // corrupts the shared universal-core session and breaks subsequent runs.
                        pendingThrow = new RuntimeException("Eyes has reported a mismatch or test failure. \n" +
                            "This exception is thrown because the '-te' flag was present, \n" +
                            "which instructs ImageTester to throw exceptions if a test fails, or a mismatch is detected");
                    }
                    break;
                }
            }
            if (pendingThrow != null) break;
            if (result != null) {
                config_.logger.reportResult(result);
                if (hasAccessibilityValidation_) {
                    config_.logger.reportResultAccessibility(result);
                }
            }
        }

        // Cancel only pending (not-yet-started) tasks; let running workers finish their RPCs.
        for (Pending p : results_) p.future.cancel(false);
        results_.clear();

        executorService_.shutdown();
        try {
            executorService_.awaitTermination(SHUTDOWN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (pendingThrow != null) throw pendingThrow;
    }

    //set eyes correct batch
    public void setBatch(Eyes eyes, BatchInfo overrideBatch, Config config) {
        BatchInfo batchToSet;
        if (config.flatBatch != null) {
            batchToSet = config.flatBatch;
        } else if (overrideBatch != null) {
            batchToSet = overrideBatch;
        } else {
            batchToSet = new BatchInfo();
        }

        batchToSet.setNotifyOnCompletion(config_.notifyOnComplete);

        //set batch
        eyes.setBatch(batchToSet);

        //set sequence name if necessary
        if (config_.sequenceName != null && !StringUtils.isEmpty(config_.sequenceName))
            eyes.getBatch().setSequenceName(config_.sequenceName);
    }

    //set eyes correct batch
    public void setTimeout(Eyes eyes, Config config) {
        if (config.getMatchTimeout() != null) {
            int matchTimeoutValue = Integer.parseInt(config.getMatchTimeout());
            if (matchTimeoutValue >= 500) eyes.setMatchTimeout(matchTimeoutValue);
        }
    }
}
