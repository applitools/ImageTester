package com.applitools.imagetester.lib;

import com.applitools.eyes.exceptions.DiffsFoundException;
import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.TestObjects.IDisposable;
import com.applitools.imagetester.TestObjects.TestBase;

import org.apache.commons.lang3.StringUtils;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class TestExecutor {
    private final Config config_;
    private ExecutorService executorService_;
    private ThreadLocal<Eyes> thEyes_;
    // ConcurrentLinkedQueue: enqueue() runs on the suite thread, cancel() runs on the HTTP thread,
    // join() runs on the run thread. Lock-free FIFO keeps add/poll/iterate safe across all three.
    private final Queue<Future<ExecutorResult>> results_ = new ConcurrentLinkedQueue<>();
    private volatile Consumer<ExecutorResult> completionListener_ = null;
    private volatile Consumer<String> startedListener_ = null;
    private volatile boolean cancelled_ = false;

    public TestExecutor(int threads, EyesFactory eyesFactory, Config conf) {
        this.executorService_ = Executors.newFixedThreadPool(threads);
        this.thEyes_ = ThreadLocal.withInitial(eyesFactory::build);
        this.config_ = conf;
    }

    public void setTestCompletionListener(Consumer<ExecutorResult> listener) {
        this.completionListener_ = listener;
    }

    public void setTestStartedListener(Consumer<String> listener) {
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
        for (Future<ExecutorResult> f : results_) f.cancel(false);
        executorService_.shutdown();
    }

    public boolean isCancelled() { return cancelled_; }

    public void enqueue(TestBase test, BatchInfo overrideBatch) {
        if (cancelled_) return;
        Consumer<String> sl = startedListener_;
        if (sl != null) {
            try { sl.accept(test.name()); } catch (Throwable ignored) { /* never let a listener disrupt enqueue */ }
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

            ExecutorResult er = new ExecutorResult(result, (endTime - startTime));
            Consumer<ExecutorResult> listener = completionListener_;
            if (listener != null) {
                try { listener.accept(er); } catch (Throwable ignored) { /* never let a listener disrupt the worker */ }
            }
            return er;
        });

        results_.add(f);
    }

    public void join() {
        int total = results_.size();
        int curr = 1;
        boolean shouldExit = false;
        while (!results_.isEmpty()) {
            if (cancelled_) return;
            config_.logger.printProgress(curr++, total);
            Future<ExecutorResult> head = results_.poll();
            if (head == null) break;
            ExecutorResult result = null;
            // Poll for this test's result so cancel() can break us out without interrupting the worker.
            // Soft-cancel deliberately does not interrupt — see cancel() for why.
            while (true) {
                if (cancelled_) return;
                try {
                    result = head.get(250, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException e) {
                    continue;
                } catch (CancellationException e) {
                    break;
                } catch (InterruptedException e) {
                    config_.logger.reportException(e);
                    break;
                } catch (ExecutionException e) {
                    config_.logger.reportException(e);
                    shouldExit = true;
                    if (config_.shouldThrowException) {
                        throw new RuntimeException("Eyes has reported a mismatch or test failure. \n" +
                            "This exception is thrown because the '-te' flag was present, \n" +
                            "which instructs ImageTester to throw exceptions if a test fails, or a mismatch is detected");
                    }
                    break;
                }
            }
            if (shouldExit) {
                executorService_.shutdown();
                if (config_.shouldThrowException) System.exit(1);
            }
            if (result != null) {
                config_.logger.reportResult(result);
                if (thEyes_.get().getAccessibilityValidation() != null) {
                    config_.logger.reportResultAccessibility(result);
                }
            }
        }

        executorService_.shutdown();
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
