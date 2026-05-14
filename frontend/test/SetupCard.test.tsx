import { render, screen } from "@testing-library/react";
import { SetupCard } from "../src/components/SetupCard";

describe("SetupCard", () => {
  it("disables the Run button when api key is missing", () => {
    render(<SetupCard hasKey={false} sourcePath="/x" matchLevel="Strict" running={false} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} />);
    expect(screen.getByRole("button", { name: /run test/i })).toBeDisabled();
  });

  it("disables the Run button when source path is empty", () => {
    render(<SetupCard hasKey={true} sourcePath="" matchLevel="Strict" running={false} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} />);
    expect(screen.getByRole("button", { name: /run test/i })).toBeDisabled();
  });

  it("enables the Run button when both api key and source are set", () => {
    render(<SetupCard hasKey={true} sourcePath="/x" matchLevel="Strict" running={false} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} />);
    expect(screen.getByRole("button", { name: /run test/i })).not.toBeDisabled();
  });

  it("shows Cancel instead of Run while a run is in flight", () => {
    render(<SetupCard hasKey={true} sourcePath="/x" matchLevel="Strict" running={true} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} />);
    expect(screen.getByRole("button", { name: /cancel/i })).toBeInTheDocument();
  });
});
