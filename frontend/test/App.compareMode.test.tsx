import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { App } from "../src/App";

// Mirrors App.runstarted.test.tsx's EventSource-stubbing pattern (the existing convention for
// tests that need to feed raw SSE payloads into the app's reducer).
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

// Mirrors App.error.test.tsx's global.fetch mocking pattern (the existing convention for
// tests that need to inspect /api/run's request body), not App.runstarted.test.tsx — that
// file only stubs EventSource and relies on setup.ts's generic fetch stub, which can't
// distinguish /api/choose-path calls or capture the POST body this test needs to assert on.
describe("App compare mode", () => {
  let origFetch: typeof globalThis.fetch;

  beforeEach(() => {
    origFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = origFetch;
    // The Forced-name test saves options (including fn) to localStorage via saveOptions —
    // clear it so the next test's App mount doesn't inherit a non-empty forced name.
    window.localStorage.removeItem("imagetester.options");
  });

  it("posts doc1Path/doc2Path with no sourcePath to /api/run in compare mode", async () => {
    let choosePathCallCount = 0;
    const runCalls: RequestInit[] = [];
    globalThis.fetch = async (url: RequestInfo | URL, init?: RequestInit) => {
      const path = url.toString();
      if (path.endsWith("/api/secret/api-key") && (!init?.method || init.method === "GET")) {
        return new Response(JSON.stringify({ hasKey: true }), { status: 200 });
      }
      if (path.endsWith("/api/choose-path") && init?.method === "POST") {
        choosePathCallCount += 1;
        const chosenPath = choosePathCallCount === 1 ? "/docs/one.pdf" : "/docs/two.pdf";
        return new Response(JSON.stringify({ path: chosenPath }), { status: 200 });
      }
      if (path.endsWith("/api/run") && init?.method === "POST") {
        runCalls.push(init);
        return new Response(JSON.stringify({ runId: "r-1" }), { status: 200 });
      }
      return new Response(JSON.stringify({ kind: "idle" }), { status: 200 });
    };

    render(<App />);

    fireEvent.click(await screen.findByRole("button", { name: /compare two documents/i }));

    fireEvent.click(screen.getByRole("button", { name: /choose file for doc 1/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: /choose file for doc 1/i })).toHaveAttribute("title", "/docs/one.pdf"));

    fireEvent.click(screen.getByRole("button", { name: /choose file for doc 2/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: /choose file for doc 2/i })).toHaveAttribute("title", "/docs/two.pdf"));

    fireEvent.change(screen.getByLabelText("Comparison name"), { target: { value: "shared-name" } });

    const runBtn = screen.getByRole("button", { name: /run test/i });
    await waitFor(() => expect(runBtn).not.toBeDisabled());
    fireEvent.click(runBtn);

    await waitFor(() => expect(runCalls.length).toBe(1));
    const body = JSON.parse(runCalls[0].body as string);
    expect(body.doc1Path).toBe("/docs/one.pdf");
    expect(body.doc2Path).toBe("/docs/two.pdf");
    expect(body.sourcePath).toBeUndefined();
  });

  it("disables the Run button in compare mode when Forced name is empty", async () => {
    globalThis.fetch = async (url: RequestInfo | URL, init?: RequestInit) => {
      const path = url.toString();
      if (path.endsWith("/api/secret/api-key") && (!init?.method || init.method === "GET")) {
        return new Response(JSON.stringify({ hasKey: true }), { status: 200 });
      }
      if (path.endsWith("/api/choose-path") && init?.method === "POST") {
        return new Response(JSON.stringify({ path: "/docs/x.pdf" }), { status: 200 });
      }
      return new Response(JSON.stringify({ kind: "idle" }), { status: 200 });
    };

    render(<App />);

    fireEvent.click(await screen.findByRole("button", { name: /compare two documents/i }));

    fireEvent.click(screen.getByRole("button", { name: /choose file for doc 1/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: /choose file for doc 1/i })).toHaveAttribute("title", "/docs/x.pdf"));

    fireEvent.click(screen.getByRole("button", { name: /choose file for doc 2/i }));

    const runBtn = await screen.findByRole("button", { name: /run test/i });
    await waitFor(() => expect(runBtn).toBeDisabled());
  });
});

// Regression test for the reducer bug (Finding 1): App.tsx's SSE reducer forwarded
// previewPath but silently dropped doc2PreviewPath from test-started/test-finished events,
// so TestRow never received doc2's path even though the backend sent it and TestRow already
// knew how to render it. This exercises the reducer directly via SSE, the exact seam that let
// the bug slip past component-level tests.
describe("App compare mode SSE reducer", () => {
  let origES: typeof globalThis.EventSource;
  let origFetch: typeof globalThis.fetch;

  beforeEach(() => {
    origES = globalThis.EventSource;
    origFetch = globalThis.fetch;
    RecordingEventSource.instances = [];
    // @ts-expect-error — test stub
    globalThis.EventSource = RecordingEventSource;
    globalThis.fetch = async () => new Response(JSON.stringify({ kind: "idle" }), { status: 200 });
  });

  afterEach(() => {
    globalThis.EventSource = origES;
    globalThis.fetch = origFetch;
  });

  it("renders both Doc 1 and Doc 2 previews when a compare-mode test-finished event carries doc2PreviewPath", async () => {
    const { container } = render(<App />);
    const es = RecordingEventSource.instances[0];
    expect(es).toBeDefined();

    act(() => {
      es.onmessage?.({ data: JSON.stringify({ type: "run-started", runId: "r-1" }) } as MessageEvent);
      es.onmessage?.({
        data: JSON.stringify({
          type: "test-finished",
          name: "compare-1",
          status: "pass",
          durationMs: 42,
          previewPath: "/docs/one.pdf",
          doc2PreviewPath: "/docs/two.pdf",
        }),
      } as MessageEvent);
    });

    await waitFor(() => expect(container.querySelectorAll("img[src*='api/preview']")).toHaveLength(2));
  });
});
