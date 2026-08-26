// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";

import { PrecheckPanel } from "./PrecheckPanel";
import type { PrecheckFinding } from "../types";

const ERROR: PrecheckFinding = {
  severity: "ERROR",
  code: "ENCRYPTED_DOC",
  message: "Doc 2 (contract-v2.pdf) is password-protected. Set PDF password in Options.",
};
const WARNING: PrecheckFinding = {
  severity: "WARNING",
  code: "PAGE_COUNT_MISMATCH",
  message: "Doc 1 has 12 pages but Doc 2 has 14 pages.",
};
const INFO: PrecheckFinding = {
  severity: "INFO",
  code: "IDENTICAL_CONTENT",
  message: "Doc 1 and Doc 2 have identical content.",
};
const DIMENSION: PrecheckFinding = {
  severity: "WARNING",
  code: "dimension-mismatch",
  message: "Page dimensions differ on 2 page(s) (pages 1, 3): page 1 renders 2065x2923 px vs 2125x2750 px at 250 DPI. Eyes resolves baselines by viewport, so mismatched pages won't be compared.",
  data: { doc1SizePx: "2065x2923", doc2SizePx: "2125x2750", pages: "1, 3" },
};
const PAGE_COUNT: PrecheckFinding = {
  severity: "WARNING",
  code: "page-count-mismatch",
  message: "Doc 1 has 3 page(s) but Doc 2 has 4 page(s). The extra page(s) will create new baselines instead of comparisons.",
  data: { doc1Pages: "3", doc2Pages: "4" },
};

const noop = () => {};

function dismiss() {
  fireEvent.click(screen.getByRole("button", { name: "Close" }));
}

describe("PrecheckPanel visibility", () => {
  it("renders nothing when there are no findings", () => {
    const { container } = render(<PrecheckPanel findings={[]} onOpenOptions={noop} />);
    expect(container.firstChild).toBeNull();
  });

  it("auto-opens the dialog when findings first appear", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    expect(screen.getByRole("dialog")).toBeTruthy();
  });

  it("collapses to a review pill when dismissed", () => {
    render(<PrecheckPanel findings={[WARNING, INFO]} onOpenOptions={noop} />);
    dismiss();
    expect(screen.getByRole("button", { name: "2 precheck findings — Review" })).toBeTruthy();
  });

  it("uses a singular pill label for one finding", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    dismiss();
    expect(screen.getByRole("button", { name: "1 precheck finding — Review" })).toBeTruthy();
  });

  it("reopens the dialog from the pill", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    dismiss();
    fireEvent.click(screen.getByRole("button", { name: "1 precheck finding — Review" }));
    expect(screen.getByRole("dialog")).toBeTruthy();
  });

  it("does not re-open when the same findings reappear", () => {
    const { rerender } = render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    dismiss();
    rerender(<PrecheckPanel findings={[{ ...WARNING, message: "reworded after options edit" }]} onOpenOptions={noop} />);
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("re-opens when a new finding appears", () => {
    const { rerender } = render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    dismiss();
    rerender(<PrecheckPanel findings={[WARNING, ERROR]} onOpenOptions={noop} />);
    expect(screen.getByRole("dialog")).toBeTruthy();
  });

  it("re-arms auto-open when resetKey changes", () => {
    const { rerender } = render(
      <PrecheckPanel findings={[WARNING]} onOpenOptions={noop} resetKey="a.pdf|b.pdf" />,
    );
    dismiss();
    rerender(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} resetKey="a.pdf|c.pdf" />);
    expect(screen.getByRole("dialog")).toBeTruthy();
  });
});

describe("PrecheckPanel dialog content", () => {
  it("counts the findings in the headline", () => {
    render(<PrecheckPanel findings={[WARNING, INFO]} onOpenOptions={noop} />);
    expect(screen.getByRole("dialog").textContent).toContain("2 issues to review before running");
  });

  it("uses a singular headline for one finding", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    expect(screen.getByRole("dialog").textContent).toContain("1 issue to review before running");
  });

  it("marks error findings as alerts", () => {
    render(<PrecheckPanel findings={[ERROR]} onOpenOptions={noop} />);
    expect(screen.getByRole("alert").textContent).toContain("password-protected");
  });

  it("marks non-error findings as status", () => {
    render(<PrecheckPanel findings={[INFO]} onOpenOptions={noop} />);
    expect(screen.getByRole("status").textContent).toContain("identical content");
  });

  it("marks warning findings as status", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    expect(screen.getByRole("status").textContent).toContain("12 pages");
  });

  it("renders findings in the order they arrive", () => {
    render(<PrecheckPanel findings={[ERROR, WARNING]} onOpenOptions={noop} />);
    const text = screen.getByRole("dialog").textContent ?? "";
    expect(text.indexOf("password-protected")).toBeLessThan(text.indexOf("12 pages"));
  });
});

describe("PrecheckPanel severity behavior", () => {
  it("notes that an error blocks the run", () => {
    render(<PrecheckPanel findings={[ERROR, WARNING]} onOpenOptions={noop} />);
    expect(screen.getByRole("dialog").textContent).toContain("This blocks the run until it is fixed.");
  });

  it("offers Close instead of Run anyway when a finding blocks the run", () => {
    render(<PrecheckPanel findings={[ERROR]} onOpenOptions={noop} />);
    expect(screen.queryByRole("button", { name: "Run anyway" })).toBeNull();
  });

  it("offers Run anyway for warnings", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    expect(screen.getByRole("button", { name: "Run anyway" })).toBeTruthy();
  });

  it("notes that warnings allow running anyway", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    expect(screen.getByRole("dialog").textContent).toContain("You can run anyway — results may be incomplete.");
  });

  it("closes the dialog on Run anyway without starting a run", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    fireEvent.click(screen.getByRole("button", { name: "Run anyway" }));
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});

