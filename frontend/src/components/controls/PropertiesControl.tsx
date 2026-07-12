import { useState } from "react";
import type { OptionSpec } from "../../lib/optionsSchema";

interface Props { spec: OptionSpec; value: string; onChange: (v: string) => void; }
type Pair = { k: string; v: string };

const parse = (s: string): Pair[] =>
  s ? s.split("|").map((p) => { const [k = "", v = ""] = p.split(":"); return { k, v }; }) : [];

const serialize = (rows: Pair[]) =>
  rows.filter((r) => r.k && r.v).map((r) => `${r.k}:${r.v}`).join("|");

export function PropertiesControl({ spec, value, onChange }: Props) {
  const [rows, setRows] = useState<Pair[]>(parse(value));
  const update = (next: Pair[]) => { setRows(next); onChange(serialize(next)); };
  return (
    <div className="text-sm">
      <span className="text-gray-700">{spec.label}</span>
      {rows.map((r, i) => (
        <div key={i} className="mt-1 flex gap-1">
          <input aria-label={`pr-${i}-k`} value={r.k} placeholder="key"
            onChange={(e) => update(rows.map((x, idx) => idx === i ? { ...x, k: e.target.value } : x))}
            className="flex-1 rounded-md border border-gray-200 px-2 py-1 text-sm" />
          <input aria-label={`pr-${i}-v`} value={r.v} placeholder="value"
            onChange={(e) => update(rows.map((x, idx) => idx === i ? { ...x, v: e.target.value } : x))}
            className="flex-1 rounded-md border border-gray-200 px-2 py-1 text-sm" />
          <button type="button" onClick={() => update(rows.filter((_, idx) => idx !== i))}
            className="px-2 text-gray-400">✕</button>
        </div>
      ))}
      <button type="button" onClick={() => update([...rows, { k: "", v: "" }])}
        className="mt-1 text-xs text-brand-teal hover:underline">+ Add property</button>
    </div>
  );
}
