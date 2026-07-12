import { useState } from "react";
import type { OptionSpec } from "../../lib/optionsSchema";

interface Props {
  spec: OptionSpec;
  value: string;
  onChange: (v: string) => void;
}

// The checkbox is a local UI toggle; the backing option value is the output directory.
// An empty directory means "off", so a non-empty value seeds the checkbox as checked.
export function WatermarkOutControl({ spec, value, onChange }: Props) {
  const [enabled, setEnabled] = useState(value !== "");
  const id = `opt-${spec.flag}`;

  const toggle = (checked: boolean) => {
    setEnabled(checked);
    if (!checked) onChange("");
  };

  return (
    <div className="text-sm">
      <label htmlFor={id} className="flex items-center gap-2 text-gray-700">
        <input id={id} type="checkbox" checked={enabled} onChange={(e) => toggle(e.target.checked)} />
        {spec.label}
      </label>
      {enabled && (
        <input
          aria-label="Output folder for cleaned PDFs"
          type="text"
          value={value}
          placeholder="folder for cleaned PDFs"
          onChange={(e) => onChange(e.target.value)}
          className="mt-2 w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-brand-teal focus:outline-none"
        />
      )}
    </div>
  );
}
