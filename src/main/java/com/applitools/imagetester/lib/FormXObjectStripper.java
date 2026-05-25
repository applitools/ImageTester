package com.applitools.imagetester.lib;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

/**
 * Empties the content stream of named FormXObjects on every page of a
 * document. After stripping, any `Do` operator that draws one of these forms
 * paints nothing — leaving the page's other geometry, text, and form references
 * intact.
 */
public final class FormXObjectStripper {

    private FormXObjectStripper() {
    }

    public static void emptyForms(PDDocument doc, Set<String> formNames) throws IOException {
        if (formNames == null || formNames.isEmpty()) return;
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            PDResources resources = page.getResources();
            if (resources == null) continue;
            for (COSName name : resources.getXObjectNames()) {
                if (!formNames.contains(name.getName())) continue;
                PDXObject xo = resources.getXObject(name);
                if (xo instanceof PDFormXObject) {
                    emptyContentStream((PDFormXObject) xo);
                }
            }
        }
    }

    private static void emptyContentStream(PDFormXObject form) throws IOException {
        try (OutputStream out = form.getCOSObject().createOutputStream()) {
            // intentionally write nothing
        }
    }
}
