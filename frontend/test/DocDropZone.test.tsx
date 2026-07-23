import { render, screen, fireEvent } from "@testing-library/react";
import { DocDropZone } from "../src/components/DocDropZone";

function makeFile(name: string): File {
  return new File(["content"], name, { type: "application/pdf" });
}

describe("DocDropZone", () => {
  const baseProps = { label: "Doc 1", path: "", onChoose: () => {}, onDropFile: () => {} };
  const zone = () => screen.getByRole("button", { name: /choose file for doc 1/i });

  it("shows the drop hint when no file is set", () => {
    render(<DocDropZone {...baseProps} />);
    expect(screen.getByText("Drop file or click to choose…")).toBeInTheDocument();
  });

  it("shows only the file basename when a path is set", () => {
    render(<DocDropZone {...baseProps} path={"C:\\tmp\\uploads\\1\\contract.pdf"} />);
    expect(screen.getByText("contract.pdf")).toBeInTheDocument();
  });

  it("puts the full path in the zone tooltip", () => {
    render(<DocDropZone {...baseProps} path={"C:\\tmp\\uploads\\1\\contract.pdf"} />);
    expect(zone()).toHaveAttribute("title", "C:\\tmp\\uploads\\1\\contract.pdf");
  });

  it("clicking the zone calls onChoose", () => {
    const onChoose = vi.fn();
    render(<DocDropZone {...baseProps} onChoose={onChoose} />);
    fireEvent.click(zone());
    expect(onChoose).toHaveBeenCalledTimes(1);
  });

  it("dropping a single file calls onDropFile with it", () => {
    const onDropFile = vi.fn();
    render(<DocDropZone {...baseProps} onDropFile={onDropFile} />);
    const file = makeFile("a.pdf");
    fireEvent.drop(zone(), { dataTransfer: { files: [file], items: [] } });
    expect(onDropFile).toHaveBeenCalledWith(file);
  });

  it("dropping multiple files shows a rejection message", () => {
    render(<DocDropZone {...baseProps} />);
    fireEvent.drop(zone(), { dataTransfer: { files: [makeFile("a.pdf"), makeFile("b.pdf")], items: [] } });
    expect(screen.getByRole("alert")).toHaveTextContent("Drop a single file");
  });

  it("dropping multiple files does not call onDropFile", () => {
    const onDropFile = vi.fn();
    render(<DocDropZone {...baseProps} onDropFile={onDropFile} />);
    fireEvent.drop(zone(), { dataTransfer: { files: [makeFile("a.pdf"), makeFile("b.pdf")], items: [] } });
    expect(onDropFile).not.toHaveBeenCalled();
  });

  it("dropping a folder shows the folder rejection message", () => {
    render(<DocDropZone {...baseProps} />);
    fireEvent.drop(zone(), {
      dataTransfer: {
        files: [makeFile("some-folder")],
        items: [{ webkitGetAsEntry: () => ({ isDirectory: true }) }],
      },
    });
    expect(screen.getByRole("alert")).toHaveTextContent(/folders can't be compared/i);
  });

  it("a successful drop clears a previous rejection message", () => {
    render(<DocDropZone {...baseProps} />);
    fireEvent.drop(zone(), { dataTransfer: { files: [makeFile("a.pdf"), makeFile("b.pdf")], items: [] } });
    fireEvent.drop(zone(), { dataTransfer: { files: [makeFile("a.pdf")], items: [] } });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("clicking the zone clears a previous rejection message", () => {
    render(<DocDropZone {...baseProps} />);
    fireEvent.drop(zone(), { dataTransfer: { files: [makeFile("a.pdf"), makeFile("b.pdf")], items: [] } });
    fireEvent.click(zone());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows the upload error passed in from the app", () => {
    render(<DocDropZone {...baseProps} uploadError="413: too large" />);
    expect(screen.getByRole("alert")).toHaveTextContent("413: too large");
  });
});
