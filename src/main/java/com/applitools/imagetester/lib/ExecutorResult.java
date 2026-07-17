package com.applitools.imagetester.lib;

import com.applitools.eyes.TestResults;

public class ExecutorResult {
    public TestResults testResult;
    public long runTimeNs;
    public String previewPath;

    public ExecutorResult(TestResults result, long runtimeNS) {
        this(result, runtimeNS, null);
    }

    public ExecutorResult(TestResults result, long runtimeNS, String previewPath) {
        this.testResult = result;
        this.runTimeNs = runtimeNS;
        this.previewPath = previewPath;
    }
}
