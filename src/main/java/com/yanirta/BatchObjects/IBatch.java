package com.yanirta.BatchObjects;

import com.applitools.eyes.BatchInfo;
import com.yanirta.lib.TestExecutor;

public interface IBatch {
    boolean isEmpty();

    void run(TestExecutor executor);

    void run(TestExecutor executor, BatchInfo overrideBatch);
}
