package com.applitools.imagetester.lib;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Rewrites PDF page content streams so text renders in normalization fonts
 * while keeping the document's layout byte-faithful: glyph shapes change,
 * pen positions never do. Each show-text run is decoded to Unicode,
 * classified, and re-encoded — Japanese runs to the bundled Noto Sans JP
 * (when normalizeJapanese), other runs to Helvetica (when normalizeLatin) —
 * at the run's ORIGINAL font size, with a TJ adjustment after every glyph
 * restoring the original font's advance. Size, leading, kerning and spacing
 * are layout, not typography: they are preserved, so documents whose
 * typography metrics differ still diff. Runs that are not covered by an
 * enabled flag, or that cannot be decoded, keep their original font and
 * bytes. The original PDPage and PDDocument are never modified.
 */
public class PdfFontNormalizer {

    private static final COSName HELV = COSName.getPDFName("Helv");
    private static final COSName NOTO_JP = COSName.getPDFName("NotoJP");
    private static final char MISSING_JP_GLYPH = '\u3013'; // 〓 geta mark (escape survives any source encoding)
    private static final char MISSING_LATIN_GLYPH = '?';
    /** Advance deltas below this (thousandths of an em) are invisible; skip the adjustment. */
    private static final float ADVANCE_EPSILON = 0.001f;
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(PdfFontNormalizer.class.getName());

    /** Text-state snapshot: the active font plus what the OUTPUT stream currently has emitted. */
    private static final class FontState {
        PDFont font;
        COSName name;
        COSBase size;
        COSName emittedName;
        COSBase emittedSize;
        float charSpacing;              // Tc
        float wordSpacing;              // Tw

        FontState copy() {
            FontState c = new FontState();
            c.font = font;
            c.name = name;
            c.size = size;
            c.emittedName = emittedName;
            c.emittedSize = emittedSize;
            c.charSpacing = charSpacing;
            c.wordSpacing = wordSpacing;
            return c;
        }
    }

    private PdfFontNormalizer() {
    }

    /**
     * Creates a new detached PDPage with normalized text. targetDoc receives
     * the embedded Noto font when normalizeJapanese is set — the returned
     * page must be added to that same document before rendering.
     */
    public static PDPage normalize(PDPage originalPage, PDDocument targetDoc,
                                   boolean normalizeLatin, boolean normalizeJapanese) throws IOException {
        PDType0Font notoFont = normalizeJapanese ? NotoFontProvider.load(targetDoc) : null;

        PDFStreamParser parser = new PDFStreamParser(originalPage);
        parser.parse();
        List<Object> rewritten = rewriteTokens(parser.getTokens(), originalPage.getResources(),
                notoFont, normalizeLatin);

        PDPage normalizedPage = new PDPage(originalPage.getMediaBox());

        COSStream contentStream = new COSStream();
        try (OutputStream out = contentStream.createOutputStream()) {
            new ContentStreamWriter(out).writeTokens(rewritten);
        }
        normalizedPage.getCOSObject().setItem(COSName.CONTENTS, contentStream);

        PDResources resources = copyResources(originalPage.getResources());
        addNormalizationFonts(resources, notoFont, normalizeLatin);
        normalizedPage.setResources(resources);

        normalizeFormXObjects(resources, notoFont, normalizeLatin);

        return normalizedPage;
    }

    /**
     * Normalizes the page and renders it to a BufferedImage using a temporary
     * single-page PDDocument. The original page and its parent document are
     * not modified.
     */
    public static BufferedImage renderNormalized(PDPage originalPage, float dpi,
                                                 boolean normalizeLatin, boolean normalizeJapanese) throws IOException {
        try (PDDocument tempDoc = new PDDocument()) {
            PDPage normalizedPage = normalize(originalPage, tempDoc, normalizeLatin, normalizeJapanese);
            tempDoc.addPage(normalizedPage);
            return new PDFRenderer(tempDoc).renderImageWithDPI(0, dpi);
        }
    }

