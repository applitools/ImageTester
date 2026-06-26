import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { OptionsDrawer } from "../src/components/OptionsDrawer";
import { defaultOptions } from "../src/lib/options";

describe("OptionsDrawer", () => {
  it("shows metadata tab controls by default", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    expect(screen.getByLabelText("App name")).toBeInTheDocument();
  });

  it("switches to PDF tab on click", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("PDF & Documents"));
    expect(screen.getByLabelText("DPI")).toBeInTheDocument();
  });
});
