import { getToken } from "./token";

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

export const api = {
  status: () => http<unknown>("GET", "/api/status"),
  hasApiKey: () => http<{ hasKey: boolean }>("GET", "/api/secret/api-key"),
  setApiKey: (value: string) => http<void>("PUT", "/api/secret/api-key", { value }),
  deleteApiKey: () => http<void>("DELETE", "/api/secret/api-key"),
  choosePath: (type: "file" | "folder", start?: string) => http<{ path?: string }>("POST", "/api/choose-path", { type, start }),
  run: (sourcePath: string, matchLevel: string) => http<{ runId: string }>("POST", "/api/run", { sourcePath, matchLevel }),
  cancel: () => http<void>("POST", "/api/cancel"),
};
