package com.applitools.imagetester.BatchObjects;

import com.applitools.eyes.BatchInfo;
import com.applitools.imagetester.lib.TestExecutor;

public interface IBatch {
    boolean isEmpty();

    void run(TestExecutor executor);

    void run(TestExecutor executor, BatchInfo overrideBatch);
}
