import { useEffect, useRef, useState } from "react";
import type { RunStateSnapshot } from "../types";
import { TestRow } from "./TestRow";

interface Props {
  state: RunStateSnapshot;
  logLines: string[];
}

type Tab = "tests" | "log";

const TICK_INTERVAL_MS = 500;
const STICK_TO_BOTTOM_THRESHOLD_PX = 40;
// Fills the flex column at md+ (the card's own height is pinned to the Setup
// column by App.tsx); below md the card flows normally, so the panel falls back
// to a fixed cap instead of an unbounded height.
const PANEL_CLASS_NAME = "flex-1 min-h-0 max-h-[60vh] overflow-y-auto md:max-h-none";

function isNearBottom(el: HTMLElement): boolean {
  return el.scrollHeight - el.scrollTop - el.clientHeight <= STICK_TO_BOTTOM_THRESHOLD_PX;
}

export function StatusPane({ state, logLines }: Props) {
  const [activeTab, setActiveTab] = useState<Tab>("tests");
  const [now, setNow] = useState(() => Date.now());
  const testsPanelRef = useRef<HTMLDivElement | null>(null);
  const logPanelRef = useRef<HTMLDivElement | null>(null);
  const testCount = state.kind === "idle" ? 0 : state.tests.length;
  // Each panel defaults true and captures its own near-bottom state before React
  // appends content, so scrolling up to inspect earlier output is never overridden.
  const testsWasNearBottomRef = useRef(true);
  const logWasNearBottomRef = useRef(true);

  const handleTestsScroll = () => {
    const el = testsPanelRef.current;
    if (el) testsWasNearBottomRef.current = isNearBottom(el);
  };

  const handleLogScroll = () => {
    const el = logPanelRef.current;
    if (el) logWasNearBottomRef.current = isNearBottom(el);
  };

  const runId = state.kind === "running" ? state.runId : null;
  // A new run starts fresh content in both tabs; re-arm auto-follow regardless of
  // where the user left either tab's scroll position on the previous run.
  useEffect(() => {
    if (runId === null) return;
    testsWasNearBottomRef.current = true;
    logWasNearBottomRef.current = true;
  }, [runId]);

  // activeTab is a dependency (not just a guard) so switching tabs re-checks the
  // freshly mounted panel — it starts at scrollTop 0 and needs to jump to the
  // bottom immediately if that panel's content should already be following.
  useEffect(() => {
    const el = testsPanelRef.current;
    if (!el || state.kind !== "running") return;
    if (testsWasNearBottomRef.current) el.scrollTop = el.scrollHeight;
  }, [testCount, state.kind, activeTab]);

  useEffect(() => {
    const el = logPanelRef.current;
    if (!el || state.kind !== "running") return;
    if (logWasNearBottomRef.current) el.scrollTop = el.scrollHeight;
  }, [logLines.length, state.kind, activeTab]);

  const hasRunning = state.kind === "running" && state.tests.some((t) => t.status === "running");
  useEffect(() => {
    if (!hasRunning) return;
    const id = window.setInterval(() => setNow(Date.now()), TICK_INTERVAL_MS);
    return () => window.clearInterval(id);
  }, [hasRunning]);

  return (
    <div className="card flex h-full flex-col p-6">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-xs uppercase tracking-wider text-gray-500">Status</h2>
        <Header state={state} />
      </div>

      <div role="tablist" className="mb-3 flex gap-1 rounded-lg bg-gray-100 p-1 text-sm">
        <TabButton id="status-tests-tab" panelId="status-tests-panel" label="Tests" active={activeTab === "tests"} onClick={() => setActiveTab("tests")} />
        <TabButton id="status-log-tab" panelId="status-log-panel" label="Log" active={activeTab === "log"} onClick={() => setActiveTab("log")} />
      </div>

      {activeTab === "tests" && (
        <div
          ref={testsPanelRef}
          onScroll={handleTestsScroll}
          role="tabpanel"
          id="status-tests-panel"
          aria-labelledby="status-tests-tab"
          aria-label="Test results"
          tabIndex={0}
          className={PANEL_CLASS_NAME}
        >
          {state.kind === "idle" && (
            <p className="text-sm text-gray-500">Pick a source and click Run to start.</p>
          )}

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
        </div>
      )}

      {activeTab === "log" && (
        <div
          ref={logPanelRef}
          onScroll={handleLogScroll}
          role="tabpanel"
          id="status-log-panel"
          aria-labelledby="status-log-tab"
          aria-label="Log"
          tabIndex={0}
          className={PANEL_CLASS_NAME}
        >
          <pre className="h-full overflow-x-hidden whitespace-pre-wrap break-words rounded-lg bg-gray-900 p-3 font-mono text-xs text-emerald-200">
            {logLines.join("")}
          </pre>
        </div>
      )}
    </div>
  );
}

function TabButton({ id, panelId, label, active, onClick }: { id: string; panelId: string; label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      role="tab"
      id={id}
      aria-selected={active}
      aria-controls={panelId}
      onClick={onClick}
      className={`flex-1 rounded-md px-2 py-1 transition-colors ${active ? "bg-white shadow-sm" : "text-gray-500 hover:text-gray-700"}`}
    >
      {label}
    </button>
  );
}

function Header({ state }: { state: RunStateSnapshot }) {
  if (state.kind === "idle") return <span className="text-xs text-gray-400">Idle</span>;
  if (state.kind === "running") return <span className="text-xs text-amber-700">Running {state.tests.filter((t) => t.status !== "running").length} / {state.tests.length}</span>;
  // Watermark-out run: the cleaned-files summary below already communicates the outcome
  if (state.outputDir) return null;
  return <span className="text-xs">{state.passed} passed{state.failed > 0 && <>, <span className="text-rose-700">{state.failed} failed</span></>}</span>;
}
