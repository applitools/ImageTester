package com.applitools.imagetester.lib;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 * Rewrites PDF page content streams so text renders in normalization fonts,
 * producing a deterministic render regardless of the original font styling.
 * Each show-text run is decoded to Unicode, classified, and re-encoded:
 * Japanese runs go to bundled Noto Sans JP 12pt (when normalizeJapanese),
 * other runs to Helvetica 12pt (when normalizeLatin). Runs that are not
 * covered by an enabled flag, or that cannot be decoded, keep their original
 * font and bytes. The original PDPage and PDDocument are never modified.
 */
public class PdfFontNormalizer {

    private static final COSName HELV = COSName.getPDFName("Helv");
    private static final COSName NOTO_JP = COSName.getPDFName("NotoJP");
    private static final float NORMALIZED_FONT_SIZE = 12f;
    private static final float NORMALIZED_LEADING = NORMALIZED_FONT_SIZE * 1.2f;
    private static final char MISSING_JP_GLYPH = '〓'; // 〓 geta mark (escape survives any source encoding)
    private static final char MISSING_LATIN_GLYPH = '?';
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(PdfFontNormalizer.class.getName());

    /** Text-state snapshot: the active font plus what the OUTPUT stream currently has emitted. */
    private static final class FontState {
        PDFont font;
        COSName name;
        COSBase size;
        COSName emittedName;
        COSBase emittedSize;

        FontState copy() {
            FontState c = new FontState();
            c.font = font;
            c.name = name;
            c.size = size;
            c.emittedName = emittedName;
            c.emittedSize = emittedSize;
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
     *   Tf       — passed through (records the active font)
     *   TL / TD  — leading normalized to 14.4 (12pt * 1.2)
     *   q / Q    — text-state save/restore mirrored
     *   Tj ' " TJ — payload decoded, classified, re-encoded; corrective Tf inserted
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
            } else if ("TL".equals(op) && !operands.isEmpty()) {
                operands.set(operands.size() - 1, new COSFloat(NORMALIZED_LEADING));
            } else if ("TD".equals(op) && operands.size() >= 2) {
                // TD sets leading to -ty; tx (horizontal offset) is preserved as-is
                operands.set(1, new COSFloat(-NORMALIZED_LEADING));
            } else if ("q".equals(op)) {
                saved.push(state.copy());
            } else if ("Q".equals(op) && !saved.isEmpty()) {
                state = saved.pop();
            } else if (("Tj".equals(op) || "'".equals(op)) && !operands.isEmpty()) {
                rewriteShowString(out, operands, operands.size() - 1, state, notoFont, normalizeLatin);
            } else if ("\"".equals(op) && operands.size() >= 3) {
                rewriteShowString(out, operands, 2, state, notoFont, normalizeLatin);
            } else if ("TJ".equals(op) && !operands.isEmpty()) {
                rewriteShowArray(out, operands, state, notoFont, normalizeLatin);
            }
            out.addAll(operands);
            out.add(token);
            operands.clear();
        }
        out.addAll(operands);
        return out;
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

    private static void rewriteShowString(List<Object> out, List<Object> operands, int stringIndex,
                                          FontState state, PDType0Font notoFont, boolean normalizeLatin) {
        Object payload = operands.get(stringIndex);
        String decoded = payload instanceof COSString
                ? PdfTextDecoder.decode((COSString) payload, state.font)
                : null;
        COSName target = chooseTarget(decoded, notoFont, normalizeLatin);
        if (target == null) {
            ensureFont(out, state, state.name, state.size);
            return;
        }
        PDFont targetFont = NOTO_JP.equals(target) ? notoFont : PDType1Font.HELVETICA;
        char fallback = NOTO_JP.equals(target) ? MISSING_JP_GLYPH : MISSING_LATIN_GLYPH;
        operands.set(stringIndex, encodeWithFallback(targetFont, decoded, fallback));
        ensureFont(out, state, target, new COSFloat(NORMALIZED_FONT_SIZE));
    }

    private static void rewriteShowArray(List<Object> out, List<Object> operands,
                                         FontState state, PDType0Font notoFont, boolean normalizeLatin) {
        Object payload = operands.get(operands.size() - 1);
        if (!(payload instanceof COSArray)) {
            ensureFont(out, state, state.name, state.size);
            return;
        }
        COSArray array = (COSArray) payload;
        String decoded = PdfTextDecoder.decode(array, state.font);
        COSName target = chooseTarget(decoded, notoFont, normalizeLatin);
        if (target == null) {
            ensureFont(out, state, state.name, state.size);
            return;
        }
        PDFont targetFont = NOTO_JP.equals(target) ? notoFont : PDType1Font.HELVETICA;
        char fallback = NOTO_JP.equals(target) ? MISSING_JP_GLYPH : MISSING_LATIN_GLYPH;
        // Re-encode each string element; kerning numbers are preserved as-is
        for (int i = 0; i < array.size(); i++) {
            COSBase element = array.get(i);
            if (element instanceof COSString) {
                String part = PdfTextDecoder.decode((COSString) element, state.font);
                array.set(i, encodeWithFallback(targetFont, part, fallback));
            }
        }
        ensureFont(out, state, target, new COSFloat(NORMALIZED_FONT_SIZE));
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
