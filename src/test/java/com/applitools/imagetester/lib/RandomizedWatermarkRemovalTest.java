package com.applitools.imagetester.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Property/fuzz test for -rwauto: across many randomly generated "templated
 * cohorts" (random watermark color, random tiling, random shared chrome, random
 * unique body content, random cohort sizes), the only thing removed must be the
 * watermark color — every other filled shape survives byte-for-byte in count.
 *
 * Mirrors the customer's situation: documents from one template share branding
 * (chrome) and a stamped watermark; only the watermark should go.
 */
public class RandomizedWatermarkRemovalTest {

    private static final int ITERATIONS = 40;
    private static final float QUANTUM = 0.02f;
    /** The watermark outline is far denser than any chrome shape, as in real PDFs. */
    private static final int WATERMARK_SEGMENTS = 70;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void randomized_cohorts_remove_only_the_watermark_color() throws IOException {
        for (int seed = 1; seed <= ITERATIONS; seed++) {
            runOneCohort(seed);
        }
    }

    private void runOneCohort(int seed) throws IOException {
        Random rnd = new Random(seed);
        int cohortSize = 2 + rnd.nextInt(5);            // 2..6 docs
        int tilesPerDoc = 1 + rnd.nextInt(4);            // 1..4 watermark stamps
        int chromeShapes = rnd.nextInt(5);               // 0..4 shared chrome shapes

        Color watermark = randomColor(rnd);
        List<Color> chrome = separatedColors(rnd, chromeShapes, watermark);
        String wmKey = colorKey(watermark);

        File inputDir = tempFolder.newFolder("in_" + seed);
        File outputDir = tempFolder.newFolder("out_" + seed);
        for (int d = 0; d < cohortSize; d++) {
            buildDoc(new File(inputDir, "doc" + d + ".pdf"), rnd, watermark, tilesPerDoc, chrome, d);
        }

        int exit = PdfVectorWatermarkAutoMode.run(inputDir, outputDir, null, new Logger());
        assertEquals("seed " + seed + ": run should succeed", 0, exit);

        for (int d = 0; d < cohortSize; d++) {
            File in = new File(inputDir, "doc" + d + ".pdf");
            File out = new File(outputDir, "doc" + d + ".pdf");
            assertTrue("seed " + seed + ": output doc" + d + " missing", out.exists());

            Map<String, Integer> before = fillCensus(in);
            Map<String, Integer> after = fillCensus(out);

            assertTrue("seed " + seed + " doc" + d + ": watermark should have been present",
                    before.getOrDefault(wmKey, 0) > 0);
            assertEquals("seed " + seed + " doc" + d + ": watermark color must be fully removed",
                    0, (int) after.getOrDefault(wmKey, 0));

            for (Map.Entry<String, Integer> e : before.entrySet()) {
                if (e.getKey().equals(wmKey)) continue;
                assertEquals("seed " + seed + " doc" + d + ": non-watermark color " + e.getKey()
                                + " must be preserved",
                        e.getValue(), after.get(e.getKey()));
            }
        }
    }

    private void buildDoc(File file, Random rnd, Color watermark, int tiles, List<Color> chrome, int docIndex)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Shared chrome — identical across docs (the template branding).
                for (int i = 0; i < chrome.size(); i++) {
                    cs.setNonStrokingColor(chrome.get(i));
                    cs.addRect(40f + i * 30f, 650f, 25f, 12f);
                    cs.fill();
                }
                // Watermark — a dense outline in the watermark color, tiled at varied positions.
                for (int t = 0; t < tiles; t++) {
                    drawDenseStamp(cs, watermark, 80f + t * 40f, 300f + t * 25f);
                }
                // Unique body — a stroked line and a uniquely colored small fill, per doc.
                cs.setStrokingColor(Color.BLACK);
                cs.moveTo(72f, 100f + docIndex * 7f);
                cs.lineTo(300f, 120f + docIndex * 5f);
                cs.stroke();
            }
            doc.save(file);
        }
    }

    private void drawDenseStamp(PDPageContentStream cs, Color color, float ox, float oy) throws IOException {
        cs.setNonStrokingColor(color);
        cs.moveTo(ox, oy);
        for (int i = 0; i < WATERMARK_SEGMENTS; i++) {
            cs.lineTo(ox + (i % 7) * 4f, oy + (i % 5) * 6f);
        }
        cs.closePath();
        cs.fill();
    }

    private Color randomColor(Random rnd) {
        return new Color(20 + rnd.nextInt(216), 20 + rnd.nextInt(216), 20 + rnd.nextInt(216));
    }

    /** Generates {@code count} colors each separated from the watermark and each other
     *  by more than the removal tolerance, so only the watermark color can match. */
    private List<Color> separatedColors(Random rnd, int count, Color watermark) {
        List<Color> out = new ArrayList<>();
        int guard = 0;
        while (out.size() < count && guard++ < 1000) {
            Color c = randomColor(rnd);
            if (farEnough(c, watermark) && out.stream().allMatch(o -> farEnough(c, o))) {
                out.add(c);
            }
        }
        return out;
    }

    private boolean farEnough(Color a, Color b) {
        return Math.max(Math.abs(a.getRed() - b.getRed()),
                Math.max(Math.abs(a.getGreen() - b.getGreen()),
                        Math.abs(a.getBlue() - b.getBlue()))) >= 40;
    }

    private Map<String, Integer> fillCensus(File pdf) throws IOException {
        Map<String, Integer> census = new HashMap<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                PDFStreamParser parser = new PDFStreamParser(doc.getPage(p));
                parser.parse();
                censusTokens(parser.getTokens(), census);
            }
        }
        return census;
    }

    private void censusTokens(List<Object> tokens, Map<String, Integer> census) {
        List<Object> args = new ArrayList<>();
        float[] fill = {0f, 0f, 0f};
        java.util.Deque<float[]> stack = new java.util.ArrayDeque<>();
        boolean inPath = false;
        for (Object t : tokens) {
            if (!(t instanceof Operator)) {
                args.add(t);
                continue;
            }
            String op = ((Operator) t).getName();
            if (isPathOp(op)) {
                inPath = true;
                args.clear();
            } else if (inPath && isFillPaint(op)) {
                census.merge(colorKey(fill), 1, Integer::sum);
                inPath = false;
                args.clear();
            } else if (inPath && isOtherPaint(op)) {
                inPath = false;
                args.clear();
            } else {
                if ("q".equals(op)) {
                    stack.push(fill.clone());
                } else if ("Q".equals(op)) {
                    if (!stack.isEmpty()) fill = stack.pop();
                } else {
                    fill = DeviceColor.fromOperator(op, args, fill);
                }
                inPath = false;
                args.clear();
            }
        }
    }

    private boolean isPathOp(String op) {
        return op.equals("m") || op.equals("l") || op.equals("c") || op.equals("v")
                || op.equals("y") || op.equals("h") || op.equals("re") || op.equals("W") || op.equals("W*");
    }

    private boolean isFillPaint(String op) {
        return op.equals("f") || op.equals("F") || op.equals("f*")
                || op.equals("b") || op.equals("b*") || op.equals("B") || op.equals("B*");
    }

    private boolean isOtherPaint(String op) {
        return op.equals("S") || op.equals("s") || op.equals("n");
    }

    private String colorKey(float[] rgb) {
        return Math.round(rgb[0] / QUANTUM) + "," + Math.round(rgb[1] / QUANTUM) + "," + Math.round(rgb[2] / QUANTUM);
    }

    private String colorKey(Color c) {
        return colorKey(new float[] {c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f});
    }
}
