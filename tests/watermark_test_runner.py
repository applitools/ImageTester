"""End-to-end functional tests for the -rwauto watermark removal pipeline.

For each test case:
  1. Copy a curated set of PDFs into a sandbox input directory.
  2. Run the shaded jar with -rwauto.
  3. Diff every original vs cleaned PDF: confirm all Do refs (Form XObjects)
     and all BT/ET text blocks are preserved on every page.

The tests don't render pages — they verify that the structural elements
the renderer would draw are still in the content stream after cleaning.
"""
import os
import re
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

from pypdf import PdfReader
from pypdf.generic import ArrayObject

ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / "jars" / "ImageTester_3.11.1_Windows.jar"
SOURCE_BASE = ROOT / "TestData" / "Applitools - ANG Example PDFs" / "policy-docs"
SANDBOX_ROOT = ROOT / "TestData" / "_watermark_test_runs"

BRANDS = ["budd", "coles", "oceania"]


def list_brand_pdfs(brand: str) -> list[Path]:
    folder = SOURCE_BASE / brand / "20260529"
    return sorted(folder.glob("Email_*.pdf"))


def page_stream(page) -> bytes:
    contents = page.get("/Contents")
    if contents is None:
        return b""
    obj = contents.get_object() if hasattr(contents, "get_object") else contents
    if isinstance(obj, ArrayObject):
        return b"".join(s.get_object().get_data() for s in obj)
    return obj.get_data()


def page_signature(stream_bytes: bytes) -> dict:
    """Structural signature: Do refs (set), BT count, total length."""
    text = stream_bytes.decode("latin-1", errors="replace")
    return {
        "do_refs": frozenset(re.findall(r"/([\w.]+)\s+Do", text)),
        "bt_count": text.count("BT"),
        "length": len(stream_bytes),
    }


def diff_pdf(orig: Path, cleaned: Path) -> dict:
    """Compare original to cleaned. Return regression details if any."""
    o = PdfReader(str(orig))
    c = PdfReader(str(cleaned))
    if len(o.pages) != len(c.pages):
        return {"error": f"page count mismatch: {len(o.pages)} vs {len(c.pages)}"}

    do_loss = []      # pages where a Do ref was lost
    bt_loss = []      # pages where text blocks decreased
    total_orig = 0
    total_clean = 0

    for i, (op, cp) in enumerate(zip(o.pages, c.pages)):
        os_ = page_signature(page_stream(op))
        cs_ = page_signature(page_stream(cp))
        total_orig += os_["length"]
        total_clean += cs_["length"]

        lost_refs = os_["do_refs"] - cs_["do_refs"]
        if lost_refs:
            do_loss.append({"page": i + 1, "lost": sorted(lost_refs)})
        if cs_["bt_count"] < os_["bt_count"]:
            bt_loss.append({
                "page": i + 1,
                "orig_bt": os_["bt_count"],
                "clean_bt": cs_["bt_count"],
            })

    return {
        "pages": len(o.pages),
        "do_loss": do_loss,
        "bt_loss": bt_loss,
        "bytes_orig": total_orig,
        "bytes_clean": total_clean,
        "pct_removed": (
            100 * (total_orig - total_clean) / total_orig if total_orig else 0
        ),
    }


def setup_sandbox(name: str, pdfs: list[Path]) -> tuple[Path, Path, dict[Path, Path]]:
    """Returns (input_dir, output_dir, mapping orig -> sandbox_input_pdf)."""
    sandbox = SANDBOX_ROOT / name
    if sandbox.exists():
        shutil.rmtree(sandbox)
    input_dir = sandbox / "input"
    output_dir = sandbox / "cleaned"
    input_dir.mkdir(parents=True)

    mapping = {}
    for src in pdfs:
        # Suffix the brand to keep filenames unique across folders
        brand = src.parent.parent.name
        dest = input_dir / f"{brand}_{src.name}"
        shutil.copy2(src, dest)
        mapping[src] = dest
    return input_dir, output_dir, mapping


