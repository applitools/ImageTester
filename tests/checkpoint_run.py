"""Capture Eyes checkpoints for all ANG cleaned/watermarked PDF variants.

Runs each cleaned or watermarked PDF under the same -a (AppName) as its
original counterpart so Eyes diffs it against the baseline established by
baseline_run.py.

Comparisons made:
  policy-docs-cleaned     vs  policy-docs         baselines
  policy-docs-cleaned-v2  vs  policy-docs         baselines
  policy-docs-cleaned-v3  vs  policy-docs         baselines
  cleaned/pre             vs  watermarked/pre     baselines
  cleaned/uat             vs  watermarked/uat     baselines
"""
import os
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / "jars" / "ImageTester_3.11.1_Windows.jar"
ANG_BASE = ROOT / "TestData" / "Applitools - ANG Example PDFs"
STAGE_ROOT = ROOT / "TestData" / "_eyes_checkpoint_runs"

BRANDS = ["budd", "coles", "oceania"]


@dataclass
class CheckpointEntry:
    pdf: Path
    app_name: str   # matches the baseline app name
    label: str


def _brand_checkpoints(src_subfolder: str, baseline_prefix: str) -> list[CheckpointEntry]:
    out = []
    for brand in BRANDS:
        for pdf in sorted((ANG_BASE / src_subfolder / brand / "20260529").glob("Email_*.pdf")):
            out.append(CheckpointEntry(
                pdf=pdf,
                app_name=f"{baseline_prefix}-{brand}-{pdf.stem}",
                label=f"{src_subfolder}/{brand}/{pdf.name}",
            ))
    return out


def _flat_checkpoints(src_subfolder: str, baseline_prefix: str) -> list[CheckpointEntry]:
    out = []
    for pdf in sorted((ANG_BASE / src_subfolder).glob("*.pdf")):
        out.append(CheckpointEntry(
            pdf=pdf,
            app_name=f"{baseline_prefix}-{pdf.stem}",
            label=f"{src_subfolder}/{pdf.name}",
        ))
    return out


CHECKPOINT_CONFIGS = [
    ("policy-docs-cleaned vs policy-docs",
     lambda: _brand_checkpoints("policy-docs-cleaned",    "ANG-PolicyDocs")),
    ("policy-docs-cleaned-v2 vs policy-docs",
     lambda: _brand_checkpoints("policy-docs-cleaned-v2", "ANG-PolicyDocs")),
    ("policy-docs-cleaned-v3 vs policy-docs",
     lambda: _brand_checkpoints("policy-docs-cleaned-v3", "ANG-PolicyDocs")),
    ("cleaned/pre vs watermarked/pre",
     lambda: _flat_checkpoints("cleaned/pre",             "ANG-Watermarked-Pre")),
    ("cleaned/uat vs watermarked/uat",
     lambda: _flat_checkpoints("cleaned/uat",             "ANG-Watermarked-UAT")),
]


def run_single(entry: CheckpointEntry, api_key: str) -> bool:
    stage_dir = STAGE_ROOT / entry.app_name
    if stage_dir.exists():
        shutil.rmtree(stage_dir)
    stage_dir.mkdir(parents=True)
    shutil.copy2(entry.pdf, stage_dir / entry.pdf.name)

    cmd = [
        "java", "-jar", str(JAR),
        "-k", api_key,
        "-a", entry.app_name,
        "-f", str(stage_dir),
    ]
    print(f"  -> running: -a \"{entry.app_name}\"")
    t0 = time.time()
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    dur = time.time() - t0
    log = proc.stdout + proc.stderr
    summary_line = next(
        (l for l in log.splitlines() if "Suite finished" in l or "passed" in l.lower() or "ERROR" in l),
        log.splitlines()[-1] if log.splitlines() else "<no output>",
    )
    ok = proc.returncode == 0
    print(f"     rc={proc.returncode}  {dur:.1f}s  {summary_line.strip()}")
    return ok


def main():
    api_key = os.environ.get("APPLITOOLS_API_KEY")
    if not api_key:
        sys.exit("APPLITOOLS_API_KEY not set in environment.")
    if not JAR.exists():
        sys.exit(f"Jar not found: {JAR}")

    all_entries: list[CheckpointEntry] = []
    for group_label, collector in CHECKPOINT_CONFIGS:
        entries = collector()
        print(f"  {group_label}: {len(entries)} PDFs")
        all_entries.extend(entries)

    print(f"\nCapturing checkpoints for {len(all_entries)} PDFs total.\n")

    results: list[dict] = []
    for entry in all_entries:
        print(f"[{entry.label}]")
        ok = run_single(entry, api_key)
        results.append({"label": entry.label, "app": entry.app_name, "ok": ok})

    total = len(results)
    passed = sum(r["ok"] for r in results)
    print(f"\n{'='*72}\nCheckpoint summary ({passed}/{total} succeeded)\n{'='*72}")
    for r in results:
        flag = "OK " if r["ok"] else "FAIL"
        print(f"  [{flag}]  {r['label']}  ->  -a \"{r['app']}\"")

    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
