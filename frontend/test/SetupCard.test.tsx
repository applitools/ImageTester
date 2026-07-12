import { render, screen, fireEvent } from "@testing-library/react";
import { SetupCard } from "../src/components/SetupCard";
import { App } from "../src/App";

describe("SetupCard", () => {
  it("disables the Run button when api key is missing", () => {
    render(<SetupCard hasKey={false} sourcePath="/x" matchLevel="Strict" running={false} optionsCount={0} drawerOpen={false} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByRole("button", { name: /run test/i })).toBeDisabled();
  });

  it("disables the Run button when source path is empty", () => {
    render(<SetupCard hasKey={true} sourcePath="" matchLevel="Strict" running={false} optionsCount={0} drawerOpen={false} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByRole("button", { name: /run test/i })).toBeDisabled();
  });

  it("enables the Run button when both api key and source are set", () => {
    render(<SetupCard hasKey={true} sourcePath="/x" matchLevel="Strict" running={false} optionsCount={0} drawerOpen={false} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByRole("button", { name: /run test/i })).not.toBeDisabled();
  });

  it("shows Cancel instead of Run while a run is in flight", () => {
    render(<SetupCard hasKey={true} sourcePath="/x" matchLevel="Strict" running={true} optionsCount={0} drawerOpen={false} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByRole("button", { name: /cancel/i })).toBeInTheDocument();
  });
});

describe("SetupCard options gear", () => {
  it("shows the non-default option count in the gear badge", () => {
    render(<SetupCard hasKey sourcePath="/x" matchLevel="Strict" running={false}
      optionsCount={3} drawerOpen={false}
      onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}}
      onRun={() => {}} onCancel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByText(/3 set/)).toBeInTheDocument();
  });
});

describe("App layout", () => {
  it("keeps the status pane mounted when the drawer opens", () => {
    render(<App />);
    fireEvent.click(screen.getByText("⚙ Options"));
    expect(screen.getByText(/Logs|No file/i)).toBeInTheDocument();
  });
});
