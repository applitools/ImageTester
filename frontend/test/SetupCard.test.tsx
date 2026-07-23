import { render, screen, fireEvent } from "@testing-library/react";
import { SetupCard } from "../src/components/SetupCard";
import { App } from "../src/App";

describe("SetupCard", () => {
  it("disables the Run button when api key is missing", () => {
    render(<SetupCard hasKey={false} sourcePath="/x" matchLevel="Strict" running={false} optionsCount={0} drawerOpen={false} compareMode={false} doc1Path="" doc2Path="" forcedName="cmp" onForcedNameChange={() => {}} onToggleCompareMode={() => {}} onChooseDoc1={() => {}} onChooseDoc2={() => {}} onDropDoc1={() => {}} onDropDoc2={() => {}} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByRole("button", { name: /run test/i })).toBeDisabled();
  });

  it("disables the Run button when source path is empty", () => {
    render(<SetupCard hasKey={true} sourcePath="" matchLevel="Strict" running={false} optionsCount={0} drawerOpen={false} compareMode={false} doc1Path="" doc2Path="" forcedName="cmp" onForcedNameChange={() => {}} onToggleCompareMode={() => {}} onChooseDoc1={() => {}} onChooseDoc2={() => {}} onDropDoc1={() => {}} onDropDoc2={() => {}} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByRole("button", { name: /run test/i })).toBeDisabled();
  });

  it("enables the Run button when both api key and source are set", () => {
    render(<SetupCard hasKey={true} sourcePath="/x" matchLevel="Strict" running={false} optionsCount={0} drawerOpen={false} compareMode={false} doc1Path="" doc2Path="" forcedName="cmp" onForcedNameChange={() => {}} onToggleCompareMode={() => {}} onChooseDoc1={() => {}} onChooseDoc2={() => {}} onDropDoc1={() => {}} onDropDoc2={() => {}} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByRole("button", { name: /run test/i })).not.toBeDisabled();
  });

  it("shows Cancel instead of Run while a run is in flight", () => {
    render(<SetupCard hasKey={true} sourcePath="/x" matchLevel="Strict" running={true} optionsCount={0} drawerOpen={false} compareMode={false} doc1Path="" doc2Path="" forcedName="cmp" onForcedNameChange={() => {}} onToggleCompareMode={() => {}} onChooseDoc1={() => {}} onChooseDoc2={() => {}} onDropDoc1={() => {}} onDropDoc2={() => {}} onRun={() => {}} onCancel={() => {}} onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByRole("button", { name: /cancel/i })).toBeInTheDocument();
  });
});

describe("SetupCard options gear", () => {
  it("shows the non-default option count in the gear badge", () => {
    render(<SetupCard hasKey sourcePath="/x" matchLevel="Strict" running={false}
      optionsCount={3} drawerOpen={false} compareMode={false} doc1Path="" doc2Path="" forcedName="cmp" onForcedNameChange={() => {}}
      onToggleCompareMode={() => {}} onChooseDoc1={() => {}} onChooseDoc2={() => {}} onDropDoc1={() => {}} onDropDoc2={() => {}}
      onSetKey={() => {}} onChoosePath={() => {}} onMatchLevel={() => {}}
      onRun={() => {}} onCancel={() => {}} onToggleDrawer={() => {}} />);
    expect(screen.getByText(/3 set/)).toBeInTheDocument();
  });
});

