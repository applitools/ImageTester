import { useEffect, useRef, useState } from "react";
import type { PrecheckFinding } from "../types";

interface Props {
  findings: PrecheckFinding[];
  onOpenOptions: () => void;
  onSetMatchSize?: (value: string) => void;
  resetKey?: string;
}

type Severity = PrecheckFinding["severity"];

const SEVERITY_TEXT: Record<Severity, string> = {
  ERROR: "text-rose-700",
  WARNING: "text-amber-700",
  INFO: "text-gray-500",
};

const SEVERITY_DOT: Record<Severity, string> = {
  ERROR: "bg-rose-500",
  WARNING: "bg-amber-500",
  INFO: "bg-gray-400",
};

const PILL_TONE: Record<Severity, string> = {
  ERROR: "border-rose-200 bg-rose-50 text-rose-700",
  WARNING: "border-amber-200 bg-amber-50 text-amber-700",
  INFO: "border-gray-200 bg-gray-50 text-gray-600",
};

const BLOCKING_NOTE = "This blocks the run until it is fixed.";
const ADVISORY_NOTE = "You can run anyway — results may be incomplete.";

// Silhouettes scale the larger doc to fit this box; tiny pages stay visible.
const SILHOUETTE_MAX_HEIGHT_PX = 73;
const SILHOUETTE_MAX_WIDTH_PX = 90;
const SILHOUETTE_MIN_EDGE_PX = 10;
const STACK_MAX_PAGES = 5;

const DIALOG_TITLE_ID = "precheck-dialog-title";

function worstSeverity(findings: PrecheckFinding[]): Severity {
  if (findings.some((f) => f.severity === "ERROR")) return "ERROR";
  if (findings.some((f) => f.severity === "WARNING")) return "WARNING";
  return "INFO";
}

/** The one-click Match size remedy, when a dimension-mismatch finding carries both docs' sizes. */
function findMatchSizeFix(findings: PrecheckFinding[]): { doc1SizePx: string; doc2SizePx: string } | null {
  for (const f of findings) {
    if (f.code !== "dimension-mismatch") continue;
    const { doc1SizePx, doc2SizePx } = f.data ?? {};
    if (doc1SizePx && doc2SizePx) return { doc1SizePx, doc2SizePx };
  }
  return null;
}

/** Mismatch findings with structured data are drawn, not described; everything else keeps the raw message. */
function FindingRow({ finding: f }: { finding: PrecheckFinding }) {
  const role = f.severity === "ERROR" ? "alert" : "status";
  if (f.code === "dimension-mismatch") {
    const doc1 = parseSize(f.data?.doc1SizePx);
    const doc2 = parseSize(f.data?.doc2SizePx);
    if (doc1 && doc2) return <DimensionRow role={role} finding={f} doc1={doc1} doc2={doc2} />;
  }
  if (f.code === "page-count-mismatch") {
    const doc1Pages = Number(f.data?.doc1Pages);
    const doc2Pages = Number(f.data?.doc2Pages);
    if (doc1Pages > 0 && doc2Pages > 0) return <PageCountRow role={role} doc1Pages={doc1Pages} doc2Pages={doc2Pages} />;
  }
  return (
    <div role={role} className="flex items-baseline gap-2 px-5 py-3">
      <span className={`h-2 w-2 flex-shrink-0 rounded-full ${SEVERITY_DOT[f.severity]}`} />
      <span className={`flex-shrink-0 text-[11px] font-semibold uppercase tracking-wide ${SEVERITY_TEXT[f.severity]}`}>
        {f.severity}
      </span>
      <span className="text-[13px] text-gray-700">{f.message}</span>
    </div>
  );
}

interface PageSize { w: number; h: number }

function parseSize(value: string | undefined): PageSize | null {
  const match = /^(\d+)x(\d+)$/.exec(value ?? "");
  return match ? { w: Number(match[1]), h: Number(match[2]) } : null;
}

