package com.applitools.imagetester.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class RunState {

    private RunState() {}

    public static final class Idle extends RunState {
        public static final Idle INSTANCE = new Idle();
        private Idle() {}
    }

    public static final class Running extends RunState {
        public final String runId;
        public final long startedAtMillis;
        public final List<TestRow> tests;
        public Running() {
            this.runId = UUID.randomUUID().toString();
            this.startedAtMillis = System.currentTimeMillis();
            this.tests = new ArrayList<>();
        }
    }

    public static final class Done extends RunState {
        public final String runId;
        public final List<TestRow> tests;
        public final int passed;
        public final int failed;
        public final long durationMs;
        public final String outputDir;
        /** Run-level failure shown in the Tests pane; null when the run produced normal rows. */
        public final String errorMessage;
        public Done(String runId, List<TestRow> tests, int passed, int failed, long durationMs) {
            this(runId, tests, passed, failed, durationMs, null, null);
        }
        public Done(String runId, List<TestRow> tests, int passed, int failed, long durationMs, String outputDir) {
            this(runId, tests, passed, failed, durationMs, outputDir, null);
        }
        public Done(String runId, List<TestRow> tests, int passed, int failed, long durationMs, String outputDir, String errorMessage) {
            this.runId = runId;
            this.tests = tests;
            this.passed = passed;
            this.failed = failed;
            this.durationMs = durationMs;
            this.outputDir = outputDir;
            this.errorMessage = errorMessage;
        }
    }

    public static final class TestRow {
        public final String name;
        public String status;  // "running" | "passed" | "mismatch" | "failed" | "new" | "aborted" | "error" | "cancelled"
        public Long durationMs;
        public String dashboardUrl;
        public String previewPath;
        public String doc2PreviewPath;
        public TestRow(String name) { this.name = name; this.status = "running"; }
    }
}
