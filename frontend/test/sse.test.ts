import { vi, describe, it, expect, beforeEach, afterEach } from "vitest";
import { connectSse } from "../src/lib/sse";

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  static CLOSED = 2;
  onopen: ((e: Event) => void) | null = null;
  onmessage: ((e: MessageEvent) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;
  readyState = 1;
  closed = false;
  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }
  close() { this.closed = true; }
}

describe("connectSse", () => {
  let origES: typeof globalThis.EventSource;

  beforeEach(() => {
    vi.useFakeTimers();
    origES = globalThis.EventSource;
    FakeEventSource.instances = [];
    // @ts-expect-error — test stub
    globalThis.EventSource = FakeEventSource;
  });

  afterEach(() => {
    vi.useRealTimers();
    globalThis.EventSource = origES;
  });

  it("invokes onConnect when the stream opens", () => {
    const onConnect = vi.fn();
    connectSse(() => {}, onConnect);
    const es = FakeEventSource.instances[0];
    es.onopen?.(new Event("open"));
    expect(onConnect).toHaveBeenCalledTimes(1);
  });

  it("reopens a new stream after a fatal close", () => {
    connectSse(() => {}, () => {});
    const first = FakeEventSource.instances[0];
    first.readyState = FakeEventSource.CLOSED;
    first.onerror?.(new Event("error"));
    vi.runAllTimers();
    expect(FakeEventSource.instances.length).toBe(2);
  });

  it("delivers events from the reopened stream", () => {
    const events: unknown[] = [];
    connectSse((e) => events.push(e), () => {});
    const first = FakeEventSource.instances[0];
    first.readyState = FakeEventSource.CLOSED;
    first.onerror?.(new Event("error"));
    vi.runAllTimers();
    const second = FakeEventSource.instances[1];
    second.onmessage?.({ data: JSON.stringify({ type: "run-started", runId: "r1" }) } as MessageEvent);
    expect(events).toEqual([{ type: "run-started", runId: "r1" }]);
  });

  it("does not reopen after close() is called", () => {
    const handle = connectSse(() => {}, () => {});
    handle.close();
    const first = FakeEventSource.instances[0];
    first.readyState = FakeEventSource.CLOSED;
    first.onerror?.(new Event("error"));
    vi.runAllTimers();
    expect(FakeEventSource.instances.length).toBe(1);
  });
});
