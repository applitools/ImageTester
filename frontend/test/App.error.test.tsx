import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { App } from "../src/App";

describe("App run error", () => {
  let origFetch: typeof globalThis.fetch;

  beforeEach(() => {
    origFetch = globalThis.fetch;
    // Seed a source path so the Run button can be enabled
    window.localStorage.setItem("imagetester.lastSourcePath", "/test/source");
  });

  afterEach(() => {
    globalThis.fetch = origFetch;
    window.localStorage.removeItem("imagetester.lastSourcePath");
  });

  it("shows error banner when api run returns 400", async () => {
    globalThis.fetch = async (url: RequestInfo | URL, init?: RequestInit) => {
      const path = url.toString();
      if (path.endsWith("/api/secret/api-key") && (!init?.method || init.method === "GET")) {
        return new Response(JSON.stringify({ hasKey: true }), { status: 200 });
      }
      if (path.endsWith("/api/run") && init?.method === "POST") {
        return new Response("Invalid source path", { status: 400 });
      }
      return new Response(JSON.stringify({ kind: "idle" }), { status: 200 });
    };

    render(<App />);

    // Wait for hasKey to resolve so the Run button becomes enabled
    const runBtn = await screen.findByRole("button", { name: /run test/i });
    await waitFor(() => expect(runBtn).not.toBeDisabled(), { timeout: 3000 });

    fireEvent.click(runBtn);

    // Assert the error banner is shown
    const alert = await screen.findByRole("alert");
    expect(alert).toBeInTheDocument();
  });
});