function DimensionRow(p: { role: "alert" | "status"; finding: PrecheckFinding; doc1: PageSize; doc2: PageSize }) {
  const scale = Math.min(
    SILHOUETTE_MAX_HEIGHT_PX / Math.max(p.doc1.h, p.doc2.h),
    SILHOUETTE_MAX_WIDTH_PX / Math.max(p.doc1.w, p.doc2.w),
  );
  const pages = p.finding.data?.pages;
  return (
    <div role={p.role} className="px-5 py-3.5">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-[13.5px] font-semibold text-gray-900">Page sizes don't match</h3>
        {pages && <Chip>Pages {pages}</Chip>}
      </div>
      <div className="mt-3 flex items-end gap-4">
        <Silhouette label="Doc 1" size={p.doc1} scale={scale} />
        <span aria-hidden="true" className="self-center pb-6 text-base font-semibold text-amber-700">≠</span>
        <Silhouette label="Doc 2" size={p.doc2} scale={scale} />
      </div>
      <p className="mt-2 text-xs leading-5 text-gray-500">
        Comparing PDFs with differing dimensions may lead to unexpected results due to content skewing.
      </p>
    </div>
  );
}

function Silhouette(p: { label: string; size: PageSize; scale: number }) {
  const width = Math.max(SILHOUETTE_MIN_EDGE_PX, Math.round(p.size.w * p.scale));
  const height = Math.max(SILHOUETTE_MIN_EDGE_PX, Math.round(p.size.h * p.scale));
  return (
    <span className="flex flex-col items-center gap-1.5">
      <span className="rounded-[3px] border-[1.5px] border-slate-500 bg-slate-50" style={{ width, height }} />
      <span className="text-center text-[11px] leading-snug text-gray-700">
        <span className="block font-semibold text-gray-900">{p.label}</span>
        {p.size.w} × {p.size.h} px
      </span>
    </span>
  );
}

function Chip(p: { children: React.ReactNode }) {
  return (
    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10.5px] font-semibold text-amber-800">
      {p.children}
    </span>
  );
}

function PageCountRow(p: { role: "alert" | "status"; doc1Pages: number; doc2Pages: number }) {
  const shared = Math.min(p.doc1Pages, p.doc2Pages);
  const extras = Math.abs(p.doc1Pages - p.doc2Pages);
  return (
    <div role={p.role} className="px-5 py-3.5">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-[13.5px] font-semibold text-gray-900">Page counts differ</h3>
        <span className="flex items-center gap-3.5">
          <PageStack count={p.doc1Pages} shared={shared} />
          <PageStack count={p.doc2Pages} shared={shared} />
        </span>
      </div>
      <p className="mt-1.5 text-xs leading-5 text-gray-500">
        {extras === 1
          ? "The extra page becomes a new baseline instead of a comparison."
          : "The extra pages become new baselines instead of comparisons."}
      </p>
    </div>
  );
}

function PageStack(p: { count: number; shared: number }) {
  const squares = Math.min(p.count, STACK_MAX_PAGES);
  return (
    <span className="flex items-center gap-1.5">
      <span className="flex items-end gap-[3px]">
        {Array.from({ length: squares }, (_, i) => (
          <span
            key={i}
            className={
              i < p.shared
                ? "h-[21px] w-4 rounded-[2px] border border-slate-500 bg-slate-50"
                : "h-[21px] w-4 rounded-[2px] border border-dashed border-amber-600 bg-amber-50"
            }
          />
        ))}
      </span>
      <span className="text-[11px] text-gray-700">{p.count}</span>
    </span>
  );
}

