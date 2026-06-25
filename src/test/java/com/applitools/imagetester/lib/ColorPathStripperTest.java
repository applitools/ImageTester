package com.applitools.imagetester.lib;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ColorPathStripperTest {

    private static final float[] WATERMARK_GRAY = {0.7f, 0.7f, 0.7f};
    private static final float TOLERANCE = 0.05f;
    private static final Set<String> FILL_OPS =
            new HashSet<>(Arrays.asList("f", "F", "f*", "b", "b*", "B", "B*"));

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void removes_filled_path_matching_target_color() throws IOException {
        List<Object> tokens = tokensForFilledRect(new Color(0.7f, 0.7f, 0.7f));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals(0, countFillOps(result));
    }

    @Test
    public void removeFromAllPages_strips_only_matching_fill_in_document() throws IOException {
        java.io.File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(new Color(0.7f, 0.7f, 0.7f));
                cs.addRect(100f, 100f, 200f, 50f);
                cs.fill();
                cs.setNonStrokingColor(new Color(0.85f, 0.1f, 0.12f));
                cs.addRect(100f, 300f, 200f, 50f);
                cs.fill();
            }
            doc.save(file);
        }
        try (PDDocument doc = PDDocument.load(file)) {
            ColorPathStripper.removeFromAllPages(doc, WATERMARK_GRAY, TOLERANCE);
            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            assertEquals(1, countFillOps(parser.getTokens()));
        }
    }

    @Test
    public void keeps_filled_path_whose_color_differs_from_target() throws IOException {
        List<Object> tokens = tokensForFilledRect(new Color(0.85f, 0.1f, 0.12f));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals(1, countFillOps(result));
    }

    @Test
    public void keeps_clip_path_even_when_color_matches_target() throws IOException {
        List<Object> tokens = tokensForClipRect(new Color(0.7f, 0.7f, 0.7f));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals("clip path (re W n) must survive", 1, countOps(result, "n"));
        assertEquals(1, countOps(result, "re"));
    }

    private List<Object> tokensForFilledRect(Color color) throws IOException {
        java.io.File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(color);
                cs.addRect(100f, 100f, 200f, 50f);
                cs.fill();
            }
            doc.save(file);
        }
        try (PDDocument doc = PDDocument.load(file)) {
            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            return parser.getTokens();
        }
    }

    @Test
    public void removes_fill_when_color_set_via_g_operator() {
        // 0.7 g  <rect> f
        List<Object> tokens = Arrays.asList(
                num(0.7f), op("g"),
                num(100), num(100), num(50), num(20), op("re"), op("f"));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals(0, countFillOps(result));
    }

    @Test
    public void removes_fill_when_color_set_via_k_cmyk_operator() {
        // 0 0 0 0.3 k -> rgb(0.7,0.7,0.7); should match gray target
        List<Object> tokens = Arrays.asList(
                num(0f), num(0f), num(0f), num(0.3f), op("k"),
                num(100), num(100), num(50), num(20), op("re"), op("f"));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals(0, countFillOps(result));
    }

    @Test
    public void keeps_fill_after_Q_restores_a_nonmatching_color() {
        // q 0.7 0.7 0.7 rg Q  <rect> f  -- the Q restores black, so the fill is black, not gray
        List<Object> tokens = Arrays.asList(
                op("q"), num(0.7f), num(0.7f), num(0.7f), op("rg"), op("Q"),
                num(100), num(100), num(50), num(20), op("re"), op("f"));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals(1, countFillOps(result));
    }

    @Test
    public void keeps_stroked_path_even_when_color_matches() {
        // 0.7 0.7 0.7 rg <rect> S  -- stroked, not filled
        List<Object> tokens = Arrays.asList(
                num(0.7f), num(0.7f), num(0.7f), op("rg"),
                num(100), num(100), num(50), num(20), op("re"), op("S"));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals(1, countOps(result, "S"));
    }

    @Test
    public void removes_only_the_matching_path_among_several() {
        // red fill, gray fill, blue fill -> only gray removed
        List<Object> tokens = Arrays.asList(
                num(0.85f), num(0.1f), num(0.1f), op("rg"), num(10), num(10), num(5), num(5), op("re"), op("f"),
                num(0.7f), num(0.7f), num(0.7f), op("rg"), num(20), num(20), num(5), num(5), op("re"), op("f"),
                num(0.1f), num(0.2f), num(0.8f), op("rg"), num(30), num(30), num(5), num(5), op("re"), op("f"));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals(2, countFillOps(result));
    }

    @Test
    public void keeps_fill_just_outside_tolerance() {
        // 0.62 gray vs 0.70 target, tolerance 0.05 -> outside -> kept
        List<Object> tokens = Arrays.asList(
                num(0.62f), num(0.62f), num(0.62f), op("rg"),
                num(100), num(100), num(50), num(20), op("re"), op("f"));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals(1, countFillOps(result));
    }

    @Test
    public void removes_fill_just_inside_tolerance() {
        // 0.66 gray vs 0.70 target, tolerance 0.05 -> inside -> removed
        List<Object> tokens = Arrays.asList(
                num(0.66f), num(0.66f), num(0.66f), op("rg"),
                num(100), num(100), num(50), num(20), op("re"), op("f"));

        List<Object> result = ColorPathStripper.strip(tokens, WATERMARK_GRAY, TOLERANCE);

        assertEquals(0, countFillOps(result));
    }

    @Test
    public void keeps_everything_when_target_is_null() {
        List<Object> tokens = Arrays.asList(
                num(0.7f), num(0.7f), num(0.7f), op("rg"),
                num(100), num(100), num(50), num(20), op("re"), op("f"));

        List<Object> result = ColorPathStripper.strip(tokens, null, TOLERANCE);

        assertEquals(1, countFillOps(result));
    }

    private static Object op(String name) {
        return Operator.getOperator(name);
    }

    private static Object num(float v) {
        return new org.apache.pdfbox.cos.COSFloat(v);
    }

    private static Object num(int v) {
        return org.apache.pdfbox.cos.COSInteger.get(v);
    }

    private List<Object> tokensForClipRect(Color color) throws IOException {
        java.io.File file = tempFolder.newFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(color);
                cs.addRect(100f, 100f, 200f, 50f);
                cs.clip();
            }
            doc.save(file);
        }
        try (PDDocument doc = PDDocument.load(file)) {
            PDFStreamParser parser = new PDFStreamParser(doc.getPage(0));
            parser.parse();
            return parser.getTokens();
        }
    }

    private int countFillOps(List<Object> tokens) {
        int count = 0;
        for (Object t : tokens) {
            if (t instanceof Operator && FILL_OPS.contains(((Operator) t).getName())) count++;
        }
        return count;
    }

    private int countOps(List<Object> tokens, String opName) {
        int count = 0;
        for (Object t : tokens) {
            if (t instanceof Operator && opName.equals(((Operator) t).getName())) count++;
        }
        return count;
    }
}
