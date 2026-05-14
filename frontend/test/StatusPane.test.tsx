import { render, screen, fireEvent } from "@testing-library/react";
import { StatusPane } from "../src/components/StatusPane";
import type { RunStateSnapshot } from "../src/types";

describe("StatusPane", () => {
  it("shows idle placeholder", () => {
    render(<StatusPane state={{ kind: "idle" }} logLines={[]} />);
    expect(screen.getByText(/pick a source/i)).toBeInTheDocument();
  });

  it("renders running tests", () => {
    const s: RunStateSnapshot = { kind: "running", runId: "r", tests: [{ name: "a.png", status: "pass", durationMs: 42 }, { name: "b.pdf", status: "running" }] };
    render(<StatusPane state={s} logLines={[]} />);
    expect(screen.getByText("a.png")).toBeInTheDocument();
    expect(screen.getByText("b.pdf")).toBeInTheDocument();
  });

  it("shows aggregate counts when done", () => {
    const s: RunStateSnapshot = { kind: "done", runId: "r", tests: [], passed: 3, failed: 1, durationMs: 1234 };
    render(<StatusPane state={s} logLines={[]} />);
    expect(screen.getByText(/3 passed/i)).toBeInTheDocument();
    expect(screen.getByText(/1 failed/i)).toBeInTheDocument();
  });

  it("hides log by default and shows it when toggled", () => {
    render(<StatusPane state={{ kind: "idle" }} logLines={["[INFO] hello"]} />);
    expect(screen.queryByText("[INFO] hello")).not.toBeInTheDocument();
    fireEvent.click(screen.getByText(/show log/i));
    expect(screen.getByText("[INFO] hello")).toBeInTheDocument();
  });
});
