import { useState } from "react";
import type { TestRow as Row } from "../types";
import { getToken } from "../lib/token";

interface Props {
  row: Row;
  now: number;
}

export function TestRow({ row, now }: Props) {
  const [previewFailed, setPreviewFailed] = useState(false);
  const icon = row.status === "running" ? "⟳" : row.status === "pass" ? "✓" : "✕";
  const tone = row.status === "pass" ? "text-emerald-700" : row.status === "fail" ? "text-rose-700" : "text-gray-600";
  const showPreview = row.previewPath && !previewFailed;
  return (
    <div className={`flex items-center justify-between gap-2 border-b border-gray-100 py-2 text-sm ${tone}`}>
      <span className="flex min-w-0 items-center gap-3">
        <span className="flex-shrink-0">{icon}</span>
        {showPreview && (
          <img
            src={`/api/preview?path=${encodeURIComponent(row.previewPath!)}&token=${encodeURIComponent(getToken())}`}
            alt=""
            className="h-24 w-24 flex-shrink-0 rounded-md border border-gray-200 bg-white object-cover"
            onError={() => setPreviewFailed(true)}
          />
        )}
        <span className="truncate">{row.name}</span>
      </span>
      <span className="flex flex-shrink-0 items-center text-xs text-gray-500">
        {row.status === "running"
          ? row.startedAtMs ? `${Math.floor((now - row.startedAtMs) / 1000)}s` : "running"
          : row.durationMs != null ? `${row.durationMs}ms` : ""}
        {row.dashboardUrl && <a className="ml-2 underline" href={row.dashboardUrl} target="_blank" rel="noreferrer">View ↗</a>}
      </span>
    </div>
  );
}
