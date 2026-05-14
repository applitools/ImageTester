import type { MatchLevel } from "../types";

interface Props {
  hasKey: boolean;
  sourcePath: string;
  matchLevel: MatchLevel;
  running: boolean;
  onSetKey: (value: string) => void;
  onChoosePath: (type: "file" | "folder") => void;
  onMatchLevel: (l: MatchLevel) => void;
  onRun: () => void;
  onCancel: () => void;
}

export function SetupCard(p: Props) {
  const canRun = p.hasKey && p.sourcePath.length > 0;
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-6 space-y-4">
      <h2 className="text-xs uppercase tracking-wider text-gray-500">Setup</h2>

      <label className="block text-sm">
        <span className="text-gray-700">Applitools API key</span>
        <input
          type="password"
          placeholder={p.hasKey ? "••••••••••••••••" : "paste your key"}
          onBlur={(e) => p.onSetKey(e.target.value)}
          className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-brand-teal focus:outline-none"
        />
        {p.hasKey && <span className="mt-1 inline-block text-xs text-brand-tealDark">• Saved</span>}
      </label>

      <div>
        <label className="block text-sm text-gray-700">Source</label>
        <div className="mt-1 rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 truncate">
          {p.sourcePath || "No file or folder chosen"}
        </div>
        <div className="mt-2 flex gap-2">
          <button type="button" onClick={() => p.onChoosePath("file")} className="flex-1 rounded-md border border-gray-200 bg-gray-50 px-3 py-1.5 text-sm hover:bg-gray-100">Choose file…</button>
          <button type="button" onClick={() => p.onChoosePath("folder")} className="flex-1 rounded-md border border-gray-200 bg-gray-50 px-3 py-1.5 text-sm hover:bg-gray-100">Choose folder…</button>
        </div>
      </div>

      <label className="block text-sm">
        <span className="text-gray-700">Match level</span>
        <select value={p.matchLevel} onChange={(e) => p.onMatchLevel(e.target.value as MatchLevel)} className="mt-1 w-full rounded-md border border-gray-200 px-3 py-2 text-sm">
          <option value="Strict">Strict</option>
          <option value="Layout">Layout</option>
          <option value="Content">Content</option>
          <option value="Exact">Exact</option>
        </select>
      </label>

      {p.running ? (
        <button type="button" onClick={p.onCancel} className="w-full rounded-md bg-gray-200 py-2.5 font-semibold text-gray-700 hover:bg-gray-300">Cancel</button>
      ) : (
        <button type="button" disabled={!canRun} onClick={p.onRun} className="w-full rounded-md bg-brand-teal py-2.5 font-semibold text-white hover:bg-brand-tealDark disabled:opacity-40">▶ Run test</button>
      )}
    </div>
  );
}
