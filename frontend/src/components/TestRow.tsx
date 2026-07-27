import { useState } from "react";
import type { TestRow as Row, TestStatus } from "../types";
import { getToken } from "../lib/token";

interface Props {
  row: Row;
  now: number;
}

// Labels mirror the Applitools Eyes result statuses; "Mismatch" is the dashboard
// term for Unresolved (diffs awaiting review).
const STATUS_LABEL: Record<Exclude<TestStatus, "running">, string> = {
  passed: "Passed",
  mismatch: "Mismatch",
  failed: "Failed",
  new: "New",
  aborted: "Aborted",
  error: "Error",
  cancelled: "Cancelled",
};

const STATUS_TONE: Record<TestStatus, string> = {
  running: "text-gray-600",
  passed: "text-emerald-700",
  mismatch: "text-rose-700",
  failed: "text-rose-700",
  error: "text-rose-700",
  new: "text-brand-tealDark",
  aborted: "text-gray-600",
  cancelled: "text-gray-600",
};

function previewUrl(path: string): string {
  return `/api/preview?path=${encodeURIComponent(path)}&token=${encodeURIComponent(getToken())}`;
}

export function TestRow({ row, now }: Props) {
  const [preview1Failed, setPreview1Failed] = useState(false);
  const [preview2Failed, setPreview2Failed] = useState(false);
  const marker = row.status === "running" ? "⟳" : STATUS_LABEL[row.status];
  const tone = STATUS_TONE[row.status];
  const showPreview1 = row.previewPath && !preview1Failed;
  const showPreview2 = row.doc2PreviewPath && !preview2Failed;
  return (
    <div className={`flex items-center justify-between gap-2 border-b border-gray-100 py-2 text-sm ${tone}`}>
      <span className="flex min-w-0 items-center gap-3">
        <span className={`flex-shrink-0 ${row.status === "running" ? "" : "text-xs font-semibold uppercase tracking-wide"}`}>{marker}</span>
        {showPreview1 && (
          <img
            src={previewUrl(row.previewPath!)}
            alt=""
            className="h-24 w-24 flex-shrink-0 rounded-md border border-gray-200 bg-white object-cover"
            onError={() => setPreview1Failed(true)}
          />
        )}
        {showPreview2 && (
          <img
            src={previewUrl(row.doc2PreviewPath!)}
            alt=""
            className="h-24 w-24 flex-shrink-0 rounded-md border border-gray-200 bg-white object-cover"
            onError={() => setPreview2Failed(true)}
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