    /**
     * Walks the token list and produces a rewritten copy:
     *   Tf        — passed through (records the active font)
     *   Tc / Tw   — passed through (recorded: they scale with glyph count)
     *   q / Q     — text-state save/restore mirrored
     *   Tj ' " TJ — payload decoded, classified, re-encoded per glyph with a
     *               TJ adjustment restoring each glyph's original advance
     */
    private static List<Object> rewriteTokens(List<Object> tokens, PDResources resources,
                                              PDType0Font notoFont, boolean normalizeLatin) {
        List<Object> out = new ArrayList<>(tokens.size());
        List<Object> operands = new ArrayList<>();
        FontState state = new FontState();
        Deque<FontState> saved = new ArrayDeque<>();

        for (Object token : tokens) {
            if (!(token instanceof Operator)) {
                operands.add(token);
                continue;
            }
            String op = ((Operator) token).getName();
            if ("Tf".equals(op) && operands.size() >= 2 && operands.get(0) instanceof COSName) {
                state.name = (COSName) operands.get(0);
                state.size = (COSBase) operands.get(1);
                state.font = resolveFont(resources, state.name);
                state.emittedName = state.name;
                state.emittedSize = state.size;
            } else if ("Tc".equals(op) && lastIsNumber(operands)) {
                state.charSpacing = lastNumber(operands);
            } else if ("Tw".equals(op) && lastIsNumber(operands)) {
                state.wordSpacing = lastNumber(operands);
            } else if ("q".equals(op)) {
                saved.push(state.copy());
            } else if ("Q".equals(op) && !saved.isEmpty()) {
                state = saved.pop();
            } else if (isShowOp(op) && !operands.isEmpty()) {
                rewriteShow(out, operands, op, state, notoFont, normalizeLatin);
                operands.clear();
                continue;
            }
            out.addAll(operands);
            out.add(token);
            operands.clear();
        }
        out.addAll(operands);
        return out;
    }

    private static boolean isShowOp(String op) {
        return "Tj".equals(op) || "'".equals(op) || "\"".equals(op) || "TJ".equals(op);
    }

    private static boolean lastIsNumber(List<Object> operands) {
        return !operands.isEmpty() && operands.get(operands.size() - 1) instanceof COSNumber;
    }

    private static float lastNumber(List<Object> operands) {
        return ((COSNumber) operands.get(operands.size() - 1)).floatValue();
    }

    /** Decides the target font for a decoded run. Returns null when the run must keep its original font. */
    private static COSName chooseTarget(String decoded, PDType0Font notoFont, boolean normalizeLatin) {
        if (decoded == null) {
            return null;
        }
        if (JapaneseText.containsJapanese(decoded)) {
            return notoFont != null ? NOTO_JP : null;
        }
        if (!normalizeLatin) {
            return null;
        }
        // Symbols Helvetica lacks (e.g. ●, ①) fall back to Noto when available so
        // both sides of a comparison render them identically instead of as '?'
        if (notoFont != null && !canEncode(PDType1Font.HELVETICA, decoded)) {
            return NOTO_JP;
        }
        return HELV;
    }