describe("PrecheckPanel dimension-mismatch fixes", () => {
  it("offers a Match size fix for each doc", () => {
    render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} />);
    expect(screen.getByRole("button", { name: /Match Doc 1 size/ })).toBeTruthy();
    expect(screen.getByRole("button", { name: /Match Doc 2 size/ })).toBeTruthy();
  });

  it("sets Match size to Doc 1's rendered size", () => {
    const onSetMatchSize = vi.fn();
    render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} onSetMatchSize={onSetMatchSize} />);
    fireEvent.click(screen.getByRole("button", { name: /Match Doc 1 size/ }));
    expect(onSetMatchSize).toHaveBeenCalledWith("2065x2923");
  });

  it("sets Match size to Doc 2's rendered size", () => {
    const onSetMatchSize = vi.fn();
    render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} onSetMatchSize={onSetMatchSize} />);
    fireEvent.click(screen.getByRole("button", { name: /Match Doc 2 size/ }));
    expect(onSetMatchSize).toHaveBeenCalledWith("2125x2750");
  });

  it("dismisses the dialog after applying a Match size fix", () => {
    render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} onSetMatchSize={noop} />);
    fireEvent.click(screen.getByRole("button", { name: /Match Doc 1 size/ }));
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("replaces Open Options while the fixes are offered", () => {
    render(<PrecheckPanel findings={[DIMENSION, WARNING]} onOpenOptions={noop} />);
    expect(screen.queryByRole("button", { name: "Open Options" })).toBeNull();
  });

  it("keeps Open Options for findings without fix data", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    expect(screen.getByRole("button", { name: "Open Options" })).toBeTruthy();
  });

  it("offers no fixes when the finding carries no sizes", () => {
    render(<PrecheckPanel findings={[{ ...DIMENSION, severity: "INFO", data: undefined }]} onOpenOptions={noop} />);
    expect(screen.queryByRole("button", { name: /Match Doc 1 size/ })).toBeNull();
  });

  it("still offers the fixes when an error blocks the run", () => {
    render(<PrecheckPanel findings={[ERROR, DIMENSION]} onOpenOptions={noop} />);
    expect(screen.getByRole("button", { name: /Match Doc 1 size/ })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Run anyway" })).toBeNull();
  });

  it("does not re-open when a seen finding merely changes severity", () => {
    const { rerender } = render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} />);
    dismiss();
    rerender(
      <PrecheckPanel
        findings={[{ ...DIMENSION, severity: "INFO", data: undefined }]}
        onOpenOptions={noop}
      />,
    );
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});

describe("PrecheckPanel visual finding presentation", () => {
  it("gives a sized dimension finding a human title instead of the raw message", () => {
    render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} />);
    const dialog = screen.getByRole("dialog").textContent ?? "";
    expect(dialog).toContain("Page sizes don't match");
    expect(dialog).not.toContain("Eyes resolves baselines by viewport");
  });

  it("labels both docs with their rendered pixel sizes", () => {
    render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} />);
    const dialog = screen.getByRole("dialog").textContent ?? "";
    expect(dialog).toContain("2065 × 2923 px");
    expect(dialog).toContain("2125 × 2750 px");
  });

  it("shows the mismatched pages as a chip", () => {
    render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} />);
    expect(screen.getByRole("dialog").textContent).toContain("Pages 1, 3");
  });

  it("warns that differing dimensions can skew content", () => {
    render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} />);
    expect(screen.getByRole("dialog").textContent).toContain(
      "Comparing PDFs with differing dimensions may lead to unexpected results due to content skewing.",
    );
  });

  it("gives a counted page-count finding a human title with both counts", () => {
    render(<PrecheckPanel findings={[PAGE_COUNT]} onOpenOptions={noop} />);
    const dialog = screen.getByRole("dialog").textContent ?? "";
    expect(dialog).toContain("Page counts differ");
    expect(dialog).not.toContain("Doc 1 has 3 page(s)");
  });

  it("falls back to the raw message when a finding carries no data", () => {
    render(
      <PrecheckPanel
        findings={[{ ...DIMENSION, severity: "INFO", data: undefined }]}
        onOpenOptions={noop}
      />,
    );
    expect(screen.getByRole("dialog").textContent).toContain("Eyes resolves baselines by viewport");
  });

  it("shows no footer note while fixes are offered", () => {
    render(<PrecheckPanel findings={[DIMENSION]} onOpenOptions={noop} />);
    const dialog = screen.getByRole("dialog").textContent ?? "";
    expect(dialog).not.toContain("Match size in Options");
    expect(dialog).not.toContain("You can run anyway");
  });
});

describe("PrecheckPanel actions and keyboard", () => {
  it("invokes onOpenOptions from the primary button", () => {
    const onOpenOptions = vi.fn();
    render(<PrecheckPanel findings={[ERROR]} onOpenOptions={onOpenOptions} />);
    fireEvent.click(screen.getByRole("button", { name: "Open Options" }));
    expect(onOpenOptions).toHaveBeenCalledTimes(1);
  });

  it("closes the dialog on Escape", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("moves focus to the dialog on auto-open", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    expect(screen.getByRole("dialog").contains(document.activeElement)).toBe(true);
  });

  it("returns focus to the pill on close", () => {
    render(<PrecheckPanel findings={[WARNING]} onOpenOptions={noop} />);
    dismiss();
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "1 precheck finding — Review" }));
  });
});
