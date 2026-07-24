import { useEffect, useRef, useState } from "react";
import type { RunStateSnapshot } from "../types";
import { TestRow } from "./TestRow";

interface Props {
  state: RunStateSnapshot;
  logLines: string[];
}

const TICK_INTERVAL_MS = 500;
const STICK_TO_BOTTOM_THRESHOLD_PX = 40;

export function StatusPane({ state, logLines }: Props) {
  const [showLog, setShowLog] = useState(true);
  const [now, setNow] = useState(() => Date.now());
  const listRef = useRef<HTMLDivElement | null>(null);
  const testCount = state.kind === "idle" ? 0 : state.tests.length;
  // Defaults true and is captured before React appends a row, so scrolling up to
  // inspect an earlier test is never overridden.
  const wasNearBottomRef = useRef(true);

  const handleListScroll = () => {
    const el = listRef.current;
    if (!el) return;
    wasNearBottomRef.current =
      el.scrollHeight - el.scrollTop - el.clientHeight <= STICK_TO_BOTTOM_THRESHOLD_PX;
  };

  const runId = state.kind === "running" ? state.runId : null;
  // A new run starts a fresh list; re-arm auto-follow regardless of where the
  // user left the previous run's scroll position.
  useEffect(() => {
    if (runId !== null) wasNearBottomRef.current = true;
  }, [runId]);

  useEffect(() => {
    const el = listRef.current;
    if (!el || state.kind !== "running") return;
    if (wasNearBottomRef.current) el.scrollTop = el.scrollHeight;
  }, [testCount, state.kind, logLines.length]);

  const hasRunning = state.kind === "running" && state.tests.some((t) => t.status === "running");
  useEffect(() => {
    if (!hasRunning) return;
    const id = window.setInterval(() => setNow(Date.now()), TICK_INTERVAL_MS);
    return () => window.clearInterval(id);
  }, [hasRunning]);

  return (
    <div className="card p-6">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-xs uppercase tracking-wider text-gray-500">Status</h2>
        <Header state={state} />
      </div>

      {state.kind === "idle" && (
        <p className="text-sm text-gray-500">Pick a source and click Run to start.</p>
      )}

      <div ref={listRef} onScroll={handleListScroll} className="max-h-[60vh] overflow-y-auto" tabIndex={0} aria-label="Test results">
        {state.kind !== "idle" && (
          <div className="space-y-0.5">
            {state.tests.map((t) => <TestRow key={t.name} row={t} now={now} />)}
          </div>
        )}

        {state.kind === "done" && state.outputDir && (
          <div className="rounded-lg bg-gray-50 p-3 text-sm">
            <div className="font-medium text-gray-700">Cleaned {state.fileCount ?? 0} PDF(s)</div>
            <div className="mt-1 truncate text-gray-500">{state.outputDir}</div>
          </div>
        )}

        <button type="button" onClick={() => setShowLog(!showLog)} className="mt-4 text-xs text-gray-500 transition-colors hover:text-gray-800">
          {showLog ? "▾" : "▸"} Show log
        </button>
        {showLog && (
          <pre className="mt-2 overflow-x-hidden whitespace-pre-wrap break-words rounded-lg bg-gray-900 p-3 font-mono text-xs text-emerald-200">
            {logLines.join("")}
          </pre>
        )}
      </div>
    </div>
  );
}

function Header({ state }: { state: RunStateSnapshot }) {
  if (state.kind === "idle") return <span className="text-xs text-gray-400">Idle</span>;
  if (state.kind === "running") return <span className="text-xs text-amber-700">Running {state.tests.filter((t) => t.status !== "running").length} / {state.tests.length}</span>;
  // Watermark-out run: the cleaned-files summary below already communicates the outcome
  if (state.outputDir) return null;
  return <span className="text-xs">{state.passed} passed{state.failed > 0 && <>, <span className="text-rose-700">{state.failed} failed</span></>}</span>;
}