export function PrecheckPanel(p: Props) {
  // Auto-open only when a finding code appears that hasn't been seen for the
  // current docs. Severity changes and removals of already-seen codes never
  // re-pop — applying a Match size fix downgrades dimension-mismatch from
  // WARNING to INFO, and that self-inflicted downgrade must stay quiet.
  // Changing resetKey (either doc path) forgets the seen codes.
  const [seen, setSeen] = useState<{ resetKey: string; codes: readonly string[] }>({ resetKey: "", codes: [] });
  const [open, setOpen] = useState(false);
  const dialogRef = useRef<HTMLDivElement | null>(null);
  const pillRef = useRef<HTMLButtonElement | null>(null);
  const returnFocusToPill = useRef(false);

  const resetKey = p.resetKey ?? "";
  const seenCodes = seen.resetKey === resetKey ? seen.codes : [];
  const newCodes = p.findings.map((f) => f.code).filter((c) => !seenCodes.includes(c));
  if (newCodes.length > 0) {
    setSeen({ resetKey, codes: [...seenCodes, ...newCodes] });
    setOpen(true);
  }

  useEffect(() => {
    if (open) {
      dialogRef.current?.focus();
    } else if (returnFocusToPill.current) {
      returnFocusToPill.current = false;
      pillRef.current?.focus();
    }
  }, [open]);

  if (p.findings.length === 0) return null;

  const close = () => {
    returnFocusToPill.current = true;
    setOpen(false);
  };

  const count = p.findings.length;
  const worst = worstSeverity(p.findings);
  const blocking = worst === "ERROR";
  const matchSizeFix = findMatchSizeFix(p.findings);

  const applyMatchSize = (value: string) => {
    p.onSetMatchSize?.(value);
    close();
  };

  if (!open) {
    return (
      <button
        ref={pillRef}
        type="button"
        onClick={() => setOpen(true)}
        className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors hover:brightness-95 ${PILL_TONE[worst]}`}
      >
        {count} precheck finding{count === 1 ? "" : "s"} — Review
      </button>
    );
  }

  const handleDialogKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "Escape") {
      e.stopPropagation();
      close();
      return;
    }
    if (e.key !== "Tab") return;
    const focusables = dialogRef.current?.querySelectorAll<HTMLElement>("button");
    if (!focusables || focusables.length === 0) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[rgba(15,23,42,0.45)] p-4">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={DIALOG_TITLE_ID}
        tabIndex={-1}
        onKeyDown={handleDialogKeyDown}
        className="max-h-full w-full max-w-[520px] overflow-y-auto rounded-xl bg-white shadow-2xl outline-none focus-visible:ring-0 focus-visible:ring-offset-0"
      >
        <div className="flex items-start justify-between p-5 pb-4">
          <div>
            <div className="text-sm font-bold uppercase tracking-[.09em] text-gray-900">Precheck</div>
            <h2 id={DIALOG_TITLE_ID} className="mt-0.5 text-[13px] text-gray-500">
              {count} issue{count === 1 ? "" : "s"} to review before running
            </h2>
          </div>
          <button type="button" onClick={close} aria-label="Close" className="text-gray-400 transition-colors hover:text-gray-700">✕</button>
        </div>

        <div className="divide-y divide-gray-100 border-y border-gray-100">
          {p.findings.map((f, i) => (
            <FindingRow key={`${f.code}-${i}`} finding={f} />
          ))}
        </div>

        <div className="flex items-center justify-between gap-4 p-5 pt-4">
          <span className="text-xs text-gray-500">
            {blocking ? BLOCKING_NOTE : matchSizeFix ? "" : ADVISORY_NOTE}
          </span>
          <span className="flex flex-shrink-0 gap-2">
            {blocking ? (
              <button
                type="button"
                onClick={close}
                className="rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-700 transition-colors hover:bg-gray-50"
              >
                Close
              </button>
            ) : (
              <button
                type="button"
                onClick={close}
                className="rounded-lg border border-transparent px-3 py-1.5 text-sm text-rose-700 transition-colors hover:bg-rose-50"
              >
                Run anyway
              </button>
            )}
            {matchSizeFix ? (
              <>
                <button
                  type="button"
                  onClick={() => applyMatchSize(matchSizeFix.doc1SizePx)}
                  aria-label={`Match Doc 1 size (${matchSizeFix.doc1SizePx} pixels)`}
                  className="rounded-lg bg-brand-tealDark px-3 py-1.5 text-sm font-semibold text-white transition hover:brightness-95"
                >
                  Match Doc 1 size
                </button>
                <button
                  type="button"
                  onClick={() => applyMatchSize(matchSizeFix.doc2SizePx)}
                  aria-label={`Match Doc 2 size (${matchSizeFix.doc2SizePx} pixels)`}
                  className="rounded-lg bg-brand-tealDark px-3 py-1.5 text-sm font-semibold text-white transition hover:brightness-95"
                >
                  Match Doc 2 size
                </button>
              </>
            ) : (
              <button
                type="button"
                onClick={p.onOpenOptions}
                className="rounded-lg bg-brand-tealDark px-3 py-1.5 text-sm font-semibold text-white transition hover:brightness-95"
              >
                Open Options
              </button>
            )}
          </span>
        </div>
      </div>
    </div>
  );
}
