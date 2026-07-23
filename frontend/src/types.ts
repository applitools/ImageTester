export type MatchLevel = "Strict" | "Layout" | "Content" | "Exact";

export interface TestRow {
  name: string;
  status: "running" | "pass" | "fail";
  durationMs?: number;
  dashboardUrl?: string;
  startedAtMs?: number;
  previewPath?: string;
  doc2PreviewPath?: string;
}

export type RunStateSnapshot =
  | { kind: "idle" }
  | { kind: "running"; runId: string; tests: TestRow[] }
  | { kind: "done"; runId: string; tests: TestRow[]; passed: number; failed: number; durationMs: number; outputDir?: string; fileCount?: number };

export type SseEvent =
  | { type: "run-started"; runId: string }
  | { type: "test-started"; name: string; previewPath?: string; doc2PreviewPath?: string }
  | { type: "test-finished"; name: string; status: "pass" | "fail"; durationMs: number; dashboardUrl?: string; previewPath?: string; doc2PreviewPath?: string }
  | { type: "log-line"; text: string }
  | { type: "run-finished"; passed: number; failed: number; durationMs: number }
  | { type: "watermark-cleaned"; outputDir: string; fileCount: number; durationMs: number };

export type UpdateStatus = {
  available: boolean;
  version?: string;
  releasePageUrl?: string;
  canOneClick: boolean;
  state: "idle" | "downloading" | "launched" | "error";
  error?: string;
};

export interface PrecheckFinding {
  severity: "ERROR" | "WARNING" | "INFO";
  code: string;
  message: string;
}
