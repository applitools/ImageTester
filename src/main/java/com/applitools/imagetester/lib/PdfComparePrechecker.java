package com.applitools.imagetester.lib;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates a doc1/doc2 pair before a compare run. Eyes resolves baselines by test name
 * and viewport (= rendered page size for PDFs), so dimension or page-count mismatches
 * silently produce new baselines instead of comparisons — this reports them up front.
 */
public final class PdfComparePrechecker {

    static final int MAX_LISTED_PAGES = 5;
    private static final int HASH_BUFFER_SIZE = 64 * 1024;

    public enum Severity { ERROR, WARNING, INFO }

    public static final class Finding {
        public final Severity severity;
        public final String code;
        public final String message;

        Finding(Severity severity, String code, String message) {
            this.severity = severity;
            this.code = code;
            this.message = message;
        }
    }

    /** Crop-box size and /Rotate for one page — what the renderer actually uses, not the media box. */
    private static final class PageBox {
        final float cropWidth;
        final float cropHeight;
        final int rotation;
        PageBox(float cropWidth, float cropHeight, int rotation) {
            this.cropWidth = cropWidth;
            this.cropHeight = cropHeight;
            this.rotation = rotation;
        }
    }

    private static final class DocFacts {
        final int pageCount;
        final List<PageBox> pageBoxes;
        DocFacts(int pageCount, List<PageBox> pageBoxes) {
            this.pageCount = pageCount;
            this.pageBoxes = pageBoxes;
        }
    }

    private PdfComparePrechecker() {}

    public static List<Finding> check(File doc1, File doc2, Config config) {
        List<Finding> findings = new ArrayList<>();
        boolean samePath = addFileLevelFindings(doc1, doc2, findings);
        boolean bothPdfs = Patterns.PDF.matcher(doc1.getName()).matches()
                && Patterns.PDF.matcher(doc2.getName()).matches();
        if (!bothPdfs) return findings;

        DocFacts facts1 = loadFacts("Doc 1", doc1, config, findings);
        DocFacts facts2 = loadFacts("Doc 2", doc2, config, findings);
        if (facts1 == null || facts2 == null || samePath) return findings;

        addPageCountFindings(facts1, facts2, findings);
        addDimensionFindings(facts1, facts2, config, findings);
        return findings;
    }

    /** Returns true when both sides are the same file (mismatch checks are then pointless). */
    private static boolean addFileLevelFindings(File doc1, File doc2, List<Finding> findings) {
        try {
            if (doc1.getCanonicalPath().equals(doc2.getCanonicalPath())) {
                findings.add(new Finding(Severity.WARNING, "same-file",
                        "Doc 1 and Doc 2 are the same file — the comparison will compare the document with itself."));
                return true;
            }
            if (doc1.length() == doc2.length() && sha256(doc1).equals(sha256(doc2))) {
                findings.add(new Finding(Severity.INFO, "identical-content",
                        "Doc 1 and Doc 2 have identical content — the comparison will trivially pass."));
            }
        } catch (IOException ignored) {
            // Unreadable files are reported by loadFacts with a precise message.
        }
        return false;
    }

    private static DocFacts loadFacts(String label, File doc, Config config, List<Finding> findings) {
        try (PDDocument document = PDDocument.load(doc, config.pdfPass)) {
            int pages = document.getNumberOfPages();
            if (pages == 0) {
                findings.add(new Finding(Severity.ERROR, "zero-pages",
                        label + " (" + doc.getName() + ") has no pages."));
                return null;
            }
            List<PageBox> boxes = new ArrayList<>(pages);
            for (int i = 0; i < pages; i++) {
                PDRectangle box = document.getPage(i).getCropBox();
                int rotation = document.getPage(i).getRotation();
                boxes.add(new PageBox(box.getWidth(), box.getHeight(), rotation));
            }
            return new DocFacts(pages, boxes);
        } catch (InvalidPasswordException e) {
            findings.add(new Finding(Severity.ERROR, "encrypted",
                    label + " (" + doc.getName() + ") is password-protected — set the PDF password option (-pp)."));
            return null;
        } catch (IOException e) {
            findings.add(new Finding(Severity.ERROR, "doc-unreadable",
                    label + " (" + doc.getName() + ") can't be read as a PDF — it may be corrupt or not a PDF."));
            return null;
        }
    }

    private static void addPageCountFindings(DocFacts facts1, DocFacts facts2, List<Finding> findings) {
        if (facts1.pageCount == facts2.pageCount) return;
        findings.add(new Finding(Severity.WARNING, "page-count-mismatch", String.format(
                "Doc 1 has %d page(s) but Doc 2 has %d page(s) — the extra page(s) will create new baselines "
                        + "instead of comparisons. Consider Selected pages (-sp) to align the ranges.",
                facts1.pageCount, facts2.pageCount)));
    }

