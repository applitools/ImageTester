import { OPTION_SPECS, type OptionSpec } from "./optionsSchema";

export type RunOptions = Record<string, unknown>;

export interface ActiveOption {
  spec: OptionSpec;
  value: unknown;
}

const STORAGE_KEY = "imagetester.options";

export function defaultOptions(): RunOptions {
  const o: RunOptions = { ml: "Strict" };
  for (const spec of OPTION_SPECS) o[spec.flag] = spec.default;
  return o;
}

function isDefault(flag: string, value: unknown): boolean {
  if (value === "" || value === false || value == null) return true;
  const spec = OPTION_SPECS.find((s) => s.flag === flag);
  return spec ? value === spec.default : false;
}

export function countNonDefault(o: RunOptions): number {
  return OPTION_SPECS.filter((s) => !isDefault(s.flag, o[s.flag])).length;
}

export function countNonDefaultForTab(o: RunOptions, tab: string): number {
  return OPTION_SPECS.filter((s) => s.tab === tab && !isDefault(s.flag, o[s.flag])).length;
}

export function listNonDefault(o: RunOptions): ActiveOption[] {
  return OPTION_SPECS
    .filter((s) => !isDefault(s.flag, o[s.flag]))
    .map((s) => ({ spec: s, value: o[s.flag] }));
}

export function toRunPayload(sourcePath: string, o: RunOptions) {
  const options: Record<string, unknown> = { ml: o.ml ?? "Strict" };
  for (const spec of OPTION_SPECS) {
    const v = o[spec.flag];
    if (!isDefault(spec.flag, v)) options[spec.flag] = v;
  }
  return { sourcePath, options };
}

export function loadOptions(): RunOptions {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? { ...defaultOptions(), ...JSON.parse(raw) } : defaultOptions();
  } catch {
    return defaultOptions();
  }
}

export function saveOptions(o: RunOptions): void {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(o)); } catch { /* ignore */ }
}
