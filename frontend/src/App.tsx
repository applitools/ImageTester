import { useEffect, useReducer, useRef, useState } from "react";
import { SetupCard } from "./components/SetupCard";
import { StatusPane } from "./components/StatusPane";
import { api } from "./lib/api";
import { connectSse } from "./lib/sse";
import type { MatchLevel, RunStateSnapshot, SseEvent, TestRow } from "./types";

type Action =
  | { type: "set"; snapshot: RunStateSnapshot }
  | { type: "sse"; event: SseEvent };

function reducer(state: RunStateSnapshot, action: Action): RunStateSnapshot {
  if (action.type === "set") return action.snapshot;
  const e = action.event;
  if (state.kind !== "running") return state;
  switch (e.type) {
    case "test-started":
      return { ...state, tests: [...state.tests, { name: e.name, status: "running" }] };
    case "test-finished": {
      const exists = state.tests.some((t) => t.name === e.name);
      const tests: TestRow[] = exists
        ? state.tests.map((t) => t.name === e.name ? { ...t, status: e.status, durationMs: e.durationMs, dashboardUrl: e.dashboardUrl } : t)
        : [...state.tests, { name: e.name, status: e.status, durationMs: e.durationMs, dashboardUrl: e.dashboardUrl }];
      return { ...state, tests };
    }
    case "run-finished":
      return { kind: "done", runId: state.runId, tests: state.tests, passed: e.passed, failed: e.failed, durationMs: e.durationMs };
    default:
      return state;
  }
}

export function App() {
  const [snapshot, dispatch] = useReducer(reducer, { kind: "idle" } as RunStateSnapshot);
  const [hasKey, setHasKey] = useState(false);
  const [sourcePath, setSourcePath] = useState("");
  const [matchLevel, setMatchLevel] = useState<MatchLevel>("Strict");
  const [logLines, setLogLines] = useState<string[]>([]);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    api.hasApiKey().then((r) => setHasKey(r.hasKey)).catch(() => {});
    api.status().then((s) => dispatch({ type: "set", snapshot: s as RunStateSnapshot })).catch(() => {});
    esRef.current = connectSse((e) => {
      if (e.type === "log-line") setLogLines((l) => [...l, e.text]);
      else dispatch({ type: "sse", event: e });
    });
    return () => esRef.current?.close();
  }, []);

  return (
    <div className="mx-auto max-w-6xl px-6 py-8">
      <header className="mb-6 flex items-center gap-2">
        <span className="inline-block h-7 w-7 rounded-lg bg-gradient-to-br from-brand-teal to-brand-tealDark"></span>
        <span className="font-semibold text-brand-navy">ImageTester</span>
        <span className="text-xs text-gray-500">v3.10</span>
      </header>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <SetupCard
          hasKey={hasKey}
          sourcePath={sourcePath}
          matchLevel={matchLevel}
          running={snapshot.kind === "running"}
          onSetKey={async (v) => { if (v) { await api.setApiKey(v); setHasKey(true); } }}
          onChoosePath={async (t) => { const r = await api.choosePath(t); if (r.path) setSourcePath(r.path); }}
          onMatchLevel={setMatchLevel}
          onRun={async () => {
            setLogLines([]);
            const r = await api.run(sourcePath, matchLevel);
            dispatch({ type: "set", snapshot: { kind: "running", runId: r.runId, tests: [] } });
          }}
          onCancel={() => api.cancel()}
        />
        <StatusPane state={snapshot} logLines={logLines} />
      </div>
    </div>
  );
}
