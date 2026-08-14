import { selectOptions, type OptionSpec } from "../../lib/optionsSchema";

interface Props {
  spec: OptionSpec;
  value: unknown;
  onChange: (v: unknown) => void;
}

export function ScalarControl({ spec, value, onChange }: Props) {
  const id = `opt-${spec.flag}`;
  if (spec.type === "checkbox") {
    return (
      <label htmlFor={id} className="flex items-center gap-2 text-sm text-gray-700">
        <input id={id} type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />
        {spec.label}
      </label>
    );
  }
  if (spec.type === "select") {
    return (
      <label htmlFor={id} className="block text-sm">
        <span className="text-gray-700">{spec.label}</span>
        <select id={id} value={String(value ?? "")} onChange={(e) => onChange(e.target.value)} className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm">
          {selectOptions(spec).map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
        </select>
      </label>
    );
  }
  const inputType = spec.type === "password" ? "password" : spec.type === "number" ? "number" : "text";
  return (
    <label htmlFor={id} className="block text-sm">
      <span className="text-gray-700">{spec.label}</span>
      <input id={id} type={inputType} value={String(value ?? "")}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-brand-teal focus:outline-none" />
    </label>
  );
}
