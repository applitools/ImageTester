import { describe, expect, it } from "vitest";
import { reducer } from "./App";
import type { RunStateSnapshot } from "./types";

describe("reducer run-error handling", () => {
  const running: RunStateSnapshot = { kind: "running", runId: "r1", tests: [] };

  it("stores the error message while the run is live", () => {
    const next = reducer(running, { type: "sse", event: { type: "run-error", text: "boom" } });
    expect(next.kind === "running" && next.errorMessage).toBe("boom");
  });

  it("carries the error message into the done state", () => {
    const withError = reducer(running, { type: "sse", event: { type: "run-error", text: "boom" } });
    const done = reducer(withError, { type: "sse", event: { type: "run-finished", passed: 0, failed: 0, durationMs: 5 } });
    expect(done.kind === "done" && done.errorMessage).toBe("boom");
  });

  it("starts the next run without the previous error", () => {
    const withError = reducer(running, { type: "sse", event: { type: "run-error", text: "boom" } });
    const next = reducer(withError, { type: "sse", event: { type: "run-started", runId: "r2" } });
    expect(next.kind === "running" && next.errorMessage).toBeUndefined();
  });
});
