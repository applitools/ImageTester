"""Convert an .xlsx to PDF and stamp Excel header/footer watermarks back on.

LibreOffice silently drops VML header/footer pictures during xlsx -> pdf
conversion. This wrapper resolves the watermark image directly out of the
.xlsx package (sheet -> legacyDrawingHF -> VML -> media), runs soffice
headlessly, then overlays the image on every page of the resulting PDF
so the visual-test pipeline sees the watermark the customer expects.

Usage:
    python stamp_watermark.py input.xlsx output.pdf

Requires: pypdf, reportlab, Pillow, LibreOffice (soffice) on PATH.
"""

import argparse
import io
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

from PIL import Image
from pypdf import PdfReader, PdfWriter
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas

SOFFICE_TIMEOUT_SECONDS = 120
WATERMARK_SCALE_TO_FIT_RATIO = 0.75

NS_RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
NS_PACKAGE_RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
NS_SHEETML = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
NS_VML = "urn:schemas-microsoft-com:vml"
NS_VML_OFFICE = "urn:schemas-microsoft-com:office:office"

VML_STYLE_DIMENSION = re.compile(r"(width|height)\s*:\s*([\d.]+)\s*pt", re.IGNORECASE)


def main():
    args = parse_args()
    xlsx_path = Path(args.xlsx)
    output_path = Path(args.pdf)

    if not xlsx_path.is_file():
        sys.exit(f"Input not found: {xlsx_path}")

    with tempfile.TemporaryDirectory(prefix="stamp-watermark-") as tmp:
        tmp_dir = Path(tmp)
        pdf_path = convert_to_pdf(xlsx_path, tmp_dir)
        watermark = extract_header_footer_watermark(xlsx_path)

        if watermark is None:
            shutil.copyfile(pdf_path, output_path)
            print(f"No header/footer watermark found; wrote plain PDF to {output_path}")
            return

        image_bytes, dims = watermark
        page_count = stamp_pdf(pdf_path, image_bytes, dims, output_path)
        print(f"Stamped watermark on {page_count} page(s): {output_path}")


def parse_args():
    parser = argparse.ArgumentParser(
        description="Convert an .xlsx to PDF and stamp Excel header/footer "
                    "watermarks back on (LibreOffice drops them)."
    )
    parser.add_argument("xlsx", help="Input .xlsx file")
    parser.add_argument("pdf", help="Output PDF path")
    return parser.parse_args()


def convert_to_pdf(xlsx_path, out_dir):
    """Run LibreOffice headless to convert xlsx -> pdf. Return the produced PDF path."""
    command = [
        "soffice",
        "--headless", "--norestore", "--nolockcheck", "--nofirststartwizard",
        "--nologo", "--nodefault",
        "--convert-to", "pdf",
        "--outdir", str(out_dir),
        str(xlsx_path),
    ]
    result = subprocess.run(
        command, capture_output=True, text=True, timeout=SOFFICE_TIMEOUT_SECONDS
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"soffice failed (exit {result.returncode}):\n{result.stderr}"
        )

    produced = out_dir / (xlsx_path.stem + ".pdf")
    if not produced.exists() or produced.stat().st_size == 0:
        raise RuntimeError(f"soffice produced no PDF at {produced}")
    return produced


def extract_header_footer_watermark(xlsx_path):
    """Return (image_bytes, (width_pt, height_pt)) for the first
    header/footer picture in the workbook, or None if none is present."""
    with zipfile.ZipFile(xlsx_path) as zf:
        sheet_paths = [
            name for name in zf.namelist()
            if name.startswith("xl/worksheets/") and name.endswith(".xml")
        ]
        for sheet_path in sheet_paths:
            found = resolve_watermark_for_sheet(zf, sheet_path)
            if found is not None:
                return found
    return None


def resolve_watermark_for_sheet(zf, sheet_path):
    """For a single sheet XML, walk legacyDrawingHF -> VML -> media. Return
    (image_bytes, dims) or None if the sheet has no header/footer picture."""
    with zf.open(sheet_path) as f:
        sheet_root = ET.parse(f).getroot()

    header_footer = sheet_root.find(f"{{{NS_SHEETML}}}headerFooter")
    legacy_hf = sheet_root.find(f"{{{NS_SHEETML}}}legacyDrawingHF")
    if header_footer is None or legacy_hf is None:
        return None

    if not has_graphic_token(header_footer):
        return None

    legacy_rid = legacy_hf.get(f"{{{NS_RELATIONSHIPS}}}id")
    if not legacy_rid:
        return None

    sheet_dir = posix_dir(sheet_path)
    sheet_rels_path = f"{sheet_dir}/_rels/{Path(sheet_path).name}.rels"
    vml_path = resolve_relationship(zf, sheet_rels_path, legacy_rid, sheet_dir)
    if vml_path is None:
        return None

    image_rid, dims = parse_vml(zf.read(vml_path))
    if image_rid is None:
        return None

    vml_dir = posix_dir(vml_path)
    vml_rels_path = f"{vml_dir}/_rels/{Path(vml_path).name}.rels"
    image_path = resolve_relationship(zf, vml_rels_path, image_rid, vml_dir)
    if image_path is None:
        return None

    return zf.read(image_path), dims


