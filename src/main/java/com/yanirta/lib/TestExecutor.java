package com.yanirta.lib;

import com.yanirta.TestObjects.IDisposable;
import com.yanirta.TestObjects.TestBase;
import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
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
        this.thEyes_ = ThreadLocal.withInitial(() -> eyesFactory.build());
        this.config_ = conf;
    }

    public void enqueue(TestBase test, BatchInfo overrideBatch) {
        Future<ExecutorResult> f = executorService_.submit(() -> {
            long startTime = System.nanoTime();
            Eyes eyes = thEyes_.get();
            //set batch
            setBatch(eyes, overrideBatch, config_);
            TestResults result = test.runSafe(eyes);
            eyes.abortIfNotClosed();

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
            try {
                config_.logger.printProgress(curr++, total);
                ExecutorResult result = results_.remove().get();
                config_.logger.reportResult(result);
            } catch (Exception e) {
                config_.logger.reportException(e);
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
            batchToSet = eyes.getBatch();
        }

        batchToSet.setNotifyOnCompletion(config_.notifyOnComplete);

        //set batch
        eyes.setBatch(batchToSet);

        //set sequence name if necessary
        if (config_.sequenceName != null && !StringUtils.isEmpty(config_.sequenceName))
            eyes.getBatch().setSequenceName(config_.sequenceName);
    }
}
