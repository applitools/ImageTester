export type MatchLevel = "Strict" | "Layout" | "Content" | "Exact";

export interface TestRow {
  name: string;
  status: "running" | "pass" | "fail";
  durationMs?: number;
  dashboardUrl?: string;
}

export type RunStateSnapshot =
  | { kind: "idle" }
  | { kind: "running"; runId: string; tests: TestRow[] }
  | { kind: "done"; runId: string; tests: TestRow[]; passed: number; failed: number; durationMs: number };

export type SseEvent =
  | { type: "test-started"; name: string }
  | { type: "test-finished"; name: string; status: "pass" | "fail"; durationMs: number; dashboardUrl?: string }
  | { type: "log-line"; text: string }
  | { type: "run-finished"; passed: number; failed: number; durationMs: number };
