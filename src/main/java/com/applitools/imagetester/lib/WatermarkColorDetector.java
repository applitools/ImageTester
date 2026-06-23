package com.applitools.imagetester.lib;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Auto-detects the fill color of a vector watermark across a cohort of PDFs.
 *
 * A watermark is a dense outline (its glyphs flattened to hundreds of path
 * segments) stamped into every document. This finds the filled paths that look
 * like that stamp, then returns the fill color of the single most complex one.
 * Branded chrome — header bars, buttons, even banners — is comparatively simple
 * geometry, so it loses to the watermark's outline; ties are broken by how often
 * the shape repeats.
 *
 * A path is treated as watermark-like if it either has a shape that is
 * byte-identical across every document ({@link PathFingerprinter}) OR has an
 * operator sequence shared by every document but whose coordinates vary per
 * document ({@link OpSequenceVarianceFinder}). The latter is the common case:
 * the same glyph outline restamped at a different position/transform in each
 * document, so no two coordinate hashes match even though the stamp is identical
 * to the eye.
 */
public final class WatermarkColorDetector {

    private static final Set<String> PATH_CONSTRUCTION_OPS = new HashSet<>(Arrays.asList(
            "m", "l", "c", "v", "y", "h", "re", "W", "W*"));
    private static final Set<String> FILL_PAINT_OPS = new HashSet<>(Arrays.asList(
            "f", "F", "f*", "b", "b*", "B", "B*"));
    private static final Set<String> NON_FILL_PAINT_OPS = new HashSet<>(Arrays.asList(
            "S", "s", "n"));

    /** Below this op count a shared path is treated as structural chrome, not a watermark outline. */
    private static final int MIN_COMPLEXITY = 6;
    private static final float COLOR_QUANTUM = 0.02f;

    private WatermarkColorDetector() {
    }

    public static float[] detect(List<File> pdfs) throws IOException {
        if (pdfs == null || pdfs.size() < 2) return null;
        Set<String> sharedShapes = PathFingerprinter.intersection(pdfs);
        Set<String> varyingOpSeqs = OpSequenceVarianceFinder.findVarying(pdfs);
        if (sharedShapes.isEmpty() && varyingOpSeqs.isEmpty()) return null;

        Map<String, ShapeTally> tally = new HashMap<>();
        for (File pdf : pdfs) {
            tallyDoc(pdf, sharedShapes, varyingOpSeqs, tally);
        }
        return mostComplexShapeColor(tally);
    }

    private static void tallyDoc(File pdf, Set<String> sharedShapes, Set<String> varyingOpSeqs,
            Map<String, ShapeTally> tally) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                PDFStreamParser parser = new PDFStreamParser(doc.getPage(p));
                parser.parse();
                tallyTokens(parser.getTokens(), sharedShapes, varyingOpSeqs, tally);
            }
        }
    }

    private static void tallyTokens(List<Object> tokens, Set<String> sharedShapes, Set<String> varyingOpSeqs,
            Map<String, ShapeTally> tally) {
        List<Object> argBuffer = new ArrayList<>();
        List<Object> currentPath = new ArrayList<>();
        boolean inPath = false;
        Deque<float[]> stateStack = new ArrayDeque<>();
        float[] fill = {0f, 0f, 0f};

        for (Object t : tokens) {
            if (!(t instanceof Operator)) {
                argBuffer.add(t);
                continue;
            }
            String op = ((Operator) t).getName();
            if (PATH_CONSTRUCTION_OPS.contains(op)) {
                currentPath.addAll(argBuffer);
                currentPath.add(t);
                argBuffer.clear();
                inPath = true;
            } else if (inPath && (FILL_PAINT_OPS.contains(op) || NON_FILL_PAINT_OPS.contains(op))) {
                currentPath.addAll(argBuffer);
                currentPath.add(t);
                argBuffer.clear();
                if (FILL_PAINT_OPS.contains(op)) record(currentPath, fill, sharedShapes, varyingOpSeqs, tally);
                currentPath.clear();
                inPath = false;
            } else {
                if (inPath) {
                    currentPath.clear();
                    inPath = false;
                }
                if ("q".equals(op)) {
                    stateStack.push(fill.clone());
                } else if ("Q".equals(op)) {
                    if (!stateStack.isEmpty()) fill = stateStack.pop();
                } else {
                    fill = DeviceColor.fromOperator(op, argBuffer, fill);
                }
                argBuffer.clear();
            }
        }
    }

    private static void record(List<Object> path, float[] fill, Set<String> sharedShapes,
            Set<String> varyingOpSeqs, Map<String, ShapeTally> tally) {
        List<PathFingerprint.PathHashes> hashes = PathFingerprint.pathHashesFor(path);
        if (hashes.isEmpty()) return;
        PathFingerprint.PathHashes ph = hashes.get(0);
        if (ph.opCount < MIN_COMPLEXITY) return;
        if (!sharedShapes.contains(ph.coordHash) && !varyingOpSeqs.contains(ph.opSeqHash)) return;
        // Key by op-sequence: a watermark restamped per document has a different
        // coordHash in each one, but a single stable opSeqHash to tally against.
        ShapeTally entry = tally.computeIfAbsent(ph.opSeqHash, k -> new ShapeTally(colorKey(fill)));
        entry.occurrences++;
        entry.opCount = ph.opCount;
    }

    private static float[] mostComplexShapeColor(Map<String, ShapeTally> tally) {
        ShapeTally best = null;
        for (ShapeTally s : tally.values()) {
            if (best == null
                    || s.opCount > best.opCount
                    || (s.opCount == best.opCount && s.occurrences > best.occurrences)) {
                best = s;
            }
        }
        if (best == null) return null;
        String[] parts = best.colorKey.split(",");
        return new float[] {
                Integer.parseInt(parts[0]) * COLOR_QUANTUM,
                Integer.parseInt(parts[1]) * COLOR_QUANTUM,
                Integer.parseInt(parts[2]) * COLOR_QUANTUM
        };
    }

    private static final class ShapeTally {
        final String colorKey;
        int occurrences;
        int opCount;

        ShapeTally(String colorKey) {
            this.colorKey = colorKey;
        }
    }

    private static String colorKey(float[] rgb) {
        return Math.round(rgb[0] / COLOR_QUANTUM) + ","
             + Math.round(rgb[1] / COLOR_QUANTUM) + ","
             + Math.round(rgb[2] / COLOR_QUANTUM);
    }
}
