import { useState } from "react";
import type { OptionSpec } from "../../lib/optionsSchema";

interface Props { spec: OptionSpec; value: string; onChange: (v: string) => void; }
type Row = { x: string; y: string; w: string; h: string };

function parse(value: string): Row[] {
  if (!value) return [];
  return value.split("|").map((seg) => {
    const [x = "", y = "", w = "", h = ""] = seg.split(",");
    return { x, y, w, h };
  });
}

function serialize(rows: Row[]): string {
  return rows.filter((r) => r.x || r.y || r.w || r.h)
    .map((r) => `${r.x},${r.y},${r.w},${r.h}`).join("|");
}

export function RegionBuilder({ spec, value, onChange }: Props) {
  const [rows, setRows] = useState<Row[]>(parse(value));
  const update = (next: Row[]) => { setRows(next); onChange(serialize(next)); };
  const setField = (i: number, k: keyof Row, v: string) =>
    update(rows.map((r, idx) => idx === i ? { ...r, [k]: v } : r));
  return (
    <div className="text-sm">
      <span className="text-gray-700">{spec.label}</span>
      {rows.map((r, i) => (
        <div key={i} className="mt-1 flex gap-1">
          {(["x", "y", "w", "h"] as const).map((k) => (
            <input key={k} aria-label={`${spec.flag}-${i}-${k}`} type="number" value={r[k]}
              onChange={(e) => setField(i, k, e.target.value)}
              className="w-16 rounded-md border border-gray-200 px-2 py-1 text-sm" placeholder={k} />
          ))}
          <button type="button" onClick={() => update(rows.filter((_, idx) => idx !== i))}
            className="px-2 text-gray-400 hover:text-gray-700">✕</button>
        </div>
      ))}
      <button type="button" onClick={() => update([...rows, { x: "", y: "", w: "", h: "" }])}
        className="mt-1 text-xs text-brand-teal hover:underline">+ Add region</button>
    </div>
  );
}
