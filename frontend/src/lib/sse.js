import { getToken } from "./token";
export function connectSse(onEvent) {
    const es = new EventSource(`/api/events?token=${encodeURIComponent(getToken())}`);
    es.onmessage = (m) => {
        try {
            onEvent(JSON.parse(m.data));
        }
        catch { /* ignore malformed */ }
    };
    return es;
}
