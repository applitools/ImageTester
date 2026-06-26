import { useState } from "react";
import { OPTION_SPECS, TABS, type OptionSpec, type TabId } from "../lib/optionsSchema";
import type { RunOptions } from "../lib/options";
import { ScalarControl } from "./controls/ScalarControl";
import { RegionBuilder } from "./controls/RegionBuilder";
import { ProxyControl } from "./controls/ProxyControl";
import { PropertiesControl } from "./controls/PropertiesControl";
import { ImageCutControl } from "./controls/ImageCutControl";

interface Props {
  options: RunOptions;
  onChange: (flag: string, value: unknown) => void;
  onClose: () => void;
}

function renderControl(spec: OptionSpec, value: unknown, onChange: (v: unknown) => void) {
  switch (spec.type) {
    case "regions":    return <RegionBuilder spec={spec} value={String(value ?? "")} onChange={onChange} />;
    case "proxy":      return <ProxyControl spec={spec} value={String(value ?? "")} onChange={onChange} />;
    case "properties": return <PropertiesControl spec={spec} value={String(value ?? "")} onChange={onChange} />;
    case "imagecut":   return <ImageCutControl spec={spec} value={String(value ?? "")} onChange={onChange} />;
    default:           return <ScalarControl spec={spec} value={value} onChange={onChange} />;
  }
}

export function OptionsDrawer({ options, onChange, onClose }: Props) {
  const [active, setActive] = useState<TabId>("metadata");
  const specs = OPTION_SPECS.filter((s) => s.tab === active);
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-xs uppercase tracking-wider text-gray-500">Options</span>
        <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-700" aria-label="Close options">✕</button>
      </div>
      <div className="mb-3 flex flex-wrap gap-1" role="tablist">
        {TABS.map((t) => (
          <button key={t.id} type="button" role="tab" aria-selected={active === t.id} onClick={() => setActive(t.id)}
            className={`rounded-md px-2 py-1 text-xs ${active === t.id ? "bg-brand-teal text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>
            {t.label}
          </button>
        ))}
      </div>
      <div className="space-y-3">
        {specs.map((spec) => (
          <div key={spec.flag}>{renderControl(spec, options[spec.flag], (v) => onChange(spec.flag, v))}</div>
        ))}
      </div>
    </div>
  );
}
