import { describe, it, expect, beforeEach } from "vitest";
import { defaultOptions, countNonDefault, toRunPayload, loadOptions, saveOptions } from "../src/lib/options";

describe("options", () => {
  beforeEach(() => localStorage.clear());

  it("defaults match level to Strict", () => {
    expect(defaultOptions().ml).toBe("Strict");
  });

  it("counts a changed option as non-default", () => {
    const o = { ...defaultOptions(), di: "300" };
    expect(countNonDefault(o)).toBe(1);
  });

  it("omits default-valued options from payload", () => {
    const payload = toRunPayload("/x", defaultOptions());
    expect(payload.options.di).toBeUndefined();
  });

  it("round-trips through localStorage", () => {
    saveOptions({ ...defaultOptions(), a: "App" });
    expect(loadOptions().a).toBe("App");
  });
});
