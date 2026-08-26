import { DocDropZone } from "./DocDropZone";
import { PrecheckPanel } from "./PrecheckPanel";
import type { MatchLevel, PrecheckFinding } from "../types";

interface Props {
  hasKey: boolean;
  sourcePath: string;
  matchLevel: MatchLevel;
  running: boolean;
  cancelling?: boolean;
  optionsCount: number;
  drawerOpen: boolean;
  compareMode: boolean;
  doc1Path: string;
  doc2Path: string;
  forcedName: string;
  precheckFindings: PrecheckFinding[];
  onSetKey: (value: string) => void;
  onChoosePath: (type: "file" | "folder") => void;
  onChooseDoc1: () => void;
  onChooseDoc2: () => void;
  onDropDoc1: (file: File) => void;
  onDropDoc2: (file: File) => void;
  doc1UploadError?: string;
  doc2UploadError?: string;
  onForcedNameChange: (value: string) => void;
  onToggleCompareMode: () => void;
  onMatchLevel: (l: MatchLevel) => void;
  onRun: () => void;
  onCancel: () => void;
  onToggleDrawer: () => void;
  onSetMatchSize?: (value: string) => void;
}

export function SetupCard(p: Props) {
  const hasPrecheckError = p.compareMode && p.precheckFindings.some((f) => f.severity === "ERROR");
  const hasPrecheckWarning = p.compareMode && p.precheckFindings.some((f) => f.severity === "WARNING");
  const canRun = !hasPrecheckError && (p.compareMode
    ? p.hasKey && p.doc1Path.length > 0 && p.doc2Path.length > 0 && p.forcedName.trim().length > 0
    : p.hasKey && p.sourcePath.length > 0);
  return (
    <div className="card p-6 space-y-4">
      <h2 className="text-xs uppercase tracking-wider text-gray-500">Setup</h2>

      <label className="block text-sm">
        <span className="text-gray-700">Applitools API key <span className="text-rose-600">*</span></span>
        <input
          type="password"
          placeholder={p.hasKey ? "••••••••••••••••" : "paste your key"}
          onBlur={(e) => p.onSetKey(e.target.value)}
          className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm transition-colors focus:border-brand-teal"
        />
        {p.hasKey && <span className="mt-1 inline-block text-xs text-brand-tealDark">• Saved</span>}
      </label>

      <div className="flex gap-1 rounded-lg bg-gray-100 p-1 text-sm">
        <button type="button" onClick={() => { if (p.compareMode) p.onToggleCompareMode(); }}
          className={`flex-1 rounded-md px-2 py-1 transition-colors ${!p.compareMode ? "bg-white shadow-sm" : "text-gray-500 hover:text-gray-700"}`}>
          Folder/File
        </button>
        <button type="button" onClick={() => { if (!p.compareMode) p.onToggleCompareMode(); }}
          className={`flex-1 rounded-md px-2 py-1 transition-colors ${p.compareMode ? "bg-white shadow-sm" : "text-gray-500 hover:text-gray-700"}`}>
          Compare two documents
        </button>
      </div>

      {p.compareMode ? (
        <div className="space-y-2">
          <div className="grid grid-cols-2 gap-2">
            <DocDropZone label="Doc 1" path={p.doc1Path} uploadError={p.doc1UploadError} onChoose={p.onChooseDoc1} onDropFile={p.onDropDoc1} />
            <DocDropZone label="Doc 2" path={p.doc2Path} uploadError={p.doc2UploadError} onChoose={p.onChooseDoc2} onDropFile={p.onDropDoc2} />
          </div>
          <div>
            <div className="text-sm text-gray-700">Comparison name <span className="text-rose-600">*</span></div>
            <input
              type="text"
              aria-label="Comparison name"
              value={p.forcedName}
              onChange={(e) => p.onForcedNameChange(e.target.value)}
              placeholder="e.g. contract-v2-vs-v3"
              className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm transition-colors focus:border-brand-teal"
            />
            <p className="mt-1 text-xs text-gray-500">
              Doc 1 and Doc 2 must share this name to be compared. Use a unique name each time — reusing one may compare against an older baseline.
            </p>
          </div>
        </div>
      ) : (
        <div>
          <label className="block text-sm text-gray-700">Source <span className="text-rose-600">*</span></label>
          <div className="mt-1 rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-600 truncate">
            {p.sourcePath || "No file or folder chosen"}
          </div>
          <div className="mt-2 flex gap-2">
            <button type="button" onClick={() => p.onChoosePath("file")} className="flex-1 rounded-lg border border-gray-200 bg-gray-50 px-3 py-1.5 text-sm transition-colors hover:bg-gray-100">Choose file…</button>
            <button type="button" onClick={() => p.onChoosePath("folder")} className="flex-1 rounded-lg border border-gray-200 bg-gray-50 px-3 py-1.5 text-sm transition-colors hover:bg-gray-100">Choose folder…</button>
          </div>
        </div>
      )}

      <label className="block text-sm">
        <span className="text-gray-700">Match level</span>
        <select value={p.matchLevel} onChange={(e) => p.onMatchLevel(e.target.value as MatchLevel)} className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm transition-colors focus:border-brand-teal">
          <option value="Strict">Strict</option>
          <option value="Layout">Layout</option>
          <option value="Content">Content</option>
          <option value="Exact">Exact</option>
        </select>
      </label>

      <button type="button" onClick={p.onToggleDrawer}
        className="flex w-full items-center justify-between rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-sm transition-colors hover:bg-gray-100">
        <span>⚙ Options</span>
        <span className="text-xs text-gray-500">{p.optionsCount > 0 ? `${p.optionsCount} set` : "none"}{p.drawerOpen ? " ▴" : " ▾"}</span>
      </button>

      {p.compareMode && (
        <PrecheckPanel
          findings={p.precheckFindings}
          onOpenOptions={() => { if (!p.drawerOpen) p.onToggleDrawer(); }}
          onSetMatchSize={p.onSetMatchSize}
          resetKey={`${p.doc1Path}|${p.doc2Path}`}
        />
      )}

      {p.running ? (
        <button type="button" disabled={p.cancelling} onClick={p.onCancel}
          className="w-full rounded-lg bg-gray-200 py-2.5 font-semibold text-gray-700 transition-colors hover:bg-gray-300 disabled:text-gray-400 disabled:hover:bg-gray-200">
          {p.cancelling ? "Cancelling…" : "Cancel"}
        </button>
      ) : (
        <button type="button" disabled={!canRun} onClick={p.onRun} className="w-full rounded-lg bg-brand-teal py-2.5 font-semibold text-white transition-colors hover:bg-brand-tealDark disabled:bg-gray-200 disabled:text-gray-400">{hasPrecheckWarning ? "Run anyway" : "▶ Run test"}</button>
      )}
    </div>
  );
}
