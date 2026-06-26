import "@testing-library/jest-dom";

// EventSource is not available in jsdom — provide a no-op stub so App can render in tests.
class EventSourceStub {
  onmessage: ((e: MessageEvent) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;
  close() {}
}
// @ts-expect-error — assigning a stub for test environment
globalThis.EventSource = EventSourceStub;

// Stub global fetch so App's on-mount API calls don't throw in jsdom.
if (!globalThis.fetch) {
  globalThis.fetch = () =>
    Promise.resolve(new Response(JSON.stringify({ hasKey: false, kind: "idle" }), { status: 200 }));
}
