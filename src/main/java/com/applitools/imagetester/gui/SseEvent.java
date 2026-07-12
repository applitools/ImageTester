package com.applitools.imagetester.gui;

public abstract class SseEvent {

    public final String type;
    protected SseEvent(String type) { this.type = type; }

    public static final class RunStarted extends SseEvent {
        public final String runId;
        public RunStarted(String runId) { super("run-started"); this.runId = runId; }
    }

    public static final class TestStarted extends SseEvent {
        public final String name;
        public TestStarted(String name) { super("test-started"); this.name = name; }
    }

    public static final class TestFinished extends SseEvent {
        public final String name;
        public final String status;
        public final long durationMs;
        public final String dashboardUrl;
        public TestFinished(String name, String status, long durationMs, String dashboardUrl) {
            super("test-finished");
            this.name = name; this.status = status; this.durationMs = durationMs; this.dashboardUrl = dashboardUrl;
        }
    }

    public static final class LogLine extends SseEvent {
        public final String text;
        public LogLine(String text) { super("log-line"); this.text = text; }
    }

    public static final class RunFinished extends SseEvent {
        public final int passed;
        public final int failed;
        public final long durationMs;
        public RunFinished(int passed, int failed, long durationMs) {
            super("run-finished"); this.passed = passed; this.failed = failed; this.durationMs = durationMs;
        }
    }

    public static final class WatermarkCleaned extends SseEvent {
        public final String outputDir;
        public final int fileCount;
        public final long durationMs;
        public WatermarkCleaned(String outputDir, int fileCount, long durationMs) {
            super("watermark-cleaned");
            this.outputDir = outputDir; this.fileCount = fileCount; this.durationMs = durationMs;
        }
    }
}
