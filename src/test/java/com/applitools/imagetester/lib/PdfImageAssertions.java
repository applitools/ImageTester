package com.applitools.imagetester.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;

/** Shared pixel-level assertions for rendered PDF pages. */
public final class PdfImageAssertions {

    private static final double MATCH_THRESHOLD_PERCENT = 1.0;

    private PdfImageAssertions() {
    }

    public static void assertImagesMatch(BufferedImage a, BufferedImage b) {
        assertEquals("Width mismatch", a.getWidth(), b.getWidth());
        assertEquals("Height mismatch", a.getHeight(), b.getHeight());

        int totalPixels = a.getWidth() * a.getHeight();
        double diffPercent = (countDiffPixels(a, b) * 100.0) / totalPixels;
        assertTrue("Images differ by " + String.format("%.2f", diffPercent)
                        + "% (threshold " + MATCH_THRESHOLD_PERCENT + "%)",
                diffPercent < MATCH_THRESHOLD_PERCENT);
    }

    public static void assertImagesDiffer(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return; // Different dimensions = different images
        }
        assertTrue("Images should differ but are identical", countDiffPixels(a, b) > 0);
    }

    /**
     * Asserts the images differ by at least MATCH_THRESHOLD_PERCENT - the
     * complement of assertImagesMatch, so a pair cannot satisfy both. Use
     * for negative controls; use assertImagesDiffer where any difference
     * at all is the correct bar.
     */
    public static void assertImagesClearlyDiffer(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return; // Different dimensions = clearly different images
        }
        int totalPixels = a.getWidth() * a.getHeight();
        double diffPercent = (countDiffPixels(a, b) * 100.0) / totalPixels;
        assertTrue("Images differ by only " + String.format("%.2f", diffPercent)
                        + "% (need >= " + MATCH_THRESHOLD_PERCENT + "%)",
                diffPercent >= MATCH_THRESHOLD_PERCENT);
    }

    private static int countDiffPixels(BufferedImage a, BufferedImage b) {
        int diff = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    diff++;
                }
            }
        }
        return diff;
    }
}
