import { render, screen, act } from "@testing-library/react";
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

describe("App run-started handling", () => {
  let origES: typeof globalThis.EventSource;

  beforeEach(() => {
    origES = globalThis.EventSource;
    RecordingEventSource.instances = [];
    // @ts-expect-error — test stub
    globalThis.EventSource = RecordingEventSource;
  });

  afterEach(() => {
    globalThis.EventSource = origES;
  });

  it("enters running state when a run-started event arrives while idle", async () => {
    render(<App />);
    const es = RecordingEventSource.instances[0];
    expect(es).toBeDefined();

    act(() => {
      es.onmessage?.({ data: JSON.stringify({ type: "run-started", runId: "r-123" }) } as MessageEvent);
    });

    expect(await screen.findByText(/running/i)).toBeInTheDocument();
  });

  it("shows the test row when test-started follows run-started", async () => {
    render(<App />);
    const es = RecordingEventSource.instances[0];

    act(() => {
      es.onmessage?.({ data: JSON.stringify({ type: "run-started", runId: "r-123" }) } as MessageEvent);
      es.onmessage?.({ data: JSON.stringify({ type: "test-started", name: "lorem_20.pdf" }) } as MessageEvent);
    });

    expect(await screen.findByText(/lorem_20\.pdf/)).toBeInTheDocument();
  });
});
