package com.applitools.imagetester.lib;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Computes the cross-document intersection of path-shape hashes. Returns the
 * set of hashes whose paths appear in every input PDF — the watermark template
 * fingerprint. Paths unique to any one document drop out of the intersection.
 */
public final class PathFingerprinter {

    private PathFingerprinter() {
    }

    public static Set<String> intersection(List<File> pdfs) throws IOException {
        if (pdfs.isEmpty()) return new HashSet<>();
        Set<String> fingerprint = null;
        for (File pdf : pdfs) {
            Set<String> hashes = hashesIn(pdf);
            if (fingerprint == null) {
                fingerprint = hashes;
            } else {
                fingerprint.retainAll(hashes);
            }
        }
        return fingerprint;
    }

    /**
     * Minimum operator count for a path to qualify as a watermark candidate.
     * Text-as-bezier-outline watermarks (e.g. "UAT Proof") typically produce paths
     * with 100–400+ operators. Template structural elements (rounded rectangles,
     * section dividers, small icons) rarely exceed 40 operators. The 100-op floor
     * excludes all structural shapes while safely including complex glyph outlines.
     *
     * NOTE: {@link OpSequenceVarianceFinder} has its own independent floor (6 ops)
     * because position-variance is already a strong watermark signal there.
     */
    static final int MIN_OPS_FOR_WATERMARK_CANDIDATE = 100;

    private static Set<String> hashesIn(File pdf) throws IOException {
        Set<String> hashes = new HashSet<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDPage page = doc.getPage(i);
                PDFStreamParser parser = new PDFStreamParser(page);
                parser.parse();
                for (PathFingerprint.PathHashes ph : PathFingerprint.pathHashesFor(parser.getTokens())) {
                    if (ph.opCount >= MIN_OPS_FOR_WATERMARK_CANDIDATE) {
                        hashes.add(ph.coordHash);
                    }
                }
            }
        }
        return hashes;
    }
}
