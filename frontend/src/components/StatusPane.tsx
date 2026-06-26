import { useEffect, useState } from "react";
import type { RunStateSnapshot } from "../types";
import { TestRow } from "./TestRow";

interface Props {
  state: RunStateSnapshot;
  logLines: string[];
}

const TICK_INTERVAL_MS = 500;

export function StatusPane({ state, logLines }: Props) {
  const [showLog, setShowLog] = useState(true);
  const [now, setNow] = useState(() => Date.now());

  const hasRunning = state.kind === "running" && state.tests.some((t) => t.status === "running");
  useEffect(() => {
    if (!hasRunning) return;
    const id = window.setInterval(() => setNow(Date.now()), TICK_INTERVAL_MS);
    return () => window.clearInterval(id);
  }, [hasRunning]);

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-6">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-xs uppercase tracking-wider text-gray-500">Status</h2>
        <Header state={state} />
      </div>

      {state.kind === "idle" && (
        <p className="text-sm text-gray-500">Pick a source and click Run to start.</p>
      )}

      {state.kind !== "idle" && (
        <div className="space-y-0.5">
          {state.tests.map((t) => <TestRow key={t.name} row={t} now={now} />)}
        </div>
      )}

      {state.kind === "done" && state.outputDir && (
        <div className="rounded-md bg-gray-50 p-3 text-sm">
          <div className="font-medium text-gray-700">Cleaned {state.fileCount ?? 0} PDF(s)</div>
          <div className="mt-1 truncate text-gray-500">{state.outputDir}</div>
        </div>
      )}

      <button type="button" onClick={() => setShowLog(!showLog)} className="mt-4 text-xs text-gray-500 hover:text-gray-800">
        {showLog ? "▾" : "▸"} Show log
      </button>
      {showLog && (
        <pre className="mt-2 max-h-64 overflow-auto rounded-md bg-gray-900 p-3 text-xs text-emerald-200">
          {logLines.join("")}
        </pre>
      )}
    </div>
  );
}

function Header({ state }: { state: RunStateSnapshot }) {
  if (state.kind === "idle") return <span className="text-xs text-gray-400">Idle</span>;
  if (state.kind === "running") return <span className="text-xs text-amber-700">Running {state.tests.filter((t) => t.status !== "running").length} / {state.tests.length}</span>;
  return <span className="text-xs">{state.passed} passed{state.failed > 0 && <>, <span className="text-rose-700">{state.failed} failed</span></>}</span>;
}