describe("SetupCard compare mode", () => {
  const baseProps = {
    hasKey: true,
    sourcePath: "",
    matchLevel: "Strict" as const,
    running: false,
    optionsCount: 0,
    drawerOpen: false,
    doc1Path: "",
    doc2Path: "",
    forcedName: "cmp",
    onSetKey: () => {},
    onChoosePath: () => {},
    onChooseDoc1: () => {},
    onChooseDoc2: () => {},
    onDropDoc1: () => {},
    onDropDoc2: () => {},
    onForcedNameChange: () => {},
    onToggleCompareMode: () => {},
    onMatchLevel: () => {},
    onRun: () => {},
    onCancel: () => {},
    onToggleDrawer: () => {},
  };

  it("shows a Folder/File vs Compare two documents toggle", () => {
    render(<SetupCard {...baseProps} compareMode={false} />);
    expect(screen.getByRole("button", { name: /folder\/file/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /compare two documents/i })).toBeInTheDocument();
  });

  it("shows the single Source picker when not in compare mode", () => {
    render(<SetupCard {...baseProps} compareMode={false} />);
    expect(screen.getByText("No file or folder chosen")).toBeInTheDocument();
    expect(screen.queryByText(/doc 1/i)).not.toBeInTheDocument();
  });

  it("shows two file pickers instead of the Source row when in compare mode", () => {
    render(<SetupCard {...baseProps} compareMode={true} />);
    expect(screen.queryByText("No file or folder chosen")).not.toBeInTheDocument();
    expect(screen.getByText("Doc 1", { selector: "label", exact: false })).toBeInTheDocument();
    expect(screen.getByText("Doc 2", { selector: "label", exact: false })).toBeInTheDocument();
  });

  it("clicking the compare-mode toggle calls onToggleCompareMode", () => {
    const onToggle = vi.fn();
    render(<SetupCard {...baseProps} compareMode={false} onToggleCompareMode={onToggle} />);
    fireEvent.click(screen.getByRole("button", { name: /compare two documents/i }));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it("clicking Choose file for Doc 1 calls onChooseDoc1", () => {
    const onChooseDoc1 = vi.fn();
    render(<SetupCard {...baseProps} compareMode={true} onChooseDoc1={onChooseDoc1} />);
    const doc1Buttons = screen.getAllByRole("button", { name: /choose file/i });
    fireEvent.click(doc1Buttons[0]);
    expect(onChooseDoc1).toHaveBeenCalledTimes(1);
  });

  it("shows a required Comparison name field directly on the card in compare mode", () => {
    render(<SetupCard {...baseProps} compareMode={true} />);
    expect(screen.getByText(/comparison name/i)).toBeInTheDocument();
    expect(screen.getAllByText("*").length).toBeGreaterThan(0);
    expect(screen.getByLabelText("Comparison name")).toBeInTheDocument();
  });

  it("marks every required field with an asterisk in compare mode", () => {
    render(<SetupCard {...baseProps} compareMode={true} />);
    expect(screen.getByText("Applitools API key", { exact: false })).toBeInTheDocument();
    expect(screen.getAllByText("*")).toHaveLength(4); // API key, Doc 1, Doc 2, Comparison name
  });

  it("marks API key and Source as required in folder/file mode, but not Match level", () => {
    render(<SetupCard {...baseProps} compareMode={false} />);
    expect(screen.getAllByText("*")).toHaveLength(2); // API key, Source
    expect(screen.queryByText("Match level", { exact: false })?.textContent).not.toContain("*");
  });

  it("does not show the Comparison name field outside compare mode", () => {
    render(<SetupCard {...baseProps} compareMode={false} />);
    expect(screen.queryByLabelText("Comparison name")).not.toBeInTheDocument();
  });

  it("typing in the Comparison name field calls onForcedNameChange", () => {
    const onForcedNameChange = vi.fn();
    render(<SetupCard {...baseProps} compareMode={true} forcedName="" onForcedNameChange={onForcedNameChange} />);
    fireEvent.change(screen.getByLabelText("Comparison name"), { target: { value: "cmp-1" } });
    expect(onForcedNameChange).toHaveBeenCalledWith("cmp-1");
  });

  it("disables Run in compare mode when Comparison name is empty even with both docs and a key", () => {
    render(<SetupCard {...baseProps} compareMode={true} doc1Path="/a.pdf" doc2Path="/b.pdf" forcedName="" />);
    expect(screen.getByRole("button", { name: /run test/i })).toBeDisabled();
  });

  it("enables Run in compare mode once both docs, a key, and a Comparison name are all set", () => {
    render(<SetupCard {...baseProps} compareMode={true} doc1Path="/a.pdf" doc2Path="/b.pdf" forcedName="cmp-1" />);
    expect(screen.getByRole("button", { name: /run test/i })).not.toBeDisabled();
  });

  it("renders the Doc 1 and Doc 2 zones in one side-by-side grid row", () => {
    render(<SetupCard {...baseProps} compareMode={true} />);
    const zone = screen.getByRole("button", { name: /choose file for doc 1/i });
    expect(zone.parentElement?.parentElement).toHaveClass("grid-cols-2");
  });

  it("dropping a file on the Doc 2 zone calls onDropDoc2 with it", () => {
    const onDropDoc2 = vi.fn();
    render(<SetupCard {...baseProps} compareMode={true} onDropDoc2={onDropDoc2} />);
    const file = new File(["content"], "b.pdf", { type: "application/pdf" });
    fireEvent.drop(screen.getByRole("button", { name: /choose file for doc 2/i }), {
      dataTransfer: { files: [file], items: [] },
    });
    expect(onDropDoc2).toHaveBeenCalledWith(file);
  });

  it("shows the Doc 1 upload error next to its zone", () => {
    render(<SetupCard {...baseProps} compareMode={true} doc1UploadError="500: boom" />);
    expect(screen.getByRole("alert")).toHaveTextContent("500: boom");
  });
});

describe("App layout", () => {
  it("keeps the status pane mounted when the drawer opens", () => {
    render(<App />);
    fireEvent.click(screen.getByText("⚙ Options"));
    expect(screen.getByText(/Logs|No file/i)).toBeInTheDocument();
  });
});
