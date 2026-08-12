package com.applitools.imagetester.lib;

import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Loads the bundled Noto Sans JP font (SIL OFL 1.1, see fonts/OFL.txt) for
 * Japanese font normalization. The TTF is the Google Fonts variable build;
 * PDFBox renders its default instance, which is Regular 400.
 */
public final class NotoFontProvider {

    private static final String FONT_RESOURCE = "/fonts/NotoSansJP-Regular.ttf";

    private NotoFontProvider() {
    }

    /**
     * Loads the bundled font fully embedded (no subsetting) into the given
     * document. Each call parses the TTF; callers should load at most once
     * per document.
     */
    public static PDType0Font load(PDDocument doc) throws IOException {
        try (InputStream in = NotoFontProvider.class.getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled font resource missing from JAR: " + FONT_RESOURCE
                        + " — the build is broken, rebuild with src/main/resources intact.");
            }
            return PDType0Font.load(doc, in, false);
        }
    }
}
