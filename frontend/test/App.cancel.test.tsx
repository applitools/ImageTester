import { render, screen, fireEvent, act } from "@testing-library/react";
import { App } from "../src/App";

class RecordingEventSource {
  static instances: RecordingEventSource[] = [];
  onopen: ((e: Event) => void) | null = null;
  onmessage: ((e: MessageEvent) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;
  readyState = 1;
  constructor(public url: string) {
    RecordingEventSource.instances.push(this);
  }
  close() {}
}

function sse(es: RecordingEventSource, event: Record<string, unknown>) {
  es.onmessage?.({ data: JSON.stringify(event) } as MessageEvent);
}

describe("App cancel behavior", () => {
  let origES: typeof globalThis.EventSource;
  let origFetch: typeof globalThis.fetch;

  beforeEach(() => {
    origES = globalThis.EventSource;
    origFetch = globalThis.fetch;
    RecordingEventSource.instances = [];
    // @ts-expect-error — test stub
    globalThis.EventSource = RecordingEventSource;
    globalThis.fetch = async (url: RequestInfo | URL, init?: RequestInit) => {
      const path = url.toString();
      if (path.endsWith("/api/cancel") && init?.method === "POST") {
        return new Response(null, { status: 204 });
      }
      if (path.endsWith("/api/secret/api-key")) {
        return new Response(JSON.stringify({ hasKey: true }), { status: 200 });
      }
      return new Response(JSON.stringify({ kind: "idle" }), { status: 200 });
    };
  });

  afterEach(() => {
    globalThis.EventSource = origES;
    globalThis.fetch = origFetch;
  });

  async function startRun() {
    // Flush the on-mount status/hasKey fetches first — their stale "idle" snapshot would
    // otherwise resolve after the synthetic SSE events and wipe the simulated run.
    await act(async () => {});
    const es = RecordingEventSource.instances[0];
    act(() => {
      sse(es, { type: "run-started", runId: "r-1" });
      sse(es, { type: "test-started", name: "contract-compare" });
    });
    return es;
  }

  it("keeps the running test visible after Cancel is clicked", async () => {
    render(<App />);
    await startRun();

    fireEvent.click(await screen.findByRole("button", { name: /cancel/i }));

    expect(screen.getByText(/contract-compare/)).toBeInTheDocument();
  });

  it("shows a disabled Cancelling button after Cancel is clicked", async () => {
    render(<App />);
    await startRun();

    fireEvent.click(await screen.findByRole("button", { name: /cancel/i }));

    expect(await screen.findByRole("button", { name: /cancelling/i })).toBeDisabled();
  });

  it("returns to a runnable state when the cancelled run finishes", async () => {
    render(<App />);
    const es = await startRun();

    fireEvent.click(await screen.findByRole("button", { name: /cancel/i }));
    act(() => {
      sse(es, { type: "run-finished", passed: 0, failed: 0, durationMs: 10 });
    });

    expect(await screen.findByRole("button", { name: /run test/i })).toBeInTheDocument();
  });

  it("marks tests still running at run-finished as cancelled", async () => {
    render(<App />);
    const es = await startRun();

    act(() => {
      sse(es, { type: "run-finished", passed: 0, failed: 0, durationMs: 10 });
    });

    expect(await screen.findByText("Cancelled")).toBeInTheDocument();
  });
});
