import { getToken } from "./token";
import type { SseEvent } from "../types";

const RECONNECT_DELAY_MS = 2000;

export interface SseHandle {
  close: () => void;
}

export function connectSse(onEvent: (e: SseEvent) => void, onConnect?: () => void): SseHandle {
  let es: EventSource | null = null;
  let closed = false;

  const open = () => {
    es = new EventSource(`/api/events?token=${encodeURIComponent(getToken())}`);
    es.onopen = () => onConnect?.();
    es.onmessage = (m) => {
      try { onEvent(JSON.parse(m.data)); } catch { /* ignore malformed */ }
    };
    es.onerror = () => {
      // EventSource retries transient drops itself; only a CLOSED stream is fatal
      // (e.g. the server restarted), so recreate it after a short delay.
      if (!closed && es?.readyState === EventSource.CLOSED) {
        setTimeout(() => { if (!closed) open(); }, RECONNECT_DELAY_MS);
      }
    };
  };

  open();
  return {
    close: () => {
      closed = true;
      es?.close();
    },
  };
}
