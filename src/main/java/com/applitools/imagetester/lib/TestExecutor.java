package com.applitools.imagetester.lib;

import com.applitools.eyes.exceptions.DiffsFoundException;
import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.TestObjects.IDisposable;
import com.applitools.imagetester.TestObjects.TestBase;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;

public class TestExecutor {
    private final Config config_;
    private ExecutorService executorService_;
    private ThreadLocal<Eyes> thEyes_;
    private Queue<Future<ExecutorResult>> results_ = new LinkedList<>();

    public TestExecutor(int threads, EyesFactory eyesFactory, Config conf) {
        this.executorService_ = Executors.newFixedThreadPool(threads);
        this.thEyes_ = ThreadLocal.withInitial(eyesFactory::build);
        this.config_ = conf;
    }

    public void enqueue(TestBase test, BatchInfo overrideBatch) {
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

            return new ExecutorResult(result, (endTime - startTime));
        });

        results_.add(f);
    }

    public void join() {
        int total = results_.size();
        int curr = 1;
        while (!results_.isEmpty()) {
            config_.logger.printProgress(curr++, total);
            ExecutorResult result = null;
            try {
                result = results_.remove().get();
            } catch (InterruptedException e) {
                config_.logger.reportException(e);
            } catch (ExecutionException e) {
                config_.logger.reportException(e);
                if (config_.shouldThrowException) {
                    throw new RuntimeException("Eyes has reported a mismatch or test failure. \n" +
                            "This exception is thrown because the '-te' flag was present, \n" +
                            "which instructs ImageTester to throw exceptions if a test fails, or a mismatch is detected");
                }
            }
            config_.logger.reportResult(result);
            if (result != null && thEyes_.get().getAccessibilityValidation() != null)
                config_.logger.reportResultAccessibility(result);
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
