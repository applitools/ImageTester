package com.applitools.imagetester.lib;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Decodes PDF show-text payloads to Unicode using the font that governs them.
 * Returns null when any code point has no Unicode mapping - callers treat
 * that run as undecodable and leave it untouched.
 */
public final class PdfTextDecoder {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(PdfTextDecoder.class.getName());

    private PdfTextDecoder() {
    }

    public static String decode(COSString string, PDFont font) {
        if (string == null || font == null) {
            return null;
        }
        byte[] bytes = string.getBytes();
        StringBuilder sb = new StringBuilder();
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            while (in.available() > 0) {
                int code = font.readCode(in);
                String unicode = font.toUnicode(code);
                if (unicode == null) {
                    return null;
                }
                sb.append(unicode);
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not decode text-show payload", e);
            return null;
        }
        return sb.toString();
    }

    public static String decode(COSArray array, PDFont font) {
        if (array == null || font == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            COSBase element = array.get(i);
            if (element instanceof COSString) {
                String part = decode((COSString) element, font);
                if (part == null) {
                    return null;
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
