import { useState } from "react";
import type { DragEvent } from "react";

interface Props {
  label: string;
  path: string;
  uploadError?: string;
  onChoose: () => void;
  onDropFile: (file: File) => void;
}

const EMPTY_HINT = "Drop file or click to choose…";
const MULTI_FILE_MESSAGE = "Drop a single file";
const FOLDER_MESSAGE = "Folders can't be compared — drop a single file";

function baseName(path: string): string {
  return path.split(/[\\/]/).pop() ?? path;
}

export function DocDropZone(p: Props) {
  const [isDragOver, setIsDragOver] = useState(false);
  const [dropError, setDropError] = useState<string | null>(null);
  const message = dropError ?? p.uploadError;

  const handleDrop = (e: DragEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files.length !== 1) {
      setDropError(MULTI_FILE_MESSAGE);
      return;
    }
    // jsdom and older engines lack webkitGetAsEntry — folder detection degrades to accepting.
    const entry = e.dataTransfer.items[0]?.webkitGetAsEntry?.();
    if (entry?.isDirectory) {
      setDropError(FOLDER_MESSAGE);
      return;
    }
    setDropError(null);
    p.onDropFile(e.dataTransfer.files[0]);
  };

  return (
    <div>
      <label className="block text-sm text-gray-700">{p.label} <span className="text-rose-600">*</span></label>
      <button
        type="button"
        aria-label={`Choose file for ${p.label}`}
        title={p.path || undefined}
        onClick={p.onChoose}
        onDragOver={(e) => { e.preventDefault(); setIsDragOver(true); }}
        onDragLeave={() => setIsDragOver(false)}
        onDrop={handleDrop}
        className={`mt-1 w-full truncate rounded-lg border px-3 py-4 text-sm transition-colors ${
          isDragOver
            ? "border-brand-teal bg-gray-50"
            : p.path
              ? "border-gray-200 bg-gray-50 text-gray-700 hover:bg-gray-100"
              : "border-dashed border-gray-300 text-gray-500 hover:bg-gray-50"
        }`}
      >
        {p.path ? baseName(p.path) : EMPTY_HINT}
      </button>
      {message && <p role="alert" className="mt-1 text-xs text-rose-700">{message}</p>}
    </div>
  );
}
