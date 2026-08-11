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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Rewrites PDF page content streams so text renders in Helvetica 12pt,
 * producing a deterministic render regardless of the original font styling.
 * Each show-text run is decoded to Unicode through its own font's encoding,
 * then re-encoded for Helvetica. Runs that cannot be decoded keep their
 * original font and bytes; code points Helvetica cannot encode become '?'
 * (logged). The original PDPage and PDDocument are never modified.
 */
public class PdfFontNormalizer {

    private static final COSName HELV = COSName.getPDFName("Helv");
    private static final float NORMALIZED_FONT_SIZE = 12f;
    private static final float NORMALIZED_LEADING = NORMALIZED_FONT_SIZE * 1.2f;
    private static final char MISSING_GLYPH = '?';
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
     * Creates a new PDPage with all decodable text re-encoded for Helvetica
     * 12pt. The original page and its parent document are not modified.
     *
     * The returned page is detached - add it to a PDDocument before rendering.
     */
    public static PDPage normalize(PDPage originalPage) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(originalPage);
        parser.parse();
        List<Object> rewritten = rewriteTokens(parser.getTokens(), originalPage.getResources());

        PDPage normalizedPage = new PDPage(originalPage.getMediaBox());

        // Write modified content stream to a new COSStream (no PDDocument needed)
        COSStream contentStream = new COSStream();
        try (OutputStream out = contentStream.createOutputStream()) {
            new ContentStreamWriter(out).writeTokens(rewritten);
        }
        normalizedPage.getCOSObject().setItem(COSName.CONTENTS, contentStream);

        // Copy resources with isolated Font and XObject subdictionaries
        // so our additions don't mutate the original page's resources
        PDResources resources = copyResources(originalPage.getResources());
        resources.put(HELV, PDType1Font.HELVETICA);
        normalizedPage.setResources(resources);

        // Recursively normalize Form XObjects (creates copies, never mutates originals)
        normalizeFormXObjects(resources);

        return normalizedPage;
    }

    /**
     * Normalizes the page and renders it to a BufferedImage using a temporary
     * single-page PDDocument. The original page and its parent document are
     * not modified.
     */
    public static BufferedImage renderNormalized(PDPage originalPage, float dpi) throws IOException {
        PDPage normalizedPage = normalize(originalPage);
        try (PDDocument tempDoc = new PDDocument()) {
            tempDoc.addPage(normalizedPage);
            return new PDFRenderer(tempDoc).renderImageWithDPI(0, dpi);
        }
    }

    /**
     * Walks the token list and produces a rewritten copy:
     *   Tf       - passed through (records the active font)
     *   TL / TD  - leading normalized to 14.4 (12pt * 1.2)
     *   q / Q    - text-state save/restore mirrored
     *   Tj ' " TJ - payload decoded through the active font and re-encoded
     *               for Helvetica; a corrective Tf is inserted when needed.
     *               Undecodable payloads keep their original font and bytes.
     */
    private static List<Object> rewriteTokens(List<Object> tokens, PDResources resources) {
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
                rewriteShowString(out, operands, operands.size() - 1, state);
            } else if ("\"".equals(op) && operands.size() >= 3) {
                rewriteShowString(out, operands, 2, state);
            } else if ("TJ".equals(op) && !operands.isEmpty()) {
                rewriteShowArray(out, operands, state);
            }
            out.addAll(operands);
            out.add(token);
            operands.clear();
        }
        out.addAll(operands);
        return out;
    }

    private static void rewriteShowString(List<Object> out, List<Object> operands, int stringIndex,
                                          FontState state) {
        Object payload = operands.get(stringIndex);
        String decoded = payload instanceof COSString
                ? PdfTextDecoder.decode((COSString) payload, state.font)
                : null;
        if (decoded == null) {
            ensureFont(out, state, state.name, state.size);
            return;
        }
        operands.set(stringIndex, encodeWithFallback(PDType1Font.HELVETICA, decoded, MISSING_GLYPH));
        ensureFont(out, state, HELV, new COSFloat(NORMALIZED_FONT_SIZE));
    }

    private static void rewriteShowArray(List<Object> out, List<Object> operands, FontState state) {
        Object payload = operands.get(operands.size() - 1);
        if (!(payload instanceof COSArray)) {
            ensureFont(out, state, state.name, state.size);
            return;
        }
        COSArray array = (COSArray) payload;
        String decoded = PdfTextDecoder.decode(array, state.font);
        if (decoded == null) {
            ensureFont(out, state, state.name, state.size);
            return;
        }
        // Re-encode each string element; kerning numbers are preserved as-is
        for (int i = 0; i < array.size(); i++) {
            COSBase element = array.get(i);
            if (element instanceof COSString) {
                String part = PdfTextDecoder.decode((COSString) element, state.font);
                array.set(i, encodeWithFallback(PDType1Font.HELVETICA, part, MISSING_GLYPH));
            }
        }
        ensureFont(out, state, HELV, new COSFloat(NORMALIZED_FONT_SIZE));
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
                            + " - neither glyph nor fallback available in " + font.getName());
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
    private static void normalizeFormXObjects(PDResources resources) throws IOException {
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
                    PDFormXObject normalized = createNormalizedFormCopy(original);
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
    private static PDFormXObject createNormalizedFormCopy(PDFormXObject original) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(original);
        parser.parse();
        List<Object> rewritten = rewriteTokens(parser.getTokens(), original.getResources());

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
        formResources.put(HELV, PDType1Font.HELVETICA);
        normalized.setResources(formResources);

        normalizeFormXObjects(normalized.getResources());

        return normalized;
    }
}
