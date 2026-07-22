// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import type { UpdateStatus } from "../types";

const updateStatus = vi.fn<[], Promise<UpdateStatus>>();
const startUpdate = vi.fn<[], Promise<void>>();
vi.mock("../lib/api", () => ({ api: { updateStatus: () => updateStatus(), startUpdate: () => startUpdate() } }));

import { UpdateBanner } from "./UpdateBanner";

const base: UpdateStatus = {
  available: true, version: "3.16.0", releasePageUrl: "https://example.invalid/rel",
  canOneClick: true, state: "idle",
};

describe("UpdateBanner", () => {
  beforeEach(() => { updateStatus.mockReset(); startUpdate.mockReset(); });

  it("renders nothing when no update is available", async () => {
    updateStatus.mockResolvedValue({ ...base, available: false });
    const { container } = render(<UpdateBanner />);
    await waitFor(() => expect(updateStatus).toHaveBeenCalled());
    expect(container.firstChild).toBeNull();
  });

  it("shows the new version when an update exists", async () => {
    updateStatus.mockResolvedValue(base);
    render(<UpdateBanner />);
    expect(await screen.findByText(/3\.16\.0/)).toBeTruthy();
  });

  it("shows an Update button when one-click is possible", async () => {
    updateStatus.mockResolvedValue(base);
    render(<UpdateBanner />);
    expect(await screen.findByRole("button", { name: /update/i })).toBeTruthy();
  });

  it("falls back to a release link when one-click is not possible", async () => {
    updateStatus.mockResolvedValue({ ...base, canOneClick: false });
    render(<UpdateBanner />);
    const link = await screen.findByRole("link", { name: /download/i });
    expect(link.getAttribute("href")).toBe("https://example.invalid/rel");
  });

  it("shows the relaunch instruction once the installer is launched", async () => {
    updateStatus.mockResolvedValue({ ...base, state: "launched" });
    render(<UpdateBanner />);
    expect(await screen.findByText(/relaunch ImageTester/i)).toBeTruthy();
  });

  it("shows the fallback link when install errored", async () => {
    updateStatus.mockResolvedValue({ ...base, state: "error", error: "Checksum mismatch" });
    render(<UpdateBanner />);
    expect(await screen.findByRole("link", { name: /download/i })).toBeTruthy();
  });

  it("shows the error text when install errored", async () => {
    updateStatus.mockResolvedValue({ ...base, state: "error", error: "Checksum mismatch" });
    render(<UpdateBanner />);
    expect(await screen.findByText(/Update failed: Checksum mismatch\./)).toBeTruthy();
  });
});