def run_cleaner(input_dir: Path, output_dir: Path) -> tuple[int, str]:
    proc = subprocess.run(
        [
            "java", "-jar", str(JAR),
            "-rwauto",
            "-f", str(input_dir),
            "-rwo", str(output_dir),
        ],
        capture_output=True, text=True, timeout=180,
    )
    return proc.returncode, proc.stdout + proc.stderr


def run_test(name: str, pdfs: list[Path]) -> dict:
    print(f"\n{'='*70}\nTEST: {name}  ({len(pdfs)} PDFs)\n{'='*70}")
    for p in pdfs:
        print(f"  - {p.parent.parent.name}/{p.name}")

    input_dir, output_dir, mapping = setup_sandbox(name, pdfs)
    rc, output = run_cleaner(input_dir, output_dir)
    fingerprint_line = next(
        (l for l in output.splitlines() if "Watermark fingerprint" in l),
        "<no fingerprint line>",
    )
    print(f"\nFingerprint: {fingerprint_line.strip()}")
    if rc != 0:
        print(f"FAILED rc={rc}\n{output}")
        return {"name": name, "ok": False, "error": output}

    # Diff each cleaned PDF against its original
    summary = {
        "name": name,
        "ok": True,
        "fingerprint_line": fingerprint_line.strip(),
        "pdfs": [],
    }
    for orig, sandbox_in in mapping.items():
        cleaned = output_dir / sandbox_in.name
        if not cleaned.exists():
            print(f"  MISSING OUTPUT: {cleaned}")
            summary["ok"] = False
            continue
        diff = diff_pdf(orig, cleaned)
        summary["pdfs"].append({"name": sandbox_in.name, "diff": diff})

    # Print verdict
    total_do_loss = sum(len(p["diff"].get("do_loss", [])) for p in summary["pdfs"])
    total_bt_loss = sum(len(p["diff"].get("bt_loss", [])) for p in summary["pdfs"])
    avg_removed = sum(p["diff"].get("pct_removed", 0) for p in summary["pdfs"]) / max(len(summary["pdfs"]), 1)

    print(f"\nResult: avg {avg_removed:.1f}% removed")
    print(f"  Do-ref losses across all pages: {total_do_loss}")
    print(f"  Text-block losses across all pages: {total_bt_loss}")
    if total_do_loss == 0 and total_bt_loss == 0:
        print("  VERDICT: PASS - structural elements preserved")
    else:
        print("  VERDICT: FAIL - structural regression detected")
        summary["ok"] = False
        for pdf in summary["pdfs"]:
            d = pdf["diff"]
            if d.get("do_loss") or d.get("bt_loss"):
                print(f"    {pdf['name']}:")
                for dl in d.get("do_loss", []):
                    print(f"      page {dl['page']}: lost Do refs {dl['lost']}")
                for bl in d.get("bt_loss", []):
                    print(f"      page {bl['page']}: BT {bl['orig_bt']} -> {bl['clean_bt']}")
    return summary


def main():
    if not JAR.exists():
        sys.exit(f"Jar not found: {JAR}")

    budd = list_brand_pdfs("budd")
    coles = list_brand_pdfs("coles")
    oceania = list_brand_pdfs("oceania")

    test_cases = [
        ("homo_budd_all", budd),
        ("homo_coles_all", coles),
        ("homo_oceania_all", oceania),
        ("mixed_2x3_brands",  budd[:2] + coles[:2] + oceania[:2]),
        ("mixed_minimum_1x3", [budd[0], coles[0], oceania[0]]),
        ("mixed_all_18", budd + coles + oceania),
        ("mixed_budd_coles_only", budd[:3] + coles[:3]),
        ("mixed_budd_oceania_only", budd[:3] + oceania[:3]),
    ]

    results = []
    for name, pdfs in test_cases:
        results.append(run_test(name, pdfs))

    print(f"\n{'='*70}\nOVERALL SUMMARY\n{'='*70}")
    for r in results:
        verdict = "PASS" if r.get("ok") else "FAIL"
        print(f"  [{verdict}] {r['name']}")

    fails = [r for r in results if not r.get("ok")]
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
