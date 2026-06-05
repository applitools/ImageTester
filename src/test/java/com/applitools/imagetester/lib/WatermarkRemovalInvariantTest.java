package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Strict invariant: after {@link VectorWatermarkRemover#removeFromAllPages},
 * the cleaned content stream is the original token sequence minus complete
 * path-operator groups whose hash matches the supplied fingerprint.
 *
 * Every other token — text strings, font selections (Tf), text positioning
 * (Td), text show ops (Tj), graphics state push/pop (q/Q), transformations
 * (cm), color settings (rg/RG), Form XObject references (Do), and the
 * numeric operands that go with them — must be preserved exactly in order.
 */
public class WatermarkRemovalInvariantTest {

    private static final Set<String> PATH_OPS = new HashSet<>(Arrays.asList(
            "m", "l", "c", "v", "y", "h", "re"));
    private static final Set<String> PAINT_OPS = new HashSet<>(Arrays.asList(
            "S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "n"));

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void cleaned_stream_preserves_every_non_path_token_in_order() throws IOException {
        File source = tempFolder.newFile("invariant_src.pdf");
        File second = tempFolder.newFile("invariant_src2.pdf");
        writeRichPage(source, /* bodyDx */ 200f, /* stampCx */ 100f, /* stampCy */ 400f);
        writeRichPage(second, /* bodyDx */ 120f, /* stampCx */ 300f, /* stampCy */ 250f);

        Set<String> fingerprint = PathFingerprinter.intersection(Arrays.asList(source, second));
        assertEquals("Fingerprint should hold exactly the shared stamp shape",
                1, fingerprint.size());

        // Snapshot original tokens before mutation.
        List<Object> originalTokens;
        try (PDDocument doc = PDDocument.load(source)) {
            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            originalTokens = new ArrayList<>(parser.getTokens());
        }

        // Apply removal and snapshot cleaned tokens.
        List<Object> cleanedTokens;
        try (PDDocument doc = PDDocument.load(source)) {
            VectorWatermarkRemover.removeFromAllPages(doc, fingerprint);
            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            cleanedTokens = new ArrayList<>(parser.getTokens());
        }

        Diff diff = subsequenceDiff(originalTokens, cleanedTokens);

        assertTrue("Cleaned stream must be a subsequence of original (got extra/reordered tokens)",
                diff.cleanedFullyMatched);
        assertTrue("Every non-path operator type from original must appear in cleaned: missing "
                        + diff.missingNonPathOps,
                diff.missingNonPathOps.isEmpty());
        assertTrue("All gaps must be complete path-operator groups: illegal gaps " + diff.illegalGaps,
                diff.illegalGaps.isEmpty());
        assertFalse("Removal must actually remove at least one path", diff.removedPaths.isEmpty());
        for (List<Object> removed : diff.removedPaths) {
            int opCount = countOps(removed);
            assertTrue("Removed path op-count (" + opCount + ") must be >= "
                            + PathFingerprinter.MIN_OPS_FOR_WATERMARK_CANDIDATE,
                    opCount >= PathFingerprinter.MIN_OPS_FOR_WATERMARK_CANDIDATE);
        }
    }

    @Test
    public void empty_fingerprint_yields_identical_token_stream() throws IOException {
        File source = tempFolder.newFile("noop_src.pdf");
        writeRichPage(source, 200f, 100f, 400f);

        List<Object> originalTokens;
        try (PDDocument doc = PDDocument.load(source)) {
            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            originalTokens = new ArrayList<>(parser.getTokens());
        }

        List<Object> cleanedTokens;
        try (PDDocument doc = PDDocument.load(source)) {
            VectorWatermarkRemover.removeFromAllPages(doc, Collections.<String>emptySet());
            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            cleanedTokens = new ArrayList<>(parser.getTokens());
        }

        assertEquals("Empty fingerprint must preserve every token",
                tokenSignatures(originalTokens), tokenSignatures(cleanedTokens));
    }

    /**
     * Builds a single PDF page that mixes the kinds of content the removal
     * pipeline must never touch — text, font selection, color, transformations,
     * graphics state — with two high-op paths: a fixed-shape stamp (the
     * fingerprint candidate) and a unique body polyline.
     */
    private void writeRichPage(File file, float bodyDx, float stampCx, float stampCy) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.saveGraphicsState();
                cs.setNonStrokingColor(0.2f, 0.3f, 0.4f);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 720);
                cs.showText("Document title");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                cs.newLineAtOffset(72, 700);
                cs.showText("Section header");
                cs.endText();

                cs.transform(org.apache.pdfbox.util.Matrix.getTranslateInstance(0, 0));
                drawUniqueBody(cs, 72f, 600f, 72f + bodyDx, 605f);
                drawStamp(cs, stampCx, stampCy);

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 9);
                cs.newLineAtOffset(72, 100);
                cs.showText("Footer text");
                cs.endText();
                cs.restoreGraphicsState();
            }
            doc.save(file);
        }
    }

    /** 101-op zigzag polyline whose normalized shape depends on dx/dy between endpoints. */
    private void drawUniqueBody(PDPageContentStream cs,
                                float x1, float y1, float x2, float y2) throws IOException {
        int steps = 100;
        cs.moveTo(x1, y1);
        for (int i = 1; i < steps; i++) {
            float t = (float) i / steps;
            float x = x1 + (x2 - x1) * t + (i % 2 == 0 ? 5f : -5f);
            float y = y1 + (y2 - y1) * t + (i % 3 == 0 ? 3f : -3f);
            cs.lineTo(x, y);
        }
        cs.stroke();
    }

    /** 101-op 100-gon stamp whose normalized shape is position-invariant. */
    private void drawStamp(PDPageContentStream cs, float cx, float cy) throws IOException {
        int sides = 100;
        float r = 30f;
        for (int i = 0; i < sides; i++) {
            double a = 2 * Math.PI * i / sides;
            float x = cx + (float) (r * Math.cos(a));
            float y = cy + (float) (r * Math.sin(a));
            if (i == 0) cs.moveTo(x, y);
            else cs.lineTo(x, y);
        }
        cs.closeAndStroke();
    }

    // ---- subsequence-diff verifier ----

    private static class Diff {
        boolean cleanedFullyMatched;
        Set<String> missingNonPathOps = new HashSet<>();
        List<List<Object>> illegalGaps = new ArrayList<>();
        List<List<Object>> removedPaths = new ArrayList<>();
    }

    /**
     * Walks original + cleaned token lists in parallel. Gaps in original
     * (tokens absent from cleaned) must each be a complete path group
     * (numeric args + path-ops + terminating paint-op).
     */
    private Diff subsequenceDiff(List<Object> orig, List<Object> cleaned) {
        Diff d = new Diff();
        int i = 0, j = 0;
        while (j < cleaned.size()) {
            if (i >= orig.size()) {
                d.cleanedFullyMatched = false;
                return d;
            }
            if (tokenSignatureOf(orig.get(i)).equals(tokenSignatureOf(cleaned.get(j)))) {
                i++; j++;
                continue;
            }
            int gapStart = i;
            String target = tokenSignatureOf(cleaned.get(j));
            while (i < orig.size() && !tokenSignatureOf(orig.get(i)).equals(target)) {
                i++;
            }
            if (i >= orig.size()) {
                d.cleanedFullyMatched = false;
                return d;
            }
            classifyGap(orig.subList(gapStart, i), d);
        }
        if (i < orig.size()) {
            classifyGap(orig.subList(i, orig.size()), d);
        }
        d.cleanedFullyMatched = true;
        return d;
    }

    private void classifyGap(List<Object> gap, Diff d) {
        int subStart = 0;
        for (int k = 0; k < gap.size(); k++) {
            Object t = gap.get(k);
            if (!(t instanceof Operator)) continue;
            String op = ((Operator) t).getName();
            if (PAINT_OPS.contains(op)) {
                List<Object> segment = gap.subList(subStart, k + 1);
                if (isCompletePath(segment)) {
                    d.removedPaths.add(new ArrayList<>(segment));
                } else {
                    d.illegalGaps.add(new ArrayList<>(segment));
                    recordMissingNonPathOps(segment, d);
                }
                subStart = k + 1;
            }
        }
        if (subStart < gap.size()) {
            List<Object> leftover = gap.subList(subStart, gap.size());
            d.illegalGaps.add(new ArrayList<>(leftover));
            recordMissingNonPathOps(leftover, d);
        }
    }

    private void recordMissingNonPathOps(List<Object> segment, Diff d) {
        for (Object t : segment) {
            if (t instanceof Operator) {
                String op = ((Operator) t).getName();
                if (!PATH_OPS.contains(op) && !PAINT_OPS.contains(op)) {
                    d.missingNonPathOps.add(op);
                }
            }
        }
    }

    private boolean isCompletePath(List<Object> segment) {
        boolean sawPath = false;
        for (Object t : segment) {
            if (!(t instanceof Operator)) continue;
            String op = ((Operator) t).getName();
            if (PATH_OPS.contains(op)) {
                sawPath = true;
            } else if (PAINT_OPS.contains(op)) {
                return sawPath;
            } else {
                return false;
            }
        }
        return false;
    }

    private int countOps(List<Object> segment) {
        int n = 0;
        for (Object t : segment) if (t instanceof Operator) n++;
        return n;
    }

    private List<String> tokenSignatures(List<Object> tokens) {
        List<String> sigs = new ArrayList<>(tokens.size());
        for (Object t : tokens) sigs.add(tokenSignatureOf(t));
        return sigs;
    }

    /** A canonical string identity for any token kind PDFBox emits. */
    private String tokenSignatureOf(Object t) {
        if (t instanceof Operator) return "op:" + ((Operator) t).getName();
        return t.getClass().getSimpleName() + ":" + t.toString();
    }
}
