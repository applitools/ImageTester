import { describe, expect, it } from "vitest";

import { OPTION_SPECS, selectOptions } from "./optionsSchema";

const AC_VALUE = /^$|^(AA|AAA):(WCAG_2_0|WCAG_2_1)$/;

describe("accessibility option", () => {
  const ac = OPTION_SPECS.find((s) => s.flag === "ac")!;

  it("is a dropdown", () => {
    expect(ac.type).toBe("select");
  });

  it("only offers values the backend -ac parser accepts", () => {
    expect(selectOptions(ac).every((o) => AC_VALUE.test(o.value))).toBe(true);
  });

  it("defaults to off (no -ac flag sent)", () => {
    expect(ac.default).toBe("");
  });
});
