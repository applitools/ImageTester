import { useState } from "react";
import { OPTION_SPECS, TABS, docUrl, type OptionSpec, type TabId } from "../lib/optionsSchema";
import { countNonDefaultForTab, listNonDefault, type RunOptions } from "../lib/options";
import { ScalarControl } from "./controls/ScalarControl";
import { RegionBuilder } from "./controls/RegionBuilder";
import { ProxyControl } from "./controls/ProxyControl";
import { PropertiesControl } from "./controls/PropertiesControl";
import { ImageCutControl } from "./controls/ImageCutControl";
import { WatermarkOutControl } from "./controls/WatermarkOutControl";

interface Props {
  options: RunOptions;
  onChange: (flag: string, value: unknown) => void;
  onClose: () => void;
  compareMode?: boolean;
}

const FULL_ROW_TYPES = new Set(["regions", "proxy", "properties", "imagecut", "watermarkout"]);
const CHIP_VALUE_MAX_CHARS = 24;

function chipText(spec: OptionSpec, value: unknown): string {
  if (typeof value === "boolean") return spec.label;
  const text = String(value);
  const shown = text.length > CHIP_VALUE_MAX_CHARS ? text.slice(0, CHIP_VALUE_MAX_CHARS) + "…" : text;
  return `${spec.label}: ${shown}`;
}

function compareModeForcedNameHelp(spec: OptionSpec, compareMode: boolean | undefined): string | undefined {
  if (spec.flag !== "fn" || !compareMode) return undefined;
  return "Required in Compare mode — Doc 1 and Doc 2 must share this name to be compared. Reusing a name from a previous comparison means today's first-run document, not necessarily Doc 1, becomes what Doc 2 is compared against.";
}

function renderControl(spec: OptionSpec, value: unknown, onChange: (v: unknown) => void) {
  switch (spec.type) {
    case "regions":    return <RegionBuilder spec={spec} value={String(value ?? "")} onChange={onChange} />;
    case "proxy":      return <ProxyControl spec={spec} value={String(value ?? "")} onChange={onChange} />;
    case "properties": return <PropertiesControl spec={spec} value={String(value ?? "")} onChange={onChange} />;
    case "imagecut":   return <ImageCutControl spec={spec} value={String(value ?? "")} onChange={onChange} />;
    case "watermarkout": return <WatermarkOutControl spec={spec} value={String(value ?? "")} onChange={onChange} />;
    default:           return <ScalarControl spec={spec} value={value} onChange={onChange} />;
  }
}

export function OptionsDrawer({ options, onChange, onClose, compareMode }: Props) {
  const [active, setActive] = useState<TabId>("metadata");
  const activeTab = TABS.find((t) => t.id === active);
  const activeOptions = listNonDefault(options);
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-xs uppercase tracking-wider text-gray-500">Options</span>
        <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-700" aria-label="Close options">✕</button>
      </div>
      {activeOptions.length > 0 && (
        <div className="mb-3 flex flex-wrap items-center gap-1" aria-label="Active options">
          {activeOptions.map(({ spec, value }) => (
            <button key={spec.flag} type="button" onClick={() => setActive(spec.tab)}
              title={`Configured on the ${TABS.find((t) => t.id === spec.tab)?.label} tab`}
              className="rounded-full border border-brand-teal/30 bg-brand-teal/5 px-2 py-0.5 text-xs text-brand-teal hover:bg-brand-teal/10">
              {chipText(spec, value)}
            </button>
          ))}
        </div>
      )}
      <div className="mb-3 flex flex-wrap gap-1" role="tablist">
        {TABS.map((t) => {
          const setCount = countNonDefaultForTab(options, t.id);
          return (
            <button key={t.id} type="button" role="tab" aria-selected={active === t.id} onClick={() => setActive(t.id)}
              aria-label={setCount > 0 ? `${t.label} — ${setCount} set` : undefined}
              className={`rounded-md px-2 py-1 text-xs ${active === t.id ? "bg-brand-teal text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>
              {t.label}
              {setCount > 0 && (
                <span aria-hidden="true"
                  className={`ml-1.5 inline-block rounded-full px-1.5 text-[10px] font-semibold ${
                    active === t.id ? "bg-white/25 text-white" : "bg-brand-teal/10 text-brand-teal"}`}>
                  {setCount}
                </span>
              )}
            </button>
          );
        })}
      </div>
      {activeTab && <p className="mb-3 text-xs text-gray-500">{activeTab.description}</p>}
      {/* All tab panels stay mounted, stacked in the same grid cell (gridRow/gridColumn: 1),
          so the row auto-sizes to the tallest tab and switching never changes the drawer's
          height. Inactive panels are invisible + non-interactive but still occupy layout. */}
      <div className="grid">
        {TABS.map((t) => {
          const isActive = t.id === active;
          const specsForTab = OPTION_SPECS.filter((s) => s.tab === t.id);
          return (
            <div key={t.id} role="tabpanel" aria-hidden={!isActive} style={{ gridRow: 1, gridColumn: 1 }}
              className={`grid grid-cols-1 gap-x-6 gap-y-4 md:grid-cols-2 xl:grid-cols-3 ${isActive ? "" : "invisible pointer-events-none"}`}>
              {specsForTab.map((spec) => (
                <div key={spec.flag} className={FULL_ROW_TYPES.has(spec.type) ? "md:col-span-2 xl:col-span-3" : ""}>
                  {renderControl(spec, options[spec.flag], (v) => onChange(spec.flag, v))}
                  {(compareModeForcedNameHelp(spec, compareMode) ?? spec.help) && (
                    <p className="mt-1 text-xs text-gray-400">
                      {compareModeForcedNameHelp(spec, compareMode) ?? spec.help}{" "}
                      <a href={docUrl(spec)} target="_blank" rel="noreferrer"
                        className="whitespace-nowrap text-brand-teal hover:underline"
                        aria-label={`${spec.label} documentation`}>README ↗</a>
                    </p>
                  )}
                </div>
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
}