    private static void addDimensionFindings(DocFacts facts1, DocFacts facts2, Config config, List<Finding> findings) {
        List<Integer> pagesToCheck = pagesToCheck(config, Math.min(facts1.pageCount, facts2.pageCount));
        List<Integer> mismatched = new ArrayList<>();
        List<Integer> rotated = new ArrayList<>();
        int[] firstMismatchPx1 = null;
        int[] firstMismatchPx2 = null;
        for (int page : pagesToCheck) {
            PageBox b1 = facts1.pageBoxes.get(page - 1);
            PageBox b2 = facts2.pageBoxes.get(page - 1);
            int[] px1 = renderedPixelSize(b1.cropWidth, b1.cropHeight, b1.rotation, config.DocumentConversionDPI);
            int[] px2 = renderedPixelSize(b2.cropWidth, b2.cropHeight, b2.rotation, config.DocumentConversionDPI);
            if (px1[0] == px2[0] && px1[1] == px2[1]) continue;
            mismatched.add(page);
            if (firstMismatchPx1 == null) {
                firstMismatchPx1 = px1;
                firstMismatchPx2 = px2;
            }
            if (px1[0] == px2[1] && px1[1] == px2[0]) rotated.add(page);
        }
        if (mismatched.isEmpty()) return;

        boolean hasSizeOverride = config.viewport != null || config.matchWidth != null;
        String listed = mismatched.stream().limit(MAX_LISTED_PAGES)
                .map(String::valueOf).collect(Collectors.joining(", "));
        StringBuilder message = new StringBuilder(String.format(
                "Page dimensions differ on %d page(s) (pages %s%s)",
                mismatched.size(), listed, mismatched.size() > MAX_LISTED_PAGES ? ", …" : ""));
        if (!rotated.isEmpty()) {
            message.append(String.format(" — page %s appears rotated", rotated.get(0)));
        }
        if (config.pdfTrim != null) {
            message.append(" (dimensions compared before trimming)");
        }
        message.append(". Eyes resolves baselines by viewport, so mismatched pages won't be compared. ");
        if (hasSizeOverride) {
            message.append("Match size/Viewport size is set — Eyes will use your override, "
                    + "but rendered content may still differ.");
        } else {
            message.append("Consider different PDFs, Match size (-ms), Viewport size (-vs), "
                    + "or Trim print margins (-tp).");
        }
        message.append(String.format(" — page %d renders %dx%d px vs %dx%d px at %.0f DPI",
                mismatched.get(0), firstMismatchPx1[0], firstMismatchPx1[1],
                firstMismatchPx2[0], firstMismatchPx2[1], config.DocumentConversionDPI));
        findings.add(new Finding(hasSizeOverride ? Severity.INFO : Severity.WARNING,
                "dimension-mismatch", message.toString()));
    }

    /**
     * Rendered pixel size for a page, mirroring PDFBox renderImageWithDPI: crop-box
     * dimensions (swapped for 90/270 rotation), scaled by dpi/72 and floor-rounded
     * with a 1px minimum. Eyes uses this rendered size as the baseline viewport.
     */
    private static int[] renderedPixelSize(float cropWidth, float cropHeight, int rotation, float dpi) {
        float scale = dpi / 72f;
        float width = cropWidth;
        float height = cropHeight;
        if (rotation == 90 || rotation == 270) {
            float tmp = width;
            width = height;
            height = tmp;
        }
        return new int[] {
                (int) Math.max(Math.floor(width * scale), 1),
                (int) Math.max(Math.floor(height * scale), 1)
        };
    }

    /** 1-based page numbers to compare: the -sp selection when set, else the whole common range. */
    private static List<Integer> pagesToCheck(Config config, int commonPageCount) {
        if (config.pages != null) {
            List<Integer> requested = Utils.parsePagesNotation(config.pages);
            if (requested != null && !requested.isEmpty()) {
                return requested.stream().filter(p -> p >= 1 && p <= commonPageCount)
                        .collect(Collectors.toList());
            }
        }
        List<Integer> all = new ArrayList<>(commonPageCount);
        for (int i = 1; i <= commonPageCount; i++) all.add(i);
        return all;
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[HASH_BUFFER_SIZE];
            try (InputStream in = new DigestInputStream(new FileInputStream(file), digest)) {
                while (in.read(buffer) != -1) {
                    // DigestInputStream updates the digest as bytes are read; nothing to do with them here.
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
