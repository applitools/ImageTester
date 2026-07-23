import { getToken } from "./token";
import type { UpdateStatus, PrecheckFinding } from "../types";

async function http<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: {
      "Authorization": `Bearer ${getToken()}`,
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    throw new Error(`${res.status}: ${detail}`);
  }
  if (res.status === 204) return undefined as unknown as T;
  return res.json();
}

async function uploadFile(file: File): Promise<{ path: string }> {
  const res = await fetch(`/api/upload?name=${encodeURIComponent(file.name)}`, {
    method: "POST",
    headers: { "Authorization": `Bearer ${getToken()}` },
    body: file,
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    throw new Error(`${res.status}: ${detail}`);
  }
  return res.json();
}

export const api = {
  status: () => http<unknown>("GET", "/api/status"),
  hasApiKey: () => http<{ hasKey: boolean }>("GET", "/api/secret/api-key"),
  setApiKey: (value: string) => http<void>("PUT", "/api/secret/api-key", { value }),
  deleteApiKey: () => http<void>("DELETE", "/api/secret/api-key"),
  choosePath: (type: "file" | "folder", start?: string) => http<{ path?: string }>("POST", "/api/choose-path", { type, start }),
  precheckCompare: (doc1Path: string, doc2Path: string, options: Record<string, unknown>) =>
    http<{ findings: PrecheckFinding[] }>("POST", "/api/precheck-compare", { doc1Path, doc2Path, options }),
  run: (payload: { sourcePath: string; options: Record<string, unknown> } | { doc1Path: string; doc2Path: string; options: Record<string, unknown> }) =>
    http<{ runId: string }>("POST", "/api/run", payload),
  cancel: () => http<void>("POST", "/api/cancel"),
  updateStatus: () => http<UpdateStatus>("GET", "/api/update"),
  startUpdate: () => http<void>("POST", "/api/update/install"),
  upload: uploadFile,
};
