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
        public final String previewPath;
        public final String doc2PreviewPath;
        public TestStarted(String name) { this(name, null); }
        public TestStarted(String name, String previewPath) { this(name, previewPath, null); }
        public TestStarted(String name, String previewPath, String doc2PreviewPath) {
            super("test-started");
            this.name = name; this.previewPath = previewPath; this.doc2PreviewPath = doc2PreviewPath;
        }
    }

    public static final class TestFinished extends SseEvent {
        public final String name;
        public final String status;
        public final long durationMs;
        public final String dashboardUrl;
        public final String previewPath;
        public final String doc2PreviewPath;
        public TestFinished(String name, String status, long durationMs, String dashboardUrl) {
            this(name, status, durationMs, dashboardUrl, null);
        }
        public TestFinished(String name, String status, long durationMs, String dashboardUrl, String previewPath) {
            this(name, status, durationMs, dashboardUrl, previewPath, null);
        }
        public TestFinished(String name, String status, long durationMs, String dashboardUrl, String previewPath, String doc2PreviewPath) {
            super("test-finished");
            this.name = name; this.status = status; this.durationMs = durationMs; this.dashboardUrl = dashboardUrl;
            this.previewPath = previewPath; this.doc2PreviewPath = doc2PreviewPath;
        }
    }

    public static final class LogLine extends SseEvent {
        public final String text;
        public LogLine(String text) { super("log-line"); this.text = text; }
    }

    /** Run-level failure (nothing test-specific to attach it to); emitted just before RunFinished. */
    public static final class RunError extends SseEvent {
        public final String text;
        public RunError(String text) { super("run-error"); this.text = text; }
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
