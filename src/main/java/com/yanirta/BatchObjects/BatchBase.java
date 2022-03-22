package com.yanirta.BatchObjects;

import com.yanirta.TestObjects.TestBase;
import com.applitools.eyes.BatchInfo;
import com.yanirta.lib.TestExecutor;

import java.util.ArrayList;
import java.util.List;

public abstract class BatchBase implements IBatch {
    private final List<TestBase> tests_ = new ArrayList<>();
    private final BatchInfo batchInfo_;

    public BatchBase(BatchInfo batchInfo) {
        this.batchInfo_ = batchInfo;
    }

    public void addTests(List<TestBase> test) {
        tests_.addAll(test);
    }

    public void run(TestExecutor executor) {
        run(executor, null);
    }

    public void run(TestExecutor executor, BatchInfo overrideBatch) {
        for (TestBase test : tests_)
            executor.enqueue(test, overrideBatch == null ? batchInfo_ : overrideBatch);
    }

    public boolean isEmpty() {
        return tests_.isEmpty();
    }

    public BatchInfo batchInfo() {
        return this.batchInfo_;
    }
}
