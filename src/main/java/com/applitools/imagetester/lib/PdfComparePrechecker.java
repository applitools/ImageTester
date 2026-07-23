package com.applitools.imagetester.lib;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

    static final float DIMENSION_TOLERANCE_PT = 1.0f;
    static final int MAX_LISTED_PAGES = 5;

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

    private static final class DocFacts {
        final int pageCount;
        final List<float[]> pageSizes;
        DocFacts(int pageCount, List<float[]> pageSizes) {
            this.pageCount = pageCount;
            this.pageSizes = pageSizes;
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
            List<float[]> sizes = new ArrayList<>(pages);
            for (int i = 0; i < pages; i++) {
                PDRectangle box = document.getPage(i).getMediaBox();
                sizes.add(new float[] { box.getWidth(), box.getHeight() });
            }
            return new DocFacts(pages, sizes);
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
        for (int page : pagesToCheck) {
            float[] s1 = facts1.pageSizes.get(page - 1);
            float[] s2 = facts2.pageSizes.get(page - 1);
            if (matches(s1, s2)) continue;
            mismatched.add(page);
            if (matches(s1, new float[] { s2[1], s2[0] })) rotated.add(page);
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
        findings.add(new Finding(hasSizeOverride ? Severity.INFO : Severity.WARNING,
                "dimension-mismatch", message.toString()));
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

    private static boolean matches(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) <= DIMENSION_TOLERANCE_PT
                && Math.abs(a[1] - b[1]) <= DIMENSION_TOLERANCE_PT;
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file.toPath()));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
