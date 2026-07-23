import { render, screen } from "@testing-library/react";
import { PrecheckPanel } from "../src/components/PrecheckPanel";
import type { PrecheckFinding } from "../src/types";

const finding = (severity: PrecheckFinding["severity"], message: string): PrecheckFinding =>
  ({ severity, code: "test-code", message });

describe("PrecheckPanel", () => {
  it("renders nothing when there are no findings", () => {
    const { container } = render(<PrecheckPanel findings={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders an error finding with role alert", () => {
    render(<PrecheckPanel findings={[finding("ERROR", "Doc 2 can't be read")]} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Doc 2 can't be read");
  });

  it("renders a warning finding with role status", () => {
    render(<PrecheckPanel findings={[finding("WARNING", "Page dimensions differ")]} />);
    expect(screen.getByRole("status")).toHaveTextContent("Page dimensions differ");
  });

  it("renders an info finding with role status", () => {
    render(<PrecheckPanel findings={[finding("INFO", "identical content")]} />);
    expect(screen.getByRole("status")).toHaveTextContent("identical content");
  });

  it("renders every finding in order", () => {
    render(<PrecheckPanel findings={[finding("ERROR", "first"), finding("WARNING", "second")]} />);
    const items = screen.getAllByText(/first|second/);
    expect(items.map((e) => e.textContent)).toEqual(["first", "second"]);
  });
});
