// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";

import { StatusPane } from "./StatusPane";

describe("StatusPane run-level errors", () => {
  it("shows the error banner in the Tests tab when the run is done", () => {
    render(<StatusPane
      state={{ kind: "done", runId: "r1", tests: [], passed: 0, failed: 0, durationMs: 10, errorMessage: "The run failed before any tests could complete. See the Log tab for details." }}
      logLines={[]}
    />);
    expect(screen.getByRole("alert").textContent).toContain("See the Log tab");
  });

  it("shows the error banner while the run is still live", () => {
    render(<StatusPane
      state={{ kind: "running", runId: "r1", tests: [], errorMessage: "boom" }}
      logLines={[]}
    />);
    expect(screen.getByRole("alert").textContent).toContain("boom");
  });
});
