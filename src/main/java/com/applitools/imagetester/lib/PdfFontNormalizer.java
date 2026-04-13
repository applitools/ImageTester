package com.applitools.imagetester.lib;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Rewrites PDF page content streams to replace all font references with
 * Helvetica 12pt, producing a deterministic render regardless of original
 * font styling. The original PDPage and PDDocument are never modified.
 */
public class PdfFontNormalizer {

    private static final COSName HELV = COSName.getPDFName("Helv");
    private static final float NORMALIZED_FONT_SIZE = 12f;
    private static final float NORMALIZED_LEADING = NORMALIZED_FONT_SIZE * 1.2f;
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(PdfFontNormalizer.class.getName());

    private PdfFontNormalizer() {
    }

    /**
     * Creates a new PDPage with all Tf operators rewritten to Helvetica 12pt.
     * The original page and its parent document are not modified.
     *
     * The returned page is detached — add it to a PDDocument before rendering.
     */
    public static PDPage normalize(PDPage originalPage) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(originalPage);
        parser.parse();
        List<Object> tokens = parser.getTokens();

        rewriteFontOperators(tokens);

        PDPage normalizedPage = new PDPage(originalPage.getMediaBox());

        // Write modified content stream to a new COSStream (no PDDocument needed)
        COSStream contentStream = new COSStream();
        try (OutputStream out = contentStream.createOutputStream()) {
            new ContentStreamWriter(out).writeTokens(tokens);
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
            PDFRenderer renderer = new PDFRenderer(tempDoc);
            return renderer.renderImageWithDPI(0, dpi);
        }
    }

    /**
     * Walks the token list and normalizes font-related operators:
     *   Tf  — set font → /Helv 12
     *   TL  — set text leading → 14.4 (12pt * 1.2)
     *   TD  — move + set leading → preserve tx, set ty to -14.4
     */
    private static void rewriteFontOperators(List<Object> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (!(token instanceof Operator)) {
                continue;
            }
            String op = ((Operator) token).getName();
            if ("Tf".equals(op) && i >= 2) {
                tokens.set(i - 2, HELV);
                tokens.set(i - 1, new COSFloat(NORMALIZED_FONT_SIZE));
            } else if ("TL".equals(op) && i >= 1) {
                tokens.set(i - 1, new COSFloat(NORMALIZED_LEADING));
            } else if ("TD".equals(op) && i >= 2) {
                // TD sets leading to -ty, so normalize ty to -NORMALIZED_LEADING
                // tx (horizontal offset) is preserved as-is
                tokens.set(i - 1, new COSFloat(-NORMALIZED_LEADING));
            }
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
     * Creates a new PDFormXObject with Tf operators rewritten to Helvetica 12pt.
     * The original form is not modified.
     */
    private static PDFormXObject createNormalizedFormCopy(PDFormXObject original) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(original);
        parser.parse();
        List<Object> tokens = parser.getTokens();
        rewriteFontOperators(tokens);

        // Build a new form with rewritten content
        PDFormXObject normalized = new PDFormXObject(new COSStream());
        try (OutputStream out = normalized.getCOSObject().createOutputStream()) {
            new ContentStreamWriter(out).writeTokens(tokens);
        }

        // Copy geometry from original
        PDRectangle bbox = original.getBBox();
        if (bbox != null) {
            normalized.setBBox(bbox);
        }
        COSArray matrix = original.getCOSObject().getCOSArray(COSName.MATRIX);
        if (matrix != null) {
            normalized.getCOSObject().setItem(COSName.MATRIX, matrix);
        }

        // Set up resources with Helv
        PDResources formResources = copyResources(original.getResources());
        formResources.put(HELV, PDType1Font.HELVETICA);
        normalized.setResources(formResources);

        // Recurse into nested Form XObjects
        normalizeFormXObjects(normalized.getResources());

        return normalized;
    }
}
