export type MatchLevel = "Strict" | "Layout" | "Content" | "Exact";

export interface TestRow {
  name: string;
  status: "running" | "pass" | "fail";
  durationMs?: number;
  dashboardUrl?: string;
  startedAtMs?: number;
}

export type RunStateSnapshot =
  | { kind: "idle" }
  | { kind: "running"; runId: string; tests: TestRow[] }
  | { kind: "done"; runId: string; tests: TestRow[]; passed: number; failed: number; durationMs: number; outputDir?: string; fileCount?: number };

export type SseEvent =
  | { type: "run-started"; runId: string }
  | { type: "test-started"; name: string }
  | { type: "test-finished"; name: string; status: "pass" | "fail"; durationMs: number; dashboardUrl?: string }
  | { type: "log-line"; text: string }
  | { type: "run-finished"; passed: number; failed: number; durationMs: number }
  | { type: "watermark-cleaned"; outputDir: string; fileCount: number; durationMs: number };
