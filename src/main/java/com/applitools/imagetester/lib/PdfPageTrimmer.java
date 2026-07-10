package com.applitools.imagetester.lib;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Resolves the -pt (pdf trim) option to a crop box for a page. In auto mode the
 * page's TrimBox metadata wins when it is genuinely smaller than the MediaBox;
 * otherwise crop marks are detected. Manual WxH crops a centered box, clamped to
 * the MediaBox. Returns null when no crop should be applied.
 */
public final class PdfPageTrimmer {

    private PdfPageTrimmer() {
    }

    public static PDRectangle resolveCropBox(PDPage page, Config config) {
        if (config.pdfTrim == null) return null;
        if (Config.PDF_TRIM_AUTO.equals(config.pdfTrim)) return resolveAuto(page);
        return centeredBox(page.getMediaBox(), Config.parsePdfTrimSize(config.pdfTrim));
    }

    private static PDRectangle resolveAuto(PDPage page) {
        PDRectangle trimBox = page.getTrimBox();
        PDRectangle mediaBox = page.getMediaBox();
        if (isStrictlySmaller(trimBox, mediaBox)) return trimBox;
        return CropMarkDetector.detect(page);
    }

    private static boolean isStrictlySmaller(PDRectangle inner, PDRectangle outer) {
        return inner.getWidth() < outer.getWidth() || inner.getHeight() < outer.getHeight();
    }

    private static PDRectangle centeredBox(PDRectangle media, float[] size) {
        float width = Math.min(size[0], media.getWidth());
        float height = Math.min(size[1], media.getHeight());
        float x = media.getLowerLeftX() + (media.getWidth() - width) / 2;
        float y = media.getLowerLeftY() + (media.getHeight() - height) / 2;
        return new PDRectangle(x, y, width, height);
    }
}
