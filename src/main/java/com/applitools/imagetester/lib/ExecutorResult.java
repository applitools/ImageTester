package com.applitools.imagetester.lib;

import com.applitools.eyes.TestResults;

public class ExecutorResult {
    public TestResults testResult;
    public long runTimeNs;
    public String previewPath;
    // Known even when testResult is null (cancelled/aborted tests have no TestResults to ask).
    public String name;

    public ExecutorResult(TestResults result, long runtimeNS) {
        this(result, runtimeNS, null, null);
    }

    public ExecutorResult(TestResults result, long runtimeNS, String previewPath) {
        this(result, runtimeNS, previewPath, null);
    }

    public ExecutorResult(TestResults result, long runtimeNS, String previewPath, String name) {
        this.testResult = result;
        this.runTimeNs = runtimeNS;
        this.previewPath = previewPath;
        this.name = name;
    }
}
