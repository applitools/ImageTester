import type { OptionSpec } from "../../lib/optionsSchema";

interface Props { spec: OptionSpec; value: string; onChange: (v: string) => void; }

export function ImageCutControl({ spec, value, onChange }: Props) {
  const parts = value.split(",");
  const fields = ["header", "footer", "left", "right"] as const;
  const get = (i: number) => parts[i] ?? "";
  const set = (i: number, v: string) => {
    const next = [get(0), get(1), get(2), get(3)];
    next[i] = v;
    onChange(next.join(","));
  };
  return (
    <div className="text-sm">
      <span className="text-gray-700">{spec.label}</span>
      <div className="mt-1 flex gap-1">
        {fields.map((f, i) => (
          <input key={f} aria-label={`ic-${f}`} type="number" value={get(i)} placeholder={f}
            onChange={(e) => set(i, e.target.value)} className="w-16 rounded-md border border-gray-200 px-2 py-1 text-sm" />
        ))}
      </div>
    </div>
  );
}