    private static boolean canEncode(PDFont font, String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            try {
                font.encode(new String(Character.toChars(cp)));
            } catch (IllegalArgumentException | IOException e) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    /**
     * Rewrites one show op. Normalized runs become a TJ at the run's ORIGINAL
     * font size: each glyph is re-encoded for the target font and followed by
     * an adjustment restoring the original font's advance, so every pen
     * position on the page survives normalization. Tc applies per shown
     * glyph and Tw per single-byte space; when re-encoding changes either
     * count, the adjustment absorbs the difference. Runs that keep their
     * original font pass through untouched.
     */
    private static void rewriteShow(List<Object> out, List<Object> operands, String op,
                                    FontState state, PDType0Font notoFont, boolean normalizeLatin) {
        if ("\"".equals(op) && operands.size() >= 3
                && operands.get(0) instanceof COSNumber && operands.get(1) instanceof COSNumber) {
            state.wordSpacing = ((COSNumber) operands.get(0)).floatValue();
            state.charSpacing = ((COSNumber) operands.get(1)).floatValue();
        }

        Object payload = operands.get(operands.size() - 1);
        String decoded = payload instanceof COSArray
                ? PdfTextDecoder.decode((COSArray) payload, state.font)
                : payload instanceof COSString
                        ? PdfTextDecoder.decode((COSString) payload, state.font)
                        : null;
        COSName target = chooseTarget(decoded, notoFont, normalizeLatin);
        float sizeOrig = state.size instanceof COSNumber ? ((COSNumber) state.size).floatValue() : Float.NaN;

        if (target == null || Float.isNaN(sizeOrig) || sizeOrig == 0) {
            emitVerbatim(out, operands, op, state);
            return;
        }

        PDFont targetFont = NOTO_JP.equals(target) ? notoFont : PDType1Font.HELVETICA;
        char fallback = NOTO_JP.equals(target) ? MISSING_JP_GLYPH : MISSING_LATIN_GLYPH;

        COSArray rewritten;
        try {
            rewritten = reencodePerGlyph(payload, state, targetFont, fallback, sizeOrig);
        } catch (IOException widthUnavailable) {
            LOG.log(Level.FINE, "Advance widths unavailable; run kept untouched", widthUnavailable);
            emitVerbatim(out, operands, op, state);
            return;
        }

        if ("\"".equals(op)) {
            out.add(operands.get(0));
            out.add(Operator.getOperator("Tw"));
            out.add(operands.get(1));
            out.add(Operator.getOperator("Tc"));
        }
        if ("'".equals(op) || "\"".equals(op)) {
            out.add(Operator.getOperator("T*"));
        }
        ensureFont(out, state, target, state.size);
        out.add(rewritten);
        out.add(Operator.getOperator("TJ"));
    }

    /** Keeps a run's original operator, operands and bytes. */
    private static void emitVerbatim(List<Object> out, List<Object> operands, String op,
                                     FontState state) {
        ensureFont(out, state, state.name, state.size);
        out.addAll(operands);
        out.add(Operator.getOperator(op));
    }

    /**
     * Re-encodes a show payload glyph by glyph for the target font. After each
     * glyph a TJ adjustment (thousandths of an em, positive pulls the pen
     * back) restores the original font's advance:
     *   adj = wNew - wOrig                            (glyph metric delta)
     *       + (glyphsNew - 1) * Tc * 1000 / size      (Tc applies per shown glyph)
     *       + (spacesNew - spacesOrig) * Tw * 1000 / size  (Tw applies per single-byte space)
     * Original kern numbers pass through unchanged — the shown size equals the
     * original, so their effect is already exact. Dropped glyphs (no fallback)
     * yield adj = -wOrig, preserving the position of everything after them.
     */
    private static COSArray reencodePerGlyph(Object payload, FontState state, PDFont targetFont,
                                             char fallback, float sizeOrig) throws IOException {
        COSArray result = new COSArray();
        List<COSBase> elements = new ArrayList<>();
        if (payload instanceof COSArray) {
            for (int i = 0; i < ((COSArray) payload).size(); i++) {
                elements.add(((COSArray) payload).get(i));
            }
        } else {
            elements.add((COSBase) payload);
        }
        for (COSBase element : elements) {
            if (!(element instanceof COSString)) {
                result.add(element);
                continue;
            }
            try (InputStream in = new ByteArrayInputStream(((COSString) element).getBytes())) {
                while (in.available() > 0) {
                    int before = in.available();
                    int code = state.font.readCode(in);
                    boolean origSingleByteSpace = before - in.available() == 1 && code == 32;
                    float wOrig = state.font.getWidth(code);

                    String unicode = state.font.toUnicode(code);
                    if (unicode == null) {
                        throw new IOException("Code " + code + " lost its Unicode mapping mid-run");
                    }
                    byte[] encoded = encodeWithFallback(targetFont, unicode, fallback).getBytes();
                    GlyphTally emitted = tally(encoded, targetFont);

                    result.add(new COSString(encoded));
                    float adj = emitted.width - wOrig
                            + (emitted.glyphs - 1) * state.charSpacing * 1000f / sizeOrig
                            + (emitted.singleByteSpaces - (origSingleByteSpace ? 1 : 0))
                                    * state.wordSpacing * 1000f / sizeOrig;
                    if (Math.abs(adj) > ADVANCE_EPSILON) {
                        result.add(new COSFloat(adj));
                    }
                }
            }
        }
        return result;
    }

    /** Width (thousandths of an em), glyph count, and single-byte-space count of encoded bytes. */
    private static final class GlyphTally {
        float width;
        int glyphs;
        int singleByteSpaces;
    }

    private static GlyphTally tally(byte[] encoded, PDFont font) throws IOException {
        GlyphTally t = new GlyphTally();
        try (InputStream in = new ByteArrayInputStream(encoded)) {
            while (in.available() > 0) {
                int before = in.available();
                int code = font.readCode(in);
                t.width += font.getWidth(code);
                t.glyphs++;
                if (before - in.available() == 1 && code == 32) {
                    t.singleByteSpaces++;
                }
            }
        }
        return t;
    }

    /** Emits a corrective Tf when the output stream's current font differs from what the run needs. */
    private static void ensureFont(List<Object> out, FontState state, COSName name, COSBase size) {
        if (name == null) {
            return;
        }
        if (Objects.equals(name, state.emittedName) && Objects.equals(size, state.emittedSize)) {
            return;
        }
        out.add(name);
        out.add(size);
        out.add(Operator.getOperator("Tf"));
        state.emittedName = name;
        state.emittedSize = size;
    }

    /**
     * Encodes text with the given font, substituting the fallback character
     * for code points the font has no glyph for. Code points the fallback
     * also cannot represent are dropped (logged).
     */
    private static COSString encodeWithFallback(PDFont font, String text, char fallback) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String s = new String(Character.toChars(cp));
            try {
                bytes.write(font.encode(s));
            } catch (IllegalArgumentException | IOException missing) {
                try {
                    bytes.write(font.encode(String.valueOf(fallback)));
                    LOG.log(Level.WARNING, "No glyph for U+" + Integer.toHexString(cp).toUpperCase()
                            + " in " + font.getName() + "; substituted '" + fallback + "'");
                } catch (IllegalArgumentException | IOException unrepresentable) {
                    LOG.log(Level.WARNING, "Dropped U+" + Integer.toHexString(cp).toUpperCase()
                            + " — neither glyph nor fallback available in " + font.getName());
                }
            }
            i += Character.charCount(cp);
        }
        return new COSString(bytes.toByteArray());
    }

    private static PDFont resolveFont(PDResources resources, COSName name) {
        if (resources == null || name == null) {
            return null;
        }
        try {
            return resources.getFont(name);
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not resolve font " + name.getName(), e);
            return null;
        }
    }

    private static void addNormalizationFonts(PDResources resources, PDType0Font notoFont,
                                              boolean normalizeLatin) {
        if (normalizeLatin) {
            resources.put(HELV, PDType1Font.HELVETICA);
        }
        if (notoFont != null) {
            resources.put(NOTO_JP, notoFont);
        }
    }

    /**
     * Copies the resources dictionary, making shallow copies of the Font and
     * XObject subdictionaries so we can add/replace entries without mutating
     * the original.
     */
    private static PDResources copyResources(PDResources original) {
        if (original == null) {
            return new PDResources();
        }
        COSDictionary origDict = original.getCOSObject();
        COSDictionary newDict = new COSDictionary(origDict);

        copySubDictionary(origDict, newDict, COSName.FONT);
        copySubDictionary(origDict, newDict, COSName.XOBJECT);

        return new PDResources(newDict);
    }

    private static void copySubDictionary(COSDictionary source, COSDictionary target, COSName key) {
        COSBase value = source.getDictionaryObject(key);
        if (value instanceof COSDictionary) {
            target.setItem(key, new COSDictionary((COSDictionary) value));
        }
    }

    /**
     * Iterates Form XObjects in the resources, creating normalized copies
     * with rewritten content streams. Replaces the references in the
     * (already-copied) XObject subdictionary.
     */
    private static void normalizeFormXObjects(PDResources resources, PDType0Font notoFont,
                                              boolean normalizeLatin) throws IOException {
        if (resources == null) {
            return;
        }

        List<COSName> names = new ArrayList<>();
        for (COSName name : resources.getXObjectNames()) {
            names.add(name);
        }

        for (COSName name : names) {
            try {
                if (resources.getXObject(name) instanceof PDFormXObject) {
                    PDFormXObject original = (PDFormXObject) resources.getXObject(name);
                    PDFormXObject normalized = createNormalizedFormCopy(original, notoFont, normalizeLatin);
                    resources.put(name, normalized);
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not process XObject: " + name.getName(), e);
            }
        }
    }

    /**
     * Creates a new PDFormXObject with normalized text. The original form is
     * not modified.
     */
    private static PDFormXObject createNormalizedFormCopy(PDFormXObject original, PDType0Font notoFont,
                                                          boolean normalizeLatin) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(original);
        parser.parse();
        List<Object> rewritten = rewriteTokens(parser.getTokens(), original.getResources(),
                notoFont, normalizeLatin);

        PDFormXObject normalized = new PDFormXObject(new COSStream());
        try (OutputStream out = normalized.getCOSObject().createOutputStream()) {
            new ContentStreamWriter(out).writeTokens(rewritten);
        }

        PDRectangle bbox = original.getBBox();
        if (bbox != null) {
            normalized.setBBox(bbox);
        }
        COSArray matrix = original.getCOSObject().getCOSArray(COSName.MATRIX);
        if (matrix != null) {
            normalized.getCOSObject().setItem(COSName.MATRIX, matrix);
        }

        PDResources formResources = copyResources(original.getResources());
        addNormalizationFonts(formResources, notoFont, normalizeLatin);
        normalized.setResources(formResources);

        normalizeFormXObjects(normalized.getResources(), notoFont, normalizeLatin);

        return normalized;
    }
}
