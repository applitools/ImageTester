import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { OPTION_SPECS, TABS, docUrl } from "../src/lib/optionsSchema";

describe("optionsSchema", () => {
  it("orders metadata tab first", () => {
    expect(TABS[0].id).toBe("metadata");
  });

  it("excludes throwExceptions and batchMapper", () => {
    const flags = OPTION_SPECS.map((o) => o.flag);
    expect(flags).not.toContain("te");
    expect(flags).not.toContain("mp");
  });

  it("places view key in downloads tab", () => {
    const vk = OPTION_SPECS.find((o) => o.flag === "vk");
    expect(vk?.tab).toBe("downloads");
  });

  it("places render threads in the execution tab as a number", () => {
    const rt = OPTION_SPECS.find((o) => o.flag === "rt");
    expect(rt?.tab).toBe("execution");
    expect(rt?.type).toBe("number");
  });

  it("places regex file filter in the execution tab as text", () => {
    const rf = OPTION_SPECS.find((o) => o.flag === "rf");
    expect(rf?.tab).toBe("execution");
    expect(rf?.type).toBe("text");
  });

  it("offers pdf trim as a checkbox in the pdf tab", () => {
    const tp = OPTION_SPECS.find((o) => o.flag === "tp");
    expect(tp?.tab).toBe("pdf");
    expect(tp?.type).toBe("checkbox");
  });

  it("gives every option a help tip", () => {
    const withoutHelp = OPTION_SPECS.filter((o) => !o.help);
    expect(withoutHelp).toEqual([]);
  });

  it("links both normalize-fonts flags to the font-normalization README section", () => {
    const nf = OPTION_SPECS.find((o) => o.flag === "nf")!;
    const nfj = OPTION_SPECS.find((o) => o.flag === "nfj")!;
    expect(docUrl(nf)).toContain("#font-normalization");
    expect(docUrl(nfj)).toContain("#font-normalization");
  });

  it("offers Japanese font normalization as a checkbox in the pdf tab", () => {
    const nfj = OPTION_SPECS.find((o) => o.flag === "nfj");
    expect(nfj?.tab).toBe("pdf");
    expect(nfj?.type).toBe("checkbox");
  });

  it("links PDF options to the documents README section", () => {
    const di = OPTION_SPECS.find((o) => o.flag === "di")!;
    expect(docUrl(di)).toContain("#pdf-and-document-options");
  });

  it("links each tab to a section heading that exists in the README", () => {
    // Every anchor docUrl produces must correspond to a real README heading slug.
    const readme = readFileSync(resolve(process.cwd(), "../README.md"), "utf8");
    const headingSlugs = new Set(
      readme.split("\n")
        .filter((l) => /^#{1,6}\s/.test(l))
        .map((l) => l.replace(/^#{1,6}\s+/, "").toLowerCase()
          .replace(/[^\w\s-]/g, "").trim().replace(/\s+/g, "-")),
    );
    for (const spec of OPTION_SPECS) {
      const anchor = docUrl(spec).split("#")[1];
      expect(headingSlugs.has(anchor)).toBe(true);
    }
  });
});
