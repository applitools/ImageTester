import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TestRow } from "../src/components/TestRow";
import type { TestRow as Row } from "../src/types";

const baseRow: Row = { name: "compare-1", status: "passed", durationMs: 10, previewPath: "/a/doc1.png" };

describe("TestRow", () => {
  it("renders only one preview image when doc2PreviewPath is absent", () => {
    const { container } = render(<TestRow row={baseRow} now={0} />);
    expect(container.querySelectorAll("img")).toHaveLength(1);
  });

  it("renders a second preview image side by side when doc2PreviewPath is present", () => {
    const row: Row = { ...baseRow, doc2PreviewPath: "/a/doc2.png" };
    const { container } = render(<TestRow row={row} now={0} />);
    expect(container.querySelectorAll("img")).toHaveLength(2);
  });

  it("second preview request includes doc2's path", () => {
    const row: Row = { ...baseRow, doc2PreviewPath: "/a/doc2.png" };
    const { container } = render(<TestRow row={row} now={0} />);
    const imgs = container.querySelectorAll("img");
    expect(imgs[1].getAttribute("src")).toContain(encodeURIComponent("/a/doc2.png"));
  });
});

describe("TestRow status label", () => {
  it.each([
    ["passed", "Passed"],
    ["mismatch", "Mismatch"],
    ["failed", "Failed"],
    ["new", "New"],
    ["aborted", "Aborted"],
    ["error", "Error"],
    ["cancelled", "Cancelled"],
  ] as const)("renders %s as the label %s", (status, label) => {
    render(<TestRow row={{ ...baseRow, status }} now={0} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it("shows the elapsed spinner, not a label, while running", () => {
    render(<TestRow row={{ name: "t", status: "running", startedAtMs: 1000 }} now={6000} />);
    expect(screen.getByText("5s")).toBeInTheDocument();
  });
});
