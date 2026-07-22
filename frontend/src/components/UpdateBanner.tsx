import { useEffect, useRef, useState } from "react";
import { api } from "../lib/api";
import type { UpdateStatus } from "../types";

const POLL_INTERVAL_MS = 1000;

export function UpdateBanner() {
  const [status, setStatus] = useState<UpdateStatus | null>(null);
  const pollRef = useRef<number | null>(null);

  const refresh = () => api.updateStatus().then(setStatus).catch(() => {});

  useEffect(() => { refresh(); }, []);

  useEffect(() => {
    if (status?.state === "downloading" && pollRef.current === null) {
      pollRef.current = window.setInterval(refresh, POLL_INTERVAL_MS);
    }
    if (status?.state !== "downloading" && pollRef.current !== null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
    return () => {
      if (pollRef.current !== null) {
        window.clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  }, [status?.state]);

  if (!status?.available) return null;

  const handleUpdate = () => api.startUpdate().then(refresh).catch(() => refresh());
  const showFallbackLink = !status.canOneClick || status.state === "error";

  return (
    <div role="status" className="card mb-4 flex items-center justify-between px-4 py-3 text-sm text-brand-navy">
      <span>
        ImageTester {status.version} is available.
        {status.state === "error" && status.error ? ` Update failed: ${status.error}.` : ""}
        {status.state === "launched" ? " Finish the update in the installer, then relaunch ImageTester." : ""}
      </span>
      {status.state === "idle" && status.canOneClick && (
        <button type="button" onClick={handleUpdate} className="ml-4 rounded-lg bg-brand-teal px-3 py-1.5 font-semibold text-white transition-colors hover:bg-brand-tealDark">
          Update
        </button>
      )}
      {status.state === "downloading" && <span className="ml-4 animate-pulse text-gray-500">Downloading…</span>}
      {showFallbackLink && status.releasePageUrl && (
        <a className="ml-4 font-medium text-brand-teal transition-colors hover:text-brand-tealDark" href={status.releasePageUrl} target="_blank" rel="noreferrer">
          Download from GitHub
        </a>
      )}
    </div>
  );
}
