package com.applitools.imagetester.lib;

import com.applitools.eyes.TestResults;

public class ExecutorResult {
    public TestResults testResult;
    public long runTimeNs;

    public ExecutorResult(TestResults result, long runtimeNS) {
        this.testResult = result;
        this.runTimeNs = runtimeNS;
    }
}
