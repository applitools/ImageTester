import type { OptionSpec } from "../../lib/optionsSchema";

interface Props { spec: OptionSpec; value: string; onChange: (v: string) => void; }

export function ProxyControl({ spec, value, onChange }: Props) {
  const [url = "", user = "", pass = ""] = value.split(",");
  const emit = (u: string, n: string, p: string) =>
    onChange([u, n, p].slice(0, p ? 3 : n ? 2 : u ? 1 : 0).join(","));
  return (
    <div className="text-sm">
      <span className="text-gray-700">{spec.label}</span>
      <input aria-label="proxy-url" value={url} placeholder="url" onChange={(e) => emit(e.target.value, user, pass)} className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm" />
      <input aria-label="proxy-user" value={user} placeholder="user" onChange={(e) => emit(url, e.target.value, pass)} className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm" />
      <input aria-label="proxy-pass" type="password" value={pass} placeholder="password" onChange={(e) => emit(url, user, e.target.value)} className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm" />
    </div>
  );
}
