import { describe, it, expect } from "vitest";
import { OPTION_SPECS, TABS } from "../src/lib/optionsSchema";

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
});
