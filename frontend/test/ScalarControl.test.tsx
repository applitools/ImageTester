import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ScalarControl } from "../src/components/controls/ScalarControl";

describe("ScalarControl", () => {
  it("emits new text value on change", () => {
    const onChange = vi.fn();
    render(<ScalarControl spec={{ flag: "a", label: "App name", type: "text", tab: "metadata", default: "" }} value="" onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("App name"), { target: { value: "X" } });
    expect(onChange).toHaveBeenCalledWith("X");
  });

  it("emits boolean on checkbox toggle", () => {
    const onChange = vi.fn();
    render(<ScalarControl spec={{ flag: "nf", label: "Normalize fonts", type: "checkbox", tab: "pdf", default: false }} value={false} onChange={onChange} />);
    fireEvent.click(screen.getByLabelText("Normalize fonts"));
    expect(onChange).toHaveBeenCalledWith(true);
  });
});
