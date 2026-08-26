// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";

import { SetupCard } from "./SetupCard";
import type { PrecheckFinding } from "../types";

const ERROR: PrecheckFinding = {
  severity: "ERROR",
  code: "ENCRYPTED_DOC",
  message: "Doc 2 (contract-v2.pdf) is password-protected. Set PDF password in Options.",
};

const DIMENSION: PrecheckFinding = {
  severity: "WARNING",
  code: "dimension-mismatch",
  message: "Page dimensions differ on 1 page(s) (pages 1): page 1 renders 2065x2923 px vs 2125x2750 px at 250 DPI.",
  data: { doc1SizePx: "2065x2923", doc2SizePx: "2125x2750" },
};

function renderSetupCard(overrides: Partial<Parameters<typeof SetupCard>[0]> & { drawerOpen: boolean; onToggleDrawer: () => void }) {
  return render(
    <SetupCard
      hasKey={true}
      sourcePath=""
      matchLevel="Strict"
      running={false}
      optionsCount={0}
      compareMode={true}
      doc1Path="C:/docs/contract-v1.pdf"
      doc2Path="C:/docs/contract-v2.pdf"
      forcedName="contract-v1-vs-v2"
      precheckFindings={[ERROR]}
      onSetKey={() => {}}
      onChoosePath={() => {}}
      onChooseDoc1={() => {}}
      onChooseDoc2={() => {}}
      onDropDoc1={() => {}}
      onDropDoc2={() => {}}
      onForcedNameChange={() => {}}
      onToggleCompareMode={() => {}}
      onMatchLevel={() => {}}
      onRun={() => {}}
      onCancel={() => {}}
      {...overrides}
    />,
  );
}

describe("SetupCard precheck dialog wiring", () => {
  it("opens the Options drawer from the dialog when it is closed", () => {
    const onToggleDrawer = vi.fn();
    renderSetupCard({ drawerOpen: false, onToggleDrawer });
    fireEvent.click(screen.getByRole("button", { name: "Open Options" }));
    expect(onToggleDrawer).toHaveBeenCalledTimes(1);
  });

  it("does not toggle the Options drawer closed when it is already open", () => {
    const onToggleDrawer = vi.fn();
    renderSetupCard({ drawerOpen: true, onToggleDrawer });
    fireEvent.click(screen.getByRole("button", { name: "Open Options" }));
    expect(onToggleDrawer).not.toHaveBeenCalled();
  });

  it("passes Match size fixes from the dialog to onSetMatchSize", () => {
    const onSetMatchSize = vi.fn();
    renderSetupCard({
      drawerOpen: false,
      onToggleDrawer: () => {},
      precheckFindings: [DIMENSION],
      onSetMatchSize,
    });
    fireEvent.click(screen.getByRole("button", { name: /Match Doc 2 size/ }));
    expect(onSetMatchSize).toHaveBeenCalledWith("2125x2750");
  });
});
