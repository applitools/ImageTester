import { useEffect, useReducer, useRef, useState } from "react";
import { SetupCard } from "./components/SetupCard";
import { StatusPane } from "./components/StatusPane";
import { OptionsDrawer } from "./components/OptionsDrawer";
import { api } from "./lib/api";
import { connectSse } from "./lib/sse";
import { getVersion } from "./lib/version";
import { loadOptions, saveOptions, countNonDefault, toRunPayload } from "./lib/options";
import type { RunOptions } from "./lib/options";
import type { MatchLevel, RunStateSnapshot, SseEvent, TestRow } from "./types";

const LAST_SOURCE_PATH_KEY = "imagetester.lastSourcePath";

function readLastSourcePath(): string {
  try { return window.localStorage.getItem(LAST_SOURCE_PATH_KEY) ?? ""; } catch { return ""; }
}

function writeLastSourcePath(value: string) {
  try { window.localStorage.setItem(LAST_SOURCE_PATH_KEY, value); } catch { /* private mode / disabled */ }
}

type Action =
  | { type: "set"; snapshot: RunStateSnapshot }
  | { type: "sse"; event: SseEvent };

function reducer(state: RunStateSnapshot, action: Action): RunStateSnapshot {
  if (action.type === "set") return action.snapshot;
  const e = action.event;
  if (state.kind !== "running") return state;
  switch (e.type) {
    case "test-started":
      return { ...state, tests: [...state.tests, { name: e.name, status: "running", startedAtMs: Date.now() }] };
    case "test-finished": {
      const exists = state.tests.some((t) => t.name === e.name);
      const tests: TestRow[] = exists
        ? state.tests.map((t) => t.name === e.name ? { ...t, status: e.status, durationMs: e.durationMs, dashboardUrl: e.dashboardUrl } : t)
        : [...state.tests, { name: e.name, status: e.status, durationMs: e.durationMs, dashboardUrl: e.dashboardUrl }];
      return { ...state, tests };
    }
    case "run-finished":
      return { kind: "done", runId: state.runId, tests: state.tests, passed: e.passed, failed: e.failed, durationMs: e.durationMs };
    case "watermark-cleaned":
      return { kind: "done", runId: state.runId, tests: state.tests, passed: 0, failed: 0, durationMs: e.durationMs, outputDir: e.outputDir, fileCount: e.fileCount };
    default:
      return state;
  }
}

export function App() {
  const [snapshot, dispatch] = useReducer(reducer, { kind: "idle" } as RunStateSnapshot);
  const [hasKey, setHasKey] = useState(false);
  const [sourcePath, setSourcePath] = useState(readLastSourcePath);
  const [options, setOptions] = useState<RunOptions>(loadOptions);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [logLines, setLogLines] = useState<string[]>([]);
  const [runError, setRunError] = useState<string | null>(null);
  const esRef = useRef<EventSource | null>(null);

  const setOption = (flag: string, value: unknown) => {
    setOptions((prev) => { const next = { ...prev, [flag]: value }; saveOptions(next); return next; });
  };

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
      <header className="mb-6">
        <div className="flex items-center gap-2">
          <span className="inline-block h-7 w-7 rounded-lg bg-gradient-to-br from-brand-teal to-brand-tealDark"></span>
          <span className="font-semibold text-brand-navy">ImageTester</span>
          {getVersion() && <span className="text-xs text-gray-500">v{getVersion()}</span>}
          <a href="https://github.com/applitools/ImageTester#readme" target="_blank" rel="noreferrer"
            className="ml-auto text-xs text-brand-teal hover:underline">Docs ↗</a>
        </div>
        <p className="mt-1 text-sm text-gray-500">
          Visual regression testing for images, PDFs &amp; documents — compares them against baselines in Applitools Eyes.
        </p>
      </header>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <div className="space-y-4">
          <SetupCard
            hasKey={hasKey}
            sourcePath={sourcePath}
            matchLevel={(options.ml as MatchLevel) ?? "Strict"}
            running={snapshot.kind === "running"}
            optionsCount={countNonDefault(options)}
            drawerOpen={drawerOpen}
            onToggleDrawer={() => setDrawerOpen((v) => !v)}
            onSetKey={async (v) => { if (v) { await api.setApiKey(v); setHasKey(true); } }}
            onChoosePath={async (t) => { const r = await api.choosePath(t, sourcePath || undefined); if (r.path) { setSourcePath(r.path); writeLastSourcePath(r.path); } }}
            onMatchLevel={(l) => setOption("ml", l)}
            onRun={async () => {
              setRunError(null);
              setLogLines([]);
              try {
                const r = await api.run(toRunPayload(sourcePath, options));
                dispatch({ type: "set", snapshot: { kind: "running", runId: r.runId, tests: [] } });
              } catch (err) {
                setRunError(err instanceof Error ? err.message : String(err));
              }
            }}
            onCancel={() => {
              api.cancel();
              // Wipe the right pane immediately — the user expects a clean slate, not a frozen final state.
              // The reducer ignores SSE events when not in "running", so any in-flight test-finished is dropped.
              dispatch({ type: "set", snapshot: { kind: "idle" } });
              setLogLines([]);
            }}
          />
          {runError && <p role="alert" className="text-sm text-rose-700">{runError}</p>}
        </div>
        <StatusPane state={snapshot} logLines={logLines} />
      </div>
      {drawerOpen && (
        <div className="mt-6">
          <OptionsDrawer options={options} onChange={setOption} onClose={() => setDrawerOpen(false)} />
        </div>
      )}
    </div>
  );
}
