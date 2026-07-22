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
    expect(screen.getByLabelText("DPI").closest('[role="tabpanel"]')).toHaveAttribute("aria-hidden", "false");
  });

  it("keeps an inactive tab's panel marked aria-hidden", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    expect(screen.getByLabelText("DPI").closest('[role="tabpanel"]')).toHaveAttribute("aria-hidden", "true");
  });

  it("shows the active tab's intro description", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    expect(screen.getByText(/Labels and identifiers attached to your tests/i)).toBeInTheDocument();
  });

  it("shows help text under a region control that has no built-in help line", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Regions"));
    const helpText = screen.getByText(/Areas excluded from comparison/i);
    expect(helpText.closest('[role="tabpanel"]')).toHaveAttribute("aria-hidden", "false");
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

  it("shows a count badge on tabs that have configured options", () => {
    const options = { ...defaultOptions(), sp: "1,3", tp: true };
    render(<OptionsDrawer options={options} onChange={() => {}} onClose={() => {}} />);
    expect(screen.getByRole("tab", { name: "PDF & Documents — 2 set" })).toBeInTheDocument();
  });

  it("shows no badge on tabs where everything is default", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    expect(screen.getByRole("tab", { name: "PDF & Documents" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: /set$/ })).not.toBeInTheDocument();
  });

  it("lists every configured option as a chip with its value", () => {
    const options = { ...defaultOptions(), sp: "1,3", tp: true, fn: "black-card" };
    render(<OptionsDrawer options={options} onChange={() => {}} onClose={() => {}} />);
    expect(screen.getByRole("button", { name: "Selected pages: 1,3" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Trim print margins" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Forced name: black-card" })).toBeInTheDocument();
  });

  it("jumps to the option's tab when its chip is clicked", () => {
    const options = { ...defaultOptions(), sp: "1,3" };
    render(<OptionsDrawer options={options} onChange={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: "Selected pages: 1,3" }));
    expect(screen.getByLabelText("DPI").closest('[role="tabpanel"]')).toHaveAttribute("aria-hidden", "false");
  });

  it("shows no active-options row when everything is default", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    expect(screen.queryByLabelText("Active options")).not.toBeInTheDocument();
  });

  it("reveals the output-folder input only after checking the local-only box", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Watermark"));
    expect(screen.queryByLabelText("Output folder for cleaned PDFs")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: /only produce cleaned PDFs locally/i }));
    expect(screen.getByLabelText("Output folder for cleaned PDFs")).toBeInTheDocument();
  });

  it("shows the compare-mode reuse warning under Forced name when compareMode is true", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} compareMode={true} />);
    fireEvent.click(screen.getByText("Batch & Branch"));
    expect(screen.getByText(/Doc 1 and Doc 2 must share this name/i)).toBeInTheDocument();
  });

  it("shows the normal Forced name help text when compareMode is false or absent", () => {
    render(<OptionsDrawer options={defaultOptions()} onChange={() => {}} onClose={() => {}} />);
    fireEvent.click(screen.getByText("Batch & Branch"));
    expect(screen.queryByText(/Doc 1 and Doc 2 must share this name/i)).not.toBeInTheDocument();
  });
});
