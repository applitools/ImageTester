package com.applitools.imagetester.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Collections;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WatermarkColorDetectorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void picks_color_of_shared_complex_shape_over_shared_simple_chrome() throws IOException {
        File a = writeDoc("a.pdf", 700f);
        File b = writeDoc("b.pdf", 1100f);

        float[] color = WatermarkColorDetector.detect(Arrays.asList(a, b));

        assertNotNull("a watermark color should be detected", color);
        assertEquals(0.7f, color[0], 0.05f);
        assertEquals(0.7f, color[1], 0.05f);
        assertEquals(0.7f, color[2], 0.05f);
    }

    @Test
    public void picks_complex_watermark_color_even_when_simple_chrome_repeats_more() throws IOException {
        File a = writeFrequentChromeAndComplexWatermark("a.pdf", 700f);
        File b = writeFrequentChromeAndComplexWatermark("b.pdf", 1100f);

        float[] color = WatermarkColorDetector.detect(Arrays.asList(a, b));

        assertNotNull(color);
        assertEquals("gray watermark, not the more-frequent dark chrome", 0.7f, color[0], 0.06f);
        assertEquals(0.7f, color[1], 0.06f);
        assertEquals(0.7f, color[2], 0.06f);
    }

    /** A single complex gray stamp plus a simple dark rectangle drawn eight times.
     *  The dark rect occurs far more often; the watermark wins only if detection
     *  keys on path complexity rather than frequency. */
    private File writeFrequentChromeAndComplexWatermark(String name, float bodyY) throws IOException {
        File file = tempFolder.newFile(name);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(new Color(0.7f, 0.7f, 0.7f));
                cs.moveTo(100f, 400f);
                for (int i = 0; i < 40; i++) {
                    cs.lineTo(100f + i * 3f, 400f + (i % 2 == 0 ? 20f : 0f));
                }
                cs.closePath();
                cs.fill();

                cs.setNonStrokingColor(new Color(0.12f, 0.2f, 0.26f));
                for (int k = 0; k < 8; k++) {
                    float oy = 100f + k * 30f;
                    cs.moveTo(50f, oy);
                    for (int i = 0; i < 8; i++) {
                        cs.lineTo(50f + i * 4f, oy + (i % 2 == 0 ? 8f : 0f));
                    }
                    cs.closePath();
                    cs.fill();
                }

                cs.setStrokingColor(Color.BLACK);
                cs.moveTo(72f, bodyY);
                cs.lineTo(300f, bodyY);
                cs.stroke();
            }
            doc.save(file);
        }
        return file;
    }

    @Test
    public void returns_null_for_a_single_pdf() throws IOException {
        File only = writeDoc("only.pdf", 700f);

        assertNull(WatermarkColorDetector.detect(Collections.singletonList(only)));
    }

    @Test
    public void detects_watermark_stamped_with_per_document_coordinate_variation() throws IOException {
        File a = writeVariantStamp("a.pdf", 0);
        File b = writeVariantStamp("b.pdf", 1);

        float[] color = WatermarkColorDetector.detect(Arrays.asList(a, b));

        assertNotNull("a watermark restamped with varying geometry must still be detected", color);
        assertEquals(0.7f, color[0], 0.06f);
        assertEquals(0.7f, color[1], 0.06f);
        assertEquals(0.7f, color[2], 0.06f);
    }

    @Test
    public void ignores_a_complex_shape_present_in_only_one_document() throws IOException {
        File a = writeSharedStamp("a.pdf", /*addUniqueBlob*/ true);
        File b = writeSharedStamp("b.pdf", /*addUniqueBlob*/ false);

        float[] color = WatermarkColorDetector.detect(Arrays.asList(a, b));

        assertNotNull(color);
        assertEquals("shared gray, not the unshared magenta blob", 0.7f, color[0], 0.06f);
        assertEquals(0.7f, color[1], 0.06f);
        assertEquals(0.7f, color[2], 0.06f);
    }

    /** Gray stamp whose geometry depends on {@code variant}, so two variants do not
     *  share a shape hash. */
    private File writeVariantStamp(String name, int variant) throws IOException {
        File file = tempFolder.newFile(name);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(new Color(0.7f, 0.7f, 0.7f));
                cs.moveTo(100f, 400f);
                for (int i = 0; i < 30; i++) {
                    cs.lineTo(100f + i * 3f, 400f + ((i + variant) % 2 == 0 ? 25f : 5f));
                }
                cs.closePath();
                cs.fill();
            }
            doc.save(file);
        }
        return file;
    }

    /** An identical shared gray stamp; optionally also a far more complex magenta blob
     *  that appears in only this one document (so it is not shared). */
    private File writeSharedStamp(String name, boolean addUniqueBlob) throws IOException {
        File file = tempFolder.newFile(name);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(new Color(0.7f, 0.7f, 0.7f));
                cs.moveTo(100f, 400f);
                for (int i = 0; i < 20; i++) {
                    cs.lineTo(100f + i * 4f, 400f + (i % 2 == 0 ? 15f : 0f));
                }
                cs.closePath();
                cs.fill();

                if (addUniqueBlob) {
                    cs.setNonStrokingColor(new Color(0.8f, 0.1f, 0.8f));
                    cs.moveTo(50f, 600f);
                    for (int i = 0; i < 200; i++) {
                        cs.lineTo(50f + (i % 9) * 5f, 600f + (i % 7) * 4f);
                    }
                    cs.closePath();
                    cs.fill();
                }
            }
            doc.save(file);
        }
        return file;
    }

    /** Each doc shares an identical complex gray stamp and a simple red rect,
     *  differing only in one body line whose Y is driven by {@code bodyY}. */
    private File writeDoc(String name, float bodyY) throws IOException {
        File file = tempFolder.newFile(name);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(new Color(0.7f, 0.7f, 0.7f));
                cs.moveTo(100f, 400f);
                for (int i = 0; i < 30; i++) {
                    cs.lineTo(100f + i * 5f, 400f + (i % 2 == 0 ? 40f : 0f));
                }
                cs.closePath();
                cs.fill();

                cs.setNonStrokingColor(new Color(0.85f, 0.1f, 0.12f));
                cs.addRect(50f, 600f, 100f, 30f);
                cs.fill();

                cs.setStrokingColor(Color.BLACK);
                cs.moveTo(72f, bodyY);
                cs.lineTo(300f, bodyY);
                cs.stroke();
            }
            doc.save(file);
        }
        return file;
    }
}
