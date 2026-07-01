import { describe, it, expect } from "vitest";
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

  it("shows the active tab's intro description", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    expect(screen.getByText(/Labels and identifiers attached to your tests/i)).toBeInTheDocument();
  });

  it("shows help text under a region control that has no built-in help line", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Regions"));
    expect(screen.getByText(/Areas excluded from comparison/i)).toBeInTheDocument();
  });

  it("links each help tip to the README", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    const link = screen.getByLabelText("App name documentation");
    expect(link).toHaveAttribute("href", expect.stringContaining("github.com/applitools/ImageTester"));
  });

  it("no longer shows the manual remove-watermark-text field", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Watermark"));
    expect(screen.queryByText(/Remove watermark text/i)).not.toBeInTheDocument();
  });

  it("reveals the output-folder input only after checking the local-only box", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Watermark"));
    expect(screen.queryByLabelText("Output folder for cleaned PDFs")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: /only produce cleaned PDFs locally/i }));
    expect(screen.getByLabelText("Output folder for cleaned PDFs")).toBeInTheDocument();
  });
});
