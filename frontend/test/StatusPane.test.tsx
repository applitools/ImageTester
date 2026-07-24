import { render, screen, fireEvent } from "@testing-library/react";
import { StatusPane } from "../src/components/StatusPane";
import type { RunStateSnapshot } from "../src/types";

describe("StatusPane", () => {
  it("shows idle placeholder", () => {
    render(<StatusPane state={{ kind: "idle" }} logLines={[]} />);
    expect(screen.getByText(/pick a source/i)).toBeInTheDocument();
  });

  it("renders running tests on the Tests tab", () => {
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

  it("shows cleaned-files summary for a watermark-out run", () => {
    render(<StatusPane state={{ kind: "done", runId: "r", tests: [], passed: 0, failed: 0, durationMs: 10, outputDir: "/out", fileCount: 4 }} logLines={[]} />);
    expect(screen.getByText(/Cleaned 4/)).toBeInTheDocument();
    expect(screen.getByText("/out")).toBeInTheDocument();
    expect(screen.queryByText(/0 passed/i)).not.toBeInTheDocument();
  });

  it("does not render log content while the Tests tab is active", () => {
    render(<StatusPane state={{ kind: "idle" }} logLines={["[INFO] hello"]} />);
    expect(screen.queryByText("[INFO] hello")).not.toBeInTheDocument();
  });
});

describe("StatusPane tabs", () => {
  it("renders a tablist with a Tests tab and a Log tab", () => {
    render(<StatusPane state={{ kind: "idle" }} logLines={[]} />);
    expect(screen.getAllByRole("tab").map((t) => t.textContent)).toEqual(["Tests", "Log"]);
  });

  it("marks the Tests tab as selected by default", () => {
    render(<StatusPane state={{ kind: "idle" }} logLines={[]} />);
    expect(screen.getByRole("tab", { name: "Tests" })).toHaveAttribute("aria-selected", "true");
  });

  it("marks the Log tab as unselected by default", () => {
    render(<StatusPane state={{ kind: "idle" }} logLines={[]} />);
    expect(screen.getByRole("tab", { name: "Log" })).toHaveAttribute("aria-selected", "false");
  });

  it("flips aria-selected to the Log tab when it is clicked", () => {
    render(<StatusPane state={{ kind: "idle" }} logLines={[]} />);
    fireEvent.click(screen.getByRole("tab", { name: "Log" }));
    expect(screen.getByRole("tab", { name: "Log" })).toHaveAttribute("aria-selected", "true");
  });

  it("shows the log content when the Log tab is active", () => {
    render(<StatusPane state={{ kind: "idle" }} logLines={["[INFO] hello"]} />);
    fireEvent.click(screen.getByRole("tab", { name: "Log" }));
    expect(screen.getByText("[INFO] hello")).toBeInTheDocument();
  });

  it("hides the Tests panel content when the Log tab is active", () => {
    const s: RunStateSnapshot = { kind: "running", runId: "r", tests: [{ name: "a.png", status: "pass", durationMs: 42 }] };
    render(<StatusPane state={s} logLines={[]} />);
    fireEvent.click(screen.getByRole("tab", { name: "Log" }));
    expect(screen.queryByText("a.png")).not.toBeInTheDocument();
  });

  it("renders exactly one scroll region on the Tests tab", () => {
    const { container } = render(<StatusPane state={{ kind: "idle" }} logLines={["[INFO] hello"]} />);
    expect(container.querySelectorAll(".overflow-y-auto")).toHaveLength(1);
  });

  it("renders exactly one scroll region on the Log tab", () => {
    const { container } = render(<StatusPane state={{ kind: "idle" }} logLines={["[INFO] hello"]} />);
    fireEvent.click(screen.getByRole("tab", { name: "Log" }));
    expect(container.querySelectorAll(".overflow-y-auto")).toHaveLength(1);
  });
});

describe("StatusPane scrolling", () => {
  function runningWith(n: number): RunStateSnapshot {
    return {
      kind: "running",
      runId: "r1",
      tests: Array.from({ length: n }, (_, i) => ({ name: `doc-${i}.pdf`, status: "running" as const })),
    } as RunStateSnapshot;
  }

  function primeScroll(container: HTMLElement, { scrollTop, scrollHeight, clientHeight }: { scrollTop: number; scrollHeight: number; clientHeight: number }) {
    Object.defineProperty(container, "scrollHeight", { configurable: true, value: scrollHeight });
    Object.defineProperty(container, "clientHeight", { configurable: true, value: clientHeight });
    container.scrollTop = scrollTop;
    fireEvent.scroll(container);
  }

  it("renders the test rows inside a scroll panel capped on mobile", () => {
    render(<StatusPane state={runningWith(3)} logLines={[]} />);
    const row = screen.getByText("doc-0.pdf");
    const container = row.closest(".overflow-y-auto");
    expect(container).toHaveClass("max-h-[60vh]", "md:max-h-none", "flex-1");
  });

  it("still renders every row inside the scroll container", () => {
    render(<StatusPane state={runningWith(40)} logLines={[]} />);
    expect(screen.getAllByText(/doc-\d+\.pdf/)).toHaveLength(40);
  });

  it("follows new rows when already near the bottom", () => {
    const { rerender } = render(<StatusPane state={runningWith(10)} logLines={[]} />);
    const container = screen.getByText("doc-0.pdf").closest(".overflow-y-auto") as HTMLElement;
    primeScroll(container, { scrollTop: 560, scrollHeight: 600, clientHeight: 60 });
    rerender(<StatusPane state={runningWith(11)} logLines={[]} />);
    expect(container.scrollTop).toBe(container.scrollHeight);
  });

  it("does not yank the view down when scrolled up to inspect an earlier test", () => {
    const { rerender } = render(<StatusPane state={runningWith(10)} logLines={[]} />);
    const container = screen.getByText("doc-0.pdf").closest(".overflow-y-auto") as HTMLElement;
    primeScroll(container, { scrollTop: 0, scrollHeight: 600, clientHeight: 60 });
    rerender(<StatusPane state={runningWith(11)} logLines={[]} />);
    expect(container.scrollTop).toBe(0);
  });

  it("re-arms auto-follow when a new run starts after scrolling up", () => {
    const { rerender } = render(<StatusPane state={runningWith(10)} logLines={[]} />);
    const container = screen.getByText("doc-0.pdf").closest(".overflow-y-auto") as HTMLElement;
    primeScroll(container, { scrollTop: 0, scrollHeight: 600, clientHeight: 60 });
    rerender(<StatusPane state={{ ...runningWith(5), runId: "r2" } as RunStateSnapshot} logLines={[]} />);
    expect(container.scrollTop).toBe(container.scrollHeight);
  });

  it("makes the scroll region keyboard-focusable", () => {
    render(<StatusPane state={runningWith(3)} logLines={[]} />);
    const container = screen.getByText("doc-0.pdf").closest(".overflow-y-auto") as HTMLElement;
    expect(container).toHaveAttribute("tabindex", "0");
  });
});

describe("StatusPane log scrolling", () => {
  function runningWith(n: number): RunStateSnapshot {
    return {
      kind: "running",
      runId: "r1",
      tests: Array.from({ length: n }, (_, i) => ({ name: `doc-${i}.pdf`, status: "running" as const })),
    } as RunStateSnapshot;
  }

  function primeScroll(container: HTMLElement, { scrollTop, scrollHeight, clientHeight }: { scrollTop: number; scrollHeight: number; clientHeight: number }) {
    Object.defineProperty(container, "scrollHeight", { configurable: true, value: scrollHeight });
    Object.defineProperty(container, "clientHeight", { configurable: true, value: clientHeight });
    container.scrollTop = scrollTop;
    fireEvent.scroll(container);
  }

  it("follows new log lines when already near the bottom", () => {
    const { rerender } = render(<StatusPane state={runningWith(10)} logLines={["[INFO] one"]} />);
    fireEvent.click(screen.getByRole("tab", { name: "Log" }));
    const container = screen.getByText("[INFO] one").closest(".overflow-y-auto") as HTMLElement;
    primeScroll(container, { scrollTop: 560, scrollHeight: 600, clientHeight: 60 });
    rerender(<StatusPane state={runningWith(10)} logLines={["[INFO] one", "[INFO] two"]} />);
    expect(container.scrollTop).toBe(container.scrollHeight);
  });
});
