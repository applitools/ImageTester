import { render, screen } from "@testing-library/react";
import { App } from "../src/App";

describe("App status column layout", () => {
  it("positions the Status cell absolutely within its grid cell at md+", () => {
    render(<App />);
    const statusHeading = screen.getByRole("heading", { name: "Status" });
    const absoluteWrapper = statusHeading.closest(".md\\:absolute");
    expect(absoluteWrapper).toHaveClass("md:inset-0");
  });

  it("marks the grid's right column as relatively positioned at md+", () => {
    render(<App />);
    const statusHeading = screen.getByRole("heading", { name: "Status" });
    expect(statusHeading.closest(".md\\:relative")).toBeInTheDocument();
  });
});