def has_graphic_token(header_footer):
    """True if any child of <headerFooter> contains the &G picture token."""
    for child in header_footer:
        if child.text and "&G" in child.text:
            return True
    return False


def resolve_relationship(zf, rels_path, rid, base_dir):
    """Resolve relationship Id `rid` in `rels_path` against `base_dir`."""
    try:
        with zf.open(rels_path) as f:
            rels_root = ET.parse(f).getroot()
    except KeyError:
        return None

    for rel in rels_root.findall(f"{{{NS_PACKAGE_RELS}}}Relationship"):
        if rel.get("Id") == rid:
            return normalize_zip_path(base_dir, rel.get("Target", ""))
    return None


def normalize_zip_path(base_dir, target):
    """Resolve a relationship Target against its rels file's directory.
    Pure string handling so .. segments work the same on every OS."""
    parts = base_dir.split("/") + target.split("/")
    out = []
    for segment in parts:
        if segment in ("", "."):
            continue
        if segment == "..":
            if out:
                out.pop()
        else:
            out.append(segment)
    return "/".join(out)


def posix_dir(zip_path):
    return Path(zip_path).parent.as_posix()


def parse_vml(vml_bytes):
    """Return (first imagedata relid, (width_pt, height_pt)) from a VML drawing,
    or (None, (None, None)) if no shape with imagedata is present."""
    root = ET.fromstring(vml_bytes)
    for shape in root.iter(f"{{{NS_VML}}}shape"):
        imagedata = shape.find(f"{{{NS_VML}}}imagedata")
        if imagedata is None:
            continue
        relid = imagedata.get(f"{{{NS_VML_OFFICE}}}relid")
        if not relid:
            continue
        dims = parse_vml_dimensions(shape.get("style", ""))
        return relid, dims
    return None, (None, None)


def parse_vml_dimensions(style):
    found = {}
    for key, value in VML_STYLE_DIMENSION.findall(style):
        found[key.lower()] = float(value)
    return found.get("width"), found.get("height")


def stamp_pdf(pdf_path, image_bytes, dims, output_path):
    """Overlay watermark on every page. Return the number of pages stamped."""
    reader = PdfReader(str(pdf_path))
    writer = PdfWriter()
    image = Image.open(io.BytesIO(image_bytes)).convert("RGBA")

    count = 0
    for page in reader.pages:
        page_w = float(page.mediabox.width)
        page_h = float(page.mediabox.height)
        wm_w, wm_h = watermark_size(image, dims, page_w, page_h)
        overlay = build_overlay_page(image, page_w, page_h, wm_w, wm_h)
        page.merge_page(overlay)
        writer.add_page(page)
        count += 1

    with open(output_path, "wb") as f:
        writer.write(f)
    return count


def watermark_size(image, dims, page_w, page_h):
    """Pick the watermark draw size in points. Prefer the dimensions declared in
    the VML (matches Excel's intent); otherwise fit-to-page preserving aspect."""
    wm_w, wm_h = dims
    if wm_w and wm_h:
        return wm_w, wm_h
    scale = min(
        (page_w * WATERMARK_SCALE_TO_FIT_RATIO) / image.width,
        (page_h * WATERMARK_SCALE_TO_FIT_RATIO) / image.height,
    )
    return image.width * scale, image.height * scale


def build_overlay_page(image, page_w, page_h, wm_w, wm_h):
    """Create a single-page PDF the size of the target page with the watermark
    centered, and return that page for merging."""
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=(page_w, page_h))
    x = (page_w - wm_w) / 2
    y = (page_h - wm_h) / 2
    c.drawImage(ImageReader(image), x, y, width=wm_w, height=wm_h, mask="auto")
    c.save()
    buf.seek(0)
    return PdfReader(buf).pages[0]


if __name__ == "__main__":
    main()
