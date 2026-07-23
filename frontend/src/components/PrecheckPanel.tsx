import type { PrecheckFinding } from "../types";

interface Props {
  findings: PrecheckFinding[];
}

const SEVERITY_STYLES: Record<PrecheckFinding["severity"], string> = {
  ERROR: "text-rose-700",
  WARNING: "text-amber-700",
  INFO: "text-gray-500",
};

export function PrecheckPanel(p: Props) {
  if (p.findings.length === 0) return null;
  return (
    <div className="space-y-1">
      {p.findings.map((f, i) => (
        <p
          key={`${f.code}-${i}`}
          role={f.severity === "ERROR" ? "alert" : "status"}
          className={`text-xs ${SEVERITY_STYLES[f.severity]}`}
        >
          {f.message}
        </p>
      ))}
    </div>
  );
}
