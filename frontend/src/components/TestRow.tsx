import type { TestRow as Row } from "../types";

interface Props {
  row: Row;
  now: number;
}

export function TestRow({ row, now }: Props) {
  const icon = row.status === "running" ? "⟳" : row.status === "pass" ? "✓" : "✕";
  const tone = row.status === "pass" ? "text-emerald-700" : row.status === "fail" ? "text-rose-700" : "text-gray-600";
  return (
    <div className={`flex items-center justify-between border-b border-gray-100 py-1.5 text-sm ${tone}`}>
      <span><span className="mr-2">{icon}</span>{row.name}</span>
      <span className="text-xs text-gray-500">
        {row.status === "running"
          ? row.startedAtMs ? `${Math.floor((now - row.startedAtMs) / 1000)}s` : "running"
          : row.durationMs != null ? `${row.durationMs}ms` : ""}
        {row.dashboardUrl && <a className="ml-2 underline" href={row.dashboardUrl} target="_blank" rel="noreferrer">View ↗</a>}
      </span>
    </div>
  );
}
