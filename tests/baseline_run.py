"""Establish per-PDF Applitools baselines for all ANG PDF sets.

Each PDF under TestData/Applitools - ANG Example PDFs/ runs through
ImageTester once with a unique -a (AppName) so it lands in its own Eyes
baseline. Subsequent runs using the same -a value will diff against the
baseline captured here.

Covered subfolders:
  policy-docs/                 brand/date structured, original watermarked
  policy-docs-cleaned/         same structure, cleaned v1
  policy-docs-cleaned-v2/      same structure, cleaned v2
  policy-docs-cleaned-v3/      same structure, cleaned v3
  cleaned/pre                  flat, pre-env cleaned
  cleaned/uat                  flat, UAT-env cleaned
  no_watermark/                flat, no watermark reference
  watermarked/pre              flat, pre-env watermarked
  watermarked/uat              flat, UAT-env watermarked
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
STAGE_ROOT = ROOT / "TestData" / "_eyes_baseline_runs"

BRANDS = ["budd", "coles", "oceania"]


@dataclass
class PdfEntry:
    app_name: str
    pdf: Path
    label: str


def _brand_entries(subfolder: str, app_prefix: str) -> list[PdfEntry]:
    out = []
    for brand in BRANDS:
        for pdf in sorted((ANG_BASE / subfolder / brand / "20260529").glob("Email_*.pdf")):
            out.append(PdfEntry(
                app_name=f"{app_prefix}-{brand}-{pdf.stem}",
                pdf=pdf,
                label=f"{subfolder}/{brand}/{pdf.name}",
            ))
    return out


def _flat_entries(subfolder: str, app_prefix: str) -> list[PdfEntry]:
    out = []
    for pdf in sorted((ANG_BASE / subfolder).glob("*.pdf")):
        out.append(PdfEntry(
            app_name=f"{app_prefix}-{pdf.stem}",
            pdf=pdf,
            label=f"{subfolder}/{pdf.name}",
        ))
    return out


FOLDER_CONFIGS = [
    ("policy-docs",            lambda: _brand_entries("policy-docs",            "ANG-PolicyDocs")),
    ("policy-docs-cleaned",    lambda: _brand_entries("policy-docs-cleaned",    "ANG-PolicyDocs-Cleaned")),
    ("policy-docs-cleaned-v2", lambda: _brand_entries("policy-docs-cleaned-v2", "ANG-PolicyDocs-Cleaned-v2")),
    ("policy-docs-cleaned-v3", lambda: _brand_entries("policy-docs-cleaned-v3", "ANG-PolicyDocs-Cleaned-v3")),
    ("cleaned/pre",            lambda: _flat_entries("cleaned/pre",             "ANG-Cleaned-Pre")),
    ("cleaned/uat",            lambda: _flat_entries("cleaned/uat",             "ANG-Cleaned-UAT")),
    ("no_watermark",           lambda: _flat_entries("no_watermark",            "ANG-NoWatermark")),
    ("watermarked/pre",        lambda: _flat_entries("watermarked/pre",         "ANG-Watermarked-Pre")),
    ("watermarked/uat",        lambda: _flat_entries("watermarked/uat",         "ANG-Watermarked-UAT")),
]


def run_single(entry: PdfEntry, api_key: str) -> bool:
    stage_dir = STAGE_ROOT / entry.app_name
    if stage_dir.exists():
        shutil.rmtree(stage_dir)
    stage_dir.mkdir(parents=True)
    shutil.copy2(entry.pdf, stage_dir / entry.pdf.name)

    cmd = [
        "java", "-jar", str(JAR),
        "-k", api_key,
        "-a", entry.app_name,
        "-as",
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

    all_entries: list[PdfEntry] = []
    for folder_name, collector in FOLDER_CONFIGS:
        entries = collector()
        print(f"  {folder_name}: {len(entries)} PDFs")
        all_entries.extend(entries)

    print(f"\nEstablishing baselines for {len(all_entries)} PDFs total.\n")

    results: list[dict] = []
    for entry in all_entries:
        print(f"[{entry.label}]")
        ok = run_single(entry, api_key)
        results.append({"label": entry.label, "app": entry.app_name, "ok": ok})

    total = len(results)
    passed = sum(r["ok"] for r in results)
    print(f"\n{'='*72}\nBaseline summary ({passed}/{total} succeeded)\n{'='*72}")
    for r in results:
        flag = "OK " if r["ok"] else "FAIL"
        print(f"  [{flag}]  {r['label']}  ->  -a \"{r['app']}\"")

    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
