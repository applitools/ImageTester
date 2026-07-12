import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { RegionBuilder } from "../src/components/controls/RegionBuilder";

const spec = { flag: "ir", label: "Ignore regions", type: "regions", tab: "regions", default: "" } as const;

describe("RegionBuilder", () => {
  it("serializes one row to x,y,w,h", () => {
    const onChange = vi.fn();
    render(<RegionBuilder spec={spec} value="" onChange={onChange} />);
    fireEvent.click(screen.getByText("+ Add region"));
    fireEvent.change(screen.getByLabelText("ir-0-x"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText("ir-0-y"), { target: { value: "2" } });
    fireEvent.change(screen.getByLabelText("ir-0-w"), { target: { value: "3" } });
    fireEvent.change(screen.getByLabelText("ir-0-h"), { target: { value: "4" } });
    expect(onChange).toHaveBeenLastCalledWith("1,2,3,4");
  });
});
