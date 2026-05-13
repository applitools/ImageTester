import { getToken } from "./token";
import type { SseEvent } from "../types";

export function connectSse(onEvent: (e: SseEvent) => void): EventSource {
  const es = new EventSource(`/api/events?token=${encodeURIComponent(getToken())}`);
  es.onmessage = (m) => {
    try { onEvent(JSON.parse(m.data)); } catch { /* ignore malformed */ }
  };
  return es;
}
