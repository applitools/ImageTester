"""Strict token-level verification for -rwauto watermark removal.

The cleaned content stream of every page must be the original token sequence
minus complete path-operator groups. Every other token (text, transformations,
graphics state, Do references, colors) must be preserved exactly in order.

A path group is defined as: arg numbers + path operators (m/l/c/v/y/h/re)
terminated by a paint operator (S/s/f/F/f*/B/B*/b/b*/n).

Any token "missing" from the cleaned stream that is NOT part of a complete
path group counts as a regression.
"""
from __future__ import annotations

import math
import re as re_mod
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator

from pypdf import PdfReader
from pypdf.generic import ArrayObject


ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / "jars" / "ImageTester_3.11.1_Windows.jar"
SOURCE_BASE = ROOT / "TestData" / "Applitools - ANG Example PDFs" / "policy-docs"
SANDBOX_ROOT = ROOT / "TestData" / "_watermark_strict_runs"

PATH_OPS = {"m", "l", "c", "v", "y", "h", "re"}
PAINT_OPS = {"S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "n"}
MIN_OPS = 100  # Mirrors PathFingerprinter.MIN_OPS_FOR_WATERMARK_CANDIDATE


# ---------- Tokenizer ----------


@dataclass(frozen=True)
class Tok:
    kind: str       # 'num' | 'op' | 'name' | 'str' | 'hex' | 'bracket' | 'dict'
    value: object   # float for num; str for op/name/str/hex/bracket/dict


_NUM_RE = re_mod.compile(r"-?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
_OP_RE  = re_mod.compile(r"[a-zA-Z'\"][a-zA-Z0-9'\"*]*")


def tokenize(stream: bytes) -> list[Tok]:
    text = stream.decode("latin-1", errors="replace")
    out: list[Tok] = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c in " \t\r\n":
            i += 1
            continue
        if c == "%":  # comment: skip to EOL
            j = text.find("\n", i)
            i = n if j < 0 else j + 1
            continue
        if c == "(":
            depth = 1
            j = i + 1
            while j < n and depth > 0:
                ch = text[j]
                if ch == chr(92):  # backslash
                    j += 2
                    continue
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                j += 1
            out.append(Tok("str", text[i:j]))
            i = j
            continue
        if c == "<":
            if i + 1 < n and text[i + 1] == "<":
                # Inline dict — skip until matching >>
                depth = 1
                j = i + 2
                while j < n - 1 and depth > 0:
                    if text[j:j+2] == "<<":
                        depth += 1
                        j += 2
                    elif text[j:j+2] == ">>":
                        depth -= 1
                        j += 2
                    else:
                        j += 1
                out.append(Tok("dict", text[i:j]))
                i = j
                continue
            j = text.find(">", i + 1)
            j = n if j < 0 else j + 1
            out.append(Tok("hex", text[i:j]))
            i = j
            continue
        if c in "[]":
            out.append(Tok("bracket", c))
            i += 1
            continue
        if c == "/":
            j = i + 1
            while j < n and text[j] not in " \t\r\n/()[]{}<>":
                j += 1
            out.append(Tok("name", text[i:j]))
            i = j
            continue
        m = _NUM_RE.match(text, i)
        if m:
            out.append(Tok("num", float(m.group())))
            i = m.end()
            continue
        m = _OP_RE.match(text, i)
        if m:
            out.append(Tok("op", m.group()))
            i = m.end()
            continue
        i += 1
    return out


# ---------- Token equality ----------


_PDF_ESCAPES = {
    "n": b"\n", "r": b"\r", "t": b"\t", "b": b"\b", "f": b"\f",
    "(": b"(", ")": b")", chr(92): bytes([92]),
}


def _decode_string_literal(s: str) -> bytes:
    """Decode a PDF string literal like '(foo\\nbar)' to bytes."""
    inner = s[1:-1]
    out = bytearray()
    i = 0
    while i < len(inner):
        c = inner[i]
        if c == chr(92) and i + 1 < len(inner):
            nxt = inner[i + 1]
            if nxt in _PDF_ESCAPES:
                out += _PDF_ESCAPES[nxt]
                i += 2
                continue
            if nxt.isdigit():
                j = i + 1
                while j < len(inner) and j - i <= 3 and inner[j].isdigit():
                    j += 1
                out.append(int(inner[i + 1:j], 8) & 0xFF)
                i = j
                continue
            if nxt in ("\n", "\r"):
                i += 2
                continue
            i += 2
            continue
        out.append(ord(c) & 0xFF)
        i += 1
    return bytes(out)


def _decode_hex_string(s: str) -> bytes:
    inner = re_mod.sub(r"\s+", "", s[1:-1])
    if len(inner) % 2 == 1:
        inner += "0"
    return bytes.fromhex(inner)


def _string_bytes(t: Tok) -> bytes:
    if t.kind == "str":
        return _decode_string_literal(t.value)
    if t.kind == "hex":
        return _decode_hex_string(t.value)
    return b""


def tok_eq(a: Tok, b: Tok) -> bool:
    # String literals and hex strings encode the same byte sequence
    if a.kind in ("str", "hex") and b.kind in ("str", "hex"):
        return _string_bytes(a) == _string_bytes(b)
    if a.kind != b.kind:
        return False
    if a.kind == "num":
        return math.isclose(a.value, b.value, abs_tol=1e-4, rel_tol=1e-4)
    return a.value == b.value


# ---------- Page stream access ----------


def page_stream(page) -> bytes:
    contents = page.get("/Contents")
    if contents is None:
        return b""
    obj = contents.get_object() if hasattr(contents, "get_object") else contents
    if isinstance(obj, ArrayObject):
        return b"".join(s.get_object().get_data() for s in obj)
    return obj.get_data()


# ---------- Diff algorithm ----------


@dataclass
class RemovedPath:
    op_count: int
    op_seq: list[str]
    start_index: int  # index into original tokens


@dataclass
class PageVerdict:
    page: int
    cleaned_consumed: bool   # cleaned was fully consumed
    removed_paths: list[RemovedPath]
    illegal_segments: list[tuple[int, list[Tok]]]  # (orig_index, tokens)


def is_complete_path(segment: list[Tok]) -> tuple[bool, int, list[str]]:
    """A complete path is a sequence of numeric args + path-ops, terminated
    by a paint-op. Numeric args may appear interleaved (they're operands for
    the following path-op). Returns (ok, op_count, op_seq)."""
    ops: list[str] = []
    saw_path_start = False
    for t in segment:
        if t.kind == "num":
            continue
        if t.kind == "op":
            if t.value in PATH_OPS:
                ops.append(t.value)
                saw_path_start = True
            elif t.value in PAINT_OPS:
                if not saw_path_start:
                    return (False, 0, ops)
                ops.append(t.value)
                # Anything after a paint op is illegal in a single path group
                return (segment[-1].kind == "op" and segment[-1].value == t.value, len(ops), ops)
            else:
                return (False, 0, ops)
        else:
            return (False, 0, ops)
    return (False, len(ops), ops)


def verify_page(orig_tokens: list[Tok], clean_tokens: list[Tok], page_num: int) -> PageVerdict:
    """Walk both streams; missing-from-cleaned spans must each be a complete path."""
    removed: list[RemovedPath] = []
    illegal: list[tuple[int, list[Tok]]] = []

    i = 0  # original index
    j = 0  # cleaned index

    while j < len(clean_tokens):
        if i >= len(orig_tokens):
            # Cleaned has extra tokens — should not happen
            illegal.append((-1, clean_tokens[j:]))
            return PageVerdict(page_num, False, removed, illegal)

        if tok_eq(orig_tokens[i], clean_tokens[j]):
            i += 1
            j += 1
            continue

        # Mismatch: walk forward in original until we resync with cleaned[j]
        gap_start = i
        target = clean_tokens[j]
        while i < len(orig_tokens) and not tok_eq(orig_tokens[i], target):
            i += 1
        gap_tokens = orig_tokens[gap_start:i]

        # gap_tokens must form 1+ complete paths back-to-back
        # Split by paint-op boundary
        sub_start = 0
        for k, t in enumerate(gap_tokens):
            if t.kind == "op" and t.value in PAINT_OPS:
                segment = gap_tokens[sub_start:k + 1]
                ok, opc, opseq = is_complete_path(segment)
                if ok:
                    removed.append(RemovedPath(opc, opseq, gap_start + sub_start))
                else:
                    illegal.append((gap_start + sub_start, segment))
                sub_start = k + 1
        # Anything after last paint op in the gap is leftover args/path-ops
        # without a terminating paint — illegal if non-empty.
        leftover = gap_tokens[sub_start:]
        if leftover:
            illegal.append((gap_start + sub_start, leftover))

    # After cleaned is exhausted, anything left in orig must also be a removed path
    if i < len(orig_tokens):
        gap_tokens = orig_tokens[i:]
        sub_start = 0
        for k, t in enumerate(gap_tokens):
            if t.kind == "op" and t.value in PAINT_OPS:
                segment = gap_tokens[sub_start:k + 1]
                ok, opc, opseq = is_complete_path(segment)
                if ok:
                    removed.append(RemovedPath(opc, opseq, i + sub_start))
                else:
                    illegal.append((i + sub_start, segment))
                sub_start = k + 1
        leftover = gap_tokens[sub_start:]
        if leftover:
            illegal.append((i + sub_start, leftover))

    return PageVerdict(page_num, True, removed, illegal)


# ---------- Pipeline ----------


def list_brand(brand: str) -> list[Path]:
    return sorted((SOURCE_BASE / brand / "20260529").glob("Email_*.pdf"))


def setup(name: str, pdfs: list[Path]) -> tuple[Path, Path, dict[Path, Path]]:
    sandbox = SANDBOX_ROOT / name
    if sandbox.exists():
        shutil.rmtree(sandbox)
    in_dir = sandbox / "input"
    out_dir = sandbox / "cleaned"
    in_dir.mkdir(parents=True)
    mapping = {}
    for src in pdfs:
        dest = in_dir / f"{src.parent.parent.name}_{src.name}"
        shutil.copy2(src, dest)
        mapping[src] = dest
    return in_dir, out_dir, mapping


def run_cleaner(in_dir: Path, out_dir: Path) -> tuple[int, str]:
    p = subprocess.run(
        ["java", "-jar", str(JAR), "-rwauto", "-f", str(in_dir), "-rwo", str(out_dir)],
        capture_output=True, text=True, timeout=180,
    )
    return p.returncode, p.stdout + p.stderr


def verify_pdf(orig: Path, cleaned: Path) -> list[PageVerdict]:
    o = PdfReader(str(orig))
    c = PdfReader(str(cleaned))
    if len(o.pages) != len(c.pages):
        raise AssertionError(f"page count mismatch: {len(o.pages)} vs {len(c.pages)}")
    verdicts = []
    for i, (op, cp) in enumerate(zip(o.pages, c.pages)):
        otoks = tokenize(page_stream(op))
        ctoks = tokenize(page_stream(cp))
        verdicts.append(verify_page(otoks, ctoks, i + 1))
    return verdicts


def run_case(name: str, pdfs: list[Path]) -> bool:
    print(f"\n{'='*72}\nSTRICT TEST: {name}  ({len(pdfs)} PDFs)\n{'='*72}")
    in_dir, out_dir, mapping = setup(name, pdfs)
    rc, log = run_cleaner(in_dir, out_dir)
    fp_line = next((l for l in log.splitlines() if "fingerprint" in l), "<no fingerprint>")
    print(f"  fingerprint: {fp_line.strip()}")
    if rc != 0:
        print(f"  RUN FAILED rc={rc}\n{log}")
        return False

    all_ok = True
    total_illegal = 0
    total_removed_paths = 0
    total_low_op_removed = 0
    for orig, sandbox_in in mapping.items():
        cleaned = out_dir / sandbox_in.name
        verdicts = verify_pdf(orig, cleaned)
        for v in verdicts:
            total_removed_paths += len(v.removed_paths)
            low_ops = [r for r in v.removed_paths if r.op_count < MIN_OPS]
            total_low_op_removed += len(low_ops)
            if v.illegal_segments or low_ops:
                all_ok = False
                print(f"    REGRESSION in {sandbox_in.name} page {v.page}:")
                for idx, seg in v.illegal_segments:
                    sample = ", ".join(f"{t.kind}:{t.value}" for t in seg[:8])
                    print(f"      illegal segment @orig#{idx} ({len(seg)} toks): {sample}{'...' if len(seg)>8 else ''}")
                    total_illegal += 1
                for r in low_ops:
                    print(f"      removed path with only {r.op_count} ops (< MIN_OPS={MIN_OPS}): {' '.join(r.op_seq[:10])}")
    verdict = "PASS" if all_ok else "FAIL"
    print(f"  {verdict}  paths_removed={total_removed_paths}  illegal_segments={total_illegal}  below_threshold={total_low_op_removed}")
    return all_ok


def main():
    if not JAR.exists():
        sys.exit(f"Jar not found: {JAR}")

    budd = list_brand("budd")
    coles = list_brand("coles")
    oceania = list_brand("oceania")

    cases = [
        ("homo_budd_all",          budd),
        ("homo_coles_all",         coles),
        ("homo_oceania_all",       oceania),
        ("mixed_2x3_brands",       budd[:2] + coles[:2] + oceania[:2]),
        ("mixed_minimum_1x3",      [budd[0], coles[0], oceania[0]]),
        ("mixed_all_18",           budd + coles + oceania),
        ("mixed_budd_coles_only",  budd[:3] + coles[:3]),
        ("mixed_budd_oceania_only", budd[:3] + oceania[:3]),
    ]

    results = {}
    for name, pdfs in cases:
        results[name] = run_case(name, pdfs)

    print(f"\n{'='*72}\nSUMMARY\n{'='*72}")
    for name, ok in results.items():
        print(f"  [{ 'PASS' if ok else 'FAIL' }] {name}")
    sys.exit(0 if all(results.values()) else 1)


if __name__ == "__main__":
    main()
