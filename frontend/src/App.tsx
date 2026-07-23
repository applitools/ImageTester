import { useEffect, useReducer, useRef, useState } from "react";
import applitoolsLogo from "./assets/applitools-logo.png";
import { SetupCard } from "./components/SetupCard";
import { UpdateBanner } from "./components/UpdateBanner";
import { StatusPane } from "./components/StatusPane";
import { OptionsDrawer } from "./components/OptionsDrawer";
import { api } from "./lib/api";
import { connectSse } from "./lib/sse";
import type { SseHandle } from "./lib/sse";
import { getVersion } from "./lib/version";
import { loadOptions, saveOptions, countNonDefault, toRunPayload, toComparePayload } from "./lib/options";
import type { RunOptions } from "./lib/options";
import type { MatchLevel, RunStateSnapshot, SseEvent, TestRow, PrecheckFinding } from "./types";

const LAST_SOURCE_PATH_KEY = "imagetester.lastSourcePath";
const PRECHECK_DEBOUNCE_MS = 300;

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
  // The server emits run-started before /api/run even responds, so this — not the
  // optimistic dispatch in onRun — is the authoritative idle → running transition.
  if (e.type === "run-started") return { kind: "running", runId: e.runId, tests: [] };
  if (state.kind !== "running") return state;
  switch (e.type) {
    case "test-started":
      return { ...state, tests: [...state.tests, { name: e.name, status: "running", startedAtMs: Date.now(), previewPath: e.previewPath, doc2PreviewPath: e.doc2PreviewPath }] };
    case "test-finished": {
      const exists = state.tests.some((t) => t.name === e.name);
      const tests: TestRow[] = exists
        ? state.tests.map((t) => t.name === e.name ? { ...t, status: e.status, durationMs: e.durationMs, dashboardUrl: e.dashboardUrl, previewPath: e.previewPath ?? t.previewPath, doc2PreviewPath: e.doc2PreviewPath ?? t.doc2PreviewPath } : t)
        : [...state.tests, { name: e.name, status: e.status, durationMs: e.durationMs, dashboardUrl: e.dashboardUrl, previewPath: e.previewPath, doc2PreviewPath: e.doc2PreviewPath }];
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
  const [compareMode, setCompareMode] = useState(false);
  const [doc1Path, setDoc1Path] = useState("");
  const [doc2Path, setDoc2Path] = useState("");
  const [options, setOptions] = useState<RunOptions>(loadOptions);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [logLines, setLogLines] = useState<string[]>([]);
  const [runError, setRunError] = useState<string | null>(null);
  const [doc1UploadError, setDoc1UploadError] = useState<string | null>(null);
  const [doc2UploadError, setDoc2UploadError] = useState<string | null>(null);
  const [precheckFindings, setPrecheckFindings] = useState<PrecheckFinding[]>([]);
  const precheckSeq = useRef(0);
  const esRef = useRef<SseHandle | null>(null);

  const setOption = (flag: string, value: unknown) => {
    setOptions((prev) => { const next = { ...prev, [flag]: value }; saveOptions(next); return next; });
  };

  const dropDoc = async (slot: 1 | 2, file: File) => {
    const setPath = slot === 1 ? setDoc1Path : setDoc2Path;
    const setError = slot === 1 ? setDoc1UploadError : setDoc2UploadError;
    setError(null);
    try {
      const r = await api.upload(file);
      setPath(r.path);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  useEffect(() => {
    api.hasApiKey().then((r) => setHasKey(r.hasKey)).catch(() => {});
    api.status().then((s) => dispatch({ type: "set", snapshot: s as RunStateSnapshot })).catch(() => {});
    esRef.current = connectSse(
      (e) => {
        if (e.type === "log-line") setLogLines((l) => [...l, e.text]);
        else dispatch({ type: "sse", event: e });
      },
      // Re-sync on every (re)connect — events emitted while the stream was down are gone,
      // so the snapshot is the only way to catch up.
      () => api.status().then((s) => dispatch({ type: "set", snapshot: s as RunStateSnapshot })).catch(() => {}),
    );
    return () => esRef.current?.close();
  }, []);

  useEffect(() => {
    if (!compareMode || !doc1Path || !doc2Path) {
      // Bump the sequence so a response already in flight from a prior doc1/doc2/compareMode
      // combination can't win the race and repopulate findings we just cleared.
      precheckSeq.current++;
      setPrecheckFindings([]);
      return;
    }
    const seq = ++precheckSeq.current;
    const timer = setTimeout(() => {
      api.precheckCompare(doc1Path, doc2Path, options)
        .then((r) => { if (seq === precheckSeq.current) setPrecheckFindings(r.findings); })
        .catch(() => { if (seq === precheckSeq.current) setPrecheckFindings([]); });
    }, PRECHECK_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [compareMode, doc1Path, doc2Path, options]);

  return (
    <div className="mx-auto max-w-6xl px-6 py-10">
      <header className="mb-8">
        <div className="flex items-center gap-2.5">
          <img src={applitoolsLogo} alt="Applitools" className="h-7 w-7" />
          <span className="text-[15px] font-semibold tracking-tight text-brand-navy">ImageTester</span>
          {getVersion() && (
            <span className="rounded-full bg-gray-100 px-2 py-0.5 font-mono text-[11px] tabular-nums tracking-tight text-gray-500">
              v{getVersion()}
            </span>
          )}
          <a href="https://github.com/applitools/ImageTester#readme" target="_blank" rel="noreferrer"
            className="ml-auto text-xs font-medium text-brand-teal transition-colors hover:text-brand-tealDark">Docs ↗</a>
        </div>
        <p className="mt-2 text-sm leading-relaxed text-gray-500">
          Visual regression testing for images, PDFs &amp; documents — compares them against baselines in Applitools Eyes.
        </p>
      </header>
      <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
        <div className="space-y-4">
          <UpdateBanner />
          <SetupCard
            hasKey={hasKey}
            sourcePath={sourcePath}
            matchLevel={(options.ml as MatchLevel) ?? "Strict"}
            running={snapshot.kind === "running"}
            optionsCount={countNonDefault(options)}
            drawerOpen={drawerOpen}
            compareMode={compareMode}
            doc1Path={doc1Path}
            doc2Path={doc2Path}
            forcedName={(options.fn as string) ?? ""}
            precheckFindings={precheckFindings}
            onForcedNameChange={(v) => setOption("fn", v)}
            onToggleCompareMode={() => setCompareMode((v) => !v)}
            onChooseDoc1={async () => { const r = await api.choosePath("file", doc1Path || undefined); if (r.path) { setDoc1Path(r.path); setDoc1UploadError(null); } }}
            onChooseDoc2={async () => { const r = await api.choosePath("file", doc2Path || undefined); if (r.path) { setDoc2Path(r.path); setDoc2UploadError(null); } }}
            onDropDoc1={(f) => dropDoc(1, f)}
            onDropDoc2={(f) => dropDoc(2, f)}
            doc1UploadError={doc1UploadError ?? undefined}
            doc2UploadError={doc2UploadError ?? undefined}
            onToggleDrawer={() => setDrawerOpen((v) => !v)}
            onSetKey={async (v) => { if (v) { await api.setApiKey(v); setHasKey(true); } }}
            onChoosePath={async (t) => { const r = await api.choosePath(t, sourcePath || undefined); if (r.path) { setSourcePath(r.path); writeLastSourcePath(r.path); } }}
            onMatchLevel={(l) => setOption("ml", l)}
            onRun={async () => {
              setRunError(null);
              setLogLines([]);
              try {
                const payload = compareMode
                  ? toComparePayload(doc1Path, doc2Path, options)
                  : toRunPayload(sourcePath, options);
                const r = await api.run(payload);
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
          <OptionsDrawer options={options} onChange={setOption} onClose={() => setDrawerOpen(false)} compareMode={compareMode} />
        </div>
      )}
    </div>
  );
}
