// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";

import { ScalarControl } from "./ScalarControl";
import type { OptionSpec } from "../../lib/optionsSchema";

const selectSpec: OptionSpec = {
  flag: "ac",
  label: "Accessibility",
  type: "select",
  tab: "matching",
  options: [
    { value: "", label: "Off" },
    { value: "AA:WCAG_2_1", label: "AA — WCAG 2.1" },
  ],
  default: "",
};

describe("ScalarControl select", () => {
  it("shows the friendly label for labeled options", () => {
    render(<ScalarControl spec={selectSpec} value="" onChange={() => {}} />);
    expect(screen.getByRole("option", { name: "AA — WCAG 2.1" })).toBeTruthy();
  });

  it("emits the raw value when a labeled option is chosen", () => {
    const onChange = vi.fn();
    render(<ScalarControl spec={selectSpec} value="" onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("Accessibility"), { target: { value: "AA:WCAG_2_1" } });
    expect(onChange).toHaveBeenCalledWith("AA:WCAG_2_1");
  });

  it("still renders plain-string options as their own label", () => {
    const plain: OptionSpec = { ...selectSpec, options: ["one", "two"] };
    render(<ScalarControl spec={plain} value="one" onChange={() => {}} />);
    expect(screen.getByRole("option", { name: "two" })).toBeTruthy();
  });
});

describe("ScalarControl text", () => {
  it("shows the spec placeholder in an empty text input", () => {
    const spec: OptionSpec = {
      flag: "s",
      label: "Server URL",
      type: "text",
      tab: "connection",
      placeholder: "https://eyes.applitools.com",
      default: "",
    };
    render(<ScalarControl spec={spec} value="" onChange={() => {}} />);
    expect(screen.getByPlaceholderText("https://eyes.applitools.com")).toBeTruthy();
  });
});
