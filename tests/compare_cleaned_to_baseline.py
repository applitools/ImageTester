"""Run ImageTester against the watermark-cleaned versions of each PDF, reusing
the same per-PDF app names from baseline_run.py. This diffs each cleaned PDF
against the baseline established earlier so the Eyes dashboard surfaces the
delta — which should be exactly the removed watermark.

Prereq: baseline_run.py has been run successfully so the baselines exist on Eyes.

Usage:
    python tests/compare_cleaned_to_baseline.py [<cleaned_root>]

Default cleaned_root is the v3 output we produced when validating the fix:
    TestData/Applitools - ANG Example PDFs/policy-docs-cleaned-v3/
"""
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / "jars" / "ImageTester_3.11.1_Windows.jar"
DEFAULT_CLEANED = ROOT / "TestData" / "Applitools - ANG Example PDFs" / "policy-docs-cleaned-v3"
STAGE_ROOT = ROOT / "TestData" / "_eyes_compare_runs"

BRANDS = ["budd", "coles", "oceania"]
APP_PREFIX = "ANG-PolicyDocs-Baseline"  # must match baseline_run.py


def list_pdfs(cleaned_root: Path):
    out = []
    for brand in BRANDS:
        folder = cleaned_root / brand / "20260529"
        for p in sorted(folder.glob("Email_*.pdf")):
            out.append((brand, p))
    return out


def run_single(brand: str, pdf: Path, api_key: str):
    stage_dir = STAGE_ROOT / f"{brand}_{pdf.stem}"
    if stage_dir.exists():
        shutil.rmtree(stage_dir)
    stage_dir.mkdir(parents=True)
    shutil.copy2(pdf, stage_dir / pdf.name)

    app_name = f"{APP_PREFIX}-{brand}-{pdf.stem}"

    cmd = [
        "java", "-jar", str(JAR),
        "-k", api_key,
        "-a", app_name,
        "-f", str(stage_dir),
    ]
    print(f"  -> diff against baseline: -a \"{app_name}\"")
    t0 = time.time()
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    dur = time.time() - t0
    log = proc.stdout + proc.stderr
    last = next((l for l in reversed(log.splitlines()) if l.strip()), "<no output>")
    print(f"     rc={proc.returncode}  {dur:.1f}s  {last.strip()}")
    return proc.returncode == 0


def main():
    cleaned_root = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_CLEANED
    if not cleaned_root.exists():
        sys.exit(f"Cleaned PDF root not found: {cleaned_root}")
    api_key = os.environ.get("APPLITOOLS_API_KEY")
    if not api_key:
        sys.exit("APPLITOOLS_API_KEY not set.")
    if not JAR.exists():
        sys.exit(f"Jar not found: {JAR}")

    pdfs = list_pdfs(cleaned_root)
    print(f"Diffing {len(pdfs)} cleaned PDFs against baselines under '{APP_PREFIX}-*'.\n")
    print(f"Source root: {cleaned_root}\n")

    results = []
    for brand, pdf in pdfs:
        print(f"[{brand}/{pdf.name}]")
        ok = run_single(brand, pdf, api_key)
        results.append((brand, pdf.name, ok))

    n_ok = sum(1 for *_, ok in results if ok)
    print(f"\nDone: {n_ok}/{len(results)} diff runs completed successfully.")
    print("Check the Applitools dashboard for visual diffs per app.")


if __name__ == "__main__":
    main()
