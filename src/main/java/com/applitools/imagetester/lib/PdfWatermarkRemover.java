package com.applitools.imagetester.lib;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Removes text watermarks from PDF page content streams. The original PDPage,
 * its parent PDDocument, and the on-disk file are never modified.
 *
 * Match semantics: case-insensitive equality after trimming whitespace.
 */
public class PdfWatermarkRemover {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(PdfWatermarkRemover.class.getName());

    private PdfWatermarkRemover() {
    }

    /**
     * Returns a detached PDPage with text-show operators whose decoded payload
     * matches the hint removed. The original page is not modified.
     */
    public static PDPage remove(PDPage originalPage, String hint) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(originalPage);
        parser.parse();
        List<Object> tokens = parser.getTokens();

        removeMatchingTextOperators(tokens, originalPage.getResources(), hint);

        PDPage cleanedPage = new PDPage(originalPage.getMediaBox());

        COSStream contentStream = new COSStream();
        try (OutputStream out = contentStream.createOutputStream()) {
            new ContentStreamWriter(out).writeTokens(tokens);
        }
        cleanedPage.getCOSObject().setItem(COSName.CONTENTS, contentStream);

        cleanedPage.setResources(copyResources(originalPage.getResources()));
        removeFromFormXObjects(cleanedPage.getResources(), hint);

        return cleanedPage;
    }

    /**
     * Removes watermarks from the page and renders it to a BufferedImage using a temporary
     * single-page PDDocument. The original page and its parent document are not modified.
     */
    public static BufferedImage renderCleaned(PDPage originalPage, String hint, float dpi) throws IOException {
        PDPage cleanedPage = remove(originalPage, hint);
        try (PDDocument tempDoc = new PDDocument()) {
            tempDoc.addPage(cleanedPage);
            PDFRenderer renderer = new PDFRenderer(tempDoc);
            return renderer.renderImageWithDPI(0, dpi);
        }
    }

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

    private static void removeMatchingTextOperators(List<Object> tokens,
                                                    PDResources resources,
                                                    String hint) {
        PDFont currentFont = null;
        List<Integer> indicesToRemove = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (!(token instanceof Operator)) {
                continue;
            }
            String op = ((Operator) token).getName();
            if ("Tf".equals(op) && i >= 2) {
                currentFont = resolveFont(resources, tokens.get(i - 2));
            } else if ("Tj".equals(op) && i >= 1 && currentFont != null) {
                String decoded = PdfTextDecoder.decode(asCosString(tokens.get(i - 1)), currentFont);
                if (matches(decoded, hint)) {
                    indicesToRemove.add(i - 1);
                    indicesToRemove.add(i);
                }
            } else if ("TJ".equals(op) && i >= 1 && currentFont != null) {
                String decoded = PdfTextDecoder.decode(asCosArray(tokens.get(i - 1)), currentFont);
                if (matches(decoded, hint)) {
                    indicesToRemove.add(i - 1);
                    indicesToRemove.add(i);
                }
            } else if ("'".equals(op) && i >= 1 && currentFont != null) {
                String decoded = PdfTextDecoder.decode(asCosString(tokens.get(i - 1)), currentFont);
                if (matches(decoded, hint)) {
                    indicesToRemove.add(i - 1);
                    indicesToRemove.add(i);
                }
            } else if ("\"".equals(op) && i >= 3 && currentFont != null) {
                String decoded = PdfTextDecoder.decode(asCosString(tokens.get(i - 1)), currentFont);
                if (matches(decoded, hint)) {
                    indicesToRemove.add(i - 3);
                    indicesToRemove.add(i - 2);
                    indicesToRemove.add(i - 1);
                    indicesToRemove.add(i);
                }
            }
        }
        removeIndicesDescending(tokens, indicesToRemove);
    }

    private static PDFont resolveFont(PDResources resources, Object fontNameToken) {
        if (resources == null || !(fontNameToken instanceof COSName)) {
            return null;
        }
        try {
            return resources.getFont((COSName) fontNameToken);
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not resolve font", e);
            return null;
        }
    }

    private static COSString asCosString(Object token) {
        return token instanceof COSString ? (COSString) token : null;
    }

    private static COSArray asCosArray(Object token) {
        return token instanceof COSArray ? (COSArray) token : null;
    }

    private static boolean matches(String decoded, String hint) {
        return decoded != null && decoded.trim().equalsIgnoreCase(hint.trim());
    }

    private static void removeIndicesDescending(List<Object> tokens, List<Integer> indices) {
        for (int i = indices.size() - 1; i >= 0; i--) {
            tokens.remove((int) indices.get(i));
        }
    }

    private static void removeFromFormXObjects(PDResources resources, String hint) throws IOException {
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
                    PDFormXObject cleaned = createCleanedFormCopy(original, hint);
                    resources.put(name, cleaned);
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not process XObject: " + name.getName(), e);
            }
        }
    }

    private static PDFormXObject createCleanedFormCopy(PDFormXObject original, String hint) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(original);
        parser.parse();
        List<Object> tokens = parser.getTokens();
        removeMatchingTextOperators(tokens, original.getResources(), hint);

        PDFormXObject cleaned = new PDFormXObject(new COSStream());
        try (OutputStream out = cleaned.getCOSObject().createOutputStream()) {
            new ContentStreamWriter(out).writeTokens(tokens);
        }
        if (original.getBBox() != null) {
            cleaned.setBBox(original.getBBox());
        }
        org.apache.pdfbox.cos.COSArray matrix =
                original.getCOSObject().getCOSArray(COSName.MATRIX);
        if (matrix != null) {
            cleaned.getCOSObject().setItem(COSName.MATRIX, matrix);
        }

        PDResources formResources = copyResources(original.getResources());
        cleaned.setResources(formResources);

        removeFromFormXObjects(cleaned.getResources(), hint);

        return cleaned;
    }
}
