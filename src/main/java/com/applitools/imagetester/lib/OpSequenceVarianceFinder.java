package com.applitools.imagetester.lib;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Finds path operator-sequences (e.g. "m l l l S") that appear in every input
 * PDF but whose specific coordinate values differ across docs. Such a sequence
 * is a watermark template stamped with subtle per-doc variation — same shape
 * structure, slightly different geometry per instance.
 *
 * The intersection ({@link PathFingerprinter}) catches watermarks drawn
 * byte-identically across docs; this finder catches the case where the
 * template renderer randomizes coordinates per stamp.
 */
public final class OpSequenceVarianceFinder {

    /**
     * Minimum operator count for a path to qualify as a watermark candidate.
     * Mirrors the floor in {@link PathFingerprinter}: text-as-bezier-outline
     * watermarks produce 100+ op paths, while common shape primitives (small
     * filled curves, bullet markers, checkmarks) that incidentally appear in
     * every template at slightly different positions stay well under that.
     *
     * Without this floor, cross-template batches falsely flag short shared
     * shapes as "varying op-seq" watermarks and strip legitimate UI elements.
     */
    private static final int MIN_OPS_FOR_WATERMARK_CANDIDATE = 100;

    private OpSequenceVarianceFinder() {
    }

    public static Set<String> findVarying(List<File> pdfs) throws IOException {
        if (pdfs.size() < 2) return Collections.emptySet();

        List<Map<String, Set<String>>> perDoc = new ArrayList<>();
        Set<String> opSeqsInEveryDoc = null;
        for (File pdf : pdfs) {
            Map<String, Set<String>> sites = sitesIn(pdf);
            perDoc.add(sites);
            if (opSeqsInEveryDoc == null) {
                opSeqsInEveryDoc = new HashSet<>(sites.keySet());
            } else {
                opSeqsInEveryDoc.retainAll(sites.keySet());
            }
        }
        if (opSeqsInEveryDoc == null) return Collections.emptySet();

        Set<String> varying = new HashSet<>();
        for (String opSeq : opSeqsInEveryDoc) {
            Set<String> reference = null;
            for (Map<String, Set<String>> docMap : perDoc) {
                Set<String> coordHashes = docMap.getOrDefault(opSeq, Collections.emptySet());
                if (reference == null) {
                    reference = coordHashes;
                } else if (!reference.equals(coordHashes)) {
                    varying.add(opSeq);
                    break;
                }
            }
        }
        return varying;
    }

    private static Map<String, Set<String>> sitesIn(File pdf) throws IOException {
        Map<String, Set<String>> result = new HashMap<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                PDFStreamParser parser = new PDFStreamParser(doc.getPage(p));
                parser.parse();
                for (PathFingerprint.PathHashes ph : PathFingerprint.pathHashesFor(parser.getTokens())) {
                    if (ph.opCount < MIN_OPS_FOR_WATERMARK_CANDIDATE) continue;
                    result.computeIfAbsent(ph.opSeqHash, k -> new HashSet<>()).add(ph.coordHash);
                }
            }
        }
        return result;
    }
}
