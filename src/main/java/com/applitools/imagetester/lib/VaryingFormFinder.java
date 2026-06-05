package com.applitools.imagetester.lib;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

/**
 * Identifies FormXObject resource names that look like a watermark stamp:
 * the same form content drawn at different positions across the input PDFs.
 *
 * A form qualifies as a watermark stamp only when BOTH conditions hold across
 * every PDF that references it:
 *   1. The form's content stream is byte-identical (same drawn artwork), and
 *   2. The placement matrix (`cm` preceding `Do`) differs between at least two
 *      documents.
 *
 * Forms whose content differs per document are per-PDF template machinery
 * (e.g., a card whose height is regenerated to fit a varying email body) and
 * are deliberately excluded. Forms drawn at the same position in every doc are
 * static chrome (logos, headers).
 *
 * Identity is by resource name, which assumes the input batch comes from the
 * same source/template.
 */
public final class VaryingFormFinder {

    private VaryingFormFinder() {
    }

    public static Set<String> findVarying(List<File> pdfs) throws IOException {
        if (pdfs.size() < 2) return Collections.emptySet();

        List<Map<String, FormUsage>> perDoc = new ArrayList<>();
        Set<String> allFormNames = new HashSet<>();
        for (File pdf : pdfs) {
            Map<String, FormUsage> usage = usageIn(pdf);
            perDoc.add(usage);
            allFormNames.addAll(usage.keySet());
        }

        Set<String> stamps = new HashSet<>();
        for (String formName : allFormNames) {
            if (looksLikeStamp(formName, perDoc)) stamps.add(formName);
        }
        return stamps;
    }

    private static boolean looksLikeStamp(String formName, List<Map<String, FormUsage>> perDoc) {
        String contentHash = null;
        Set<String> referencePlacements = null;
        boolean positionVariesAcrossDocs = false;

        for (Map<String, FormUsage> docMap : perDoc) {
            FormUsage usage = docMap.get(formName);
            if (usage == null) return false;

            // Condition 1: identical drawn content across every doc
            if (contentHash == null) contentHash = usage.contentHash;
            else if (!contentHash.equals(usage.contentHash)) return false;

            // Condition 2: within each doc the form must appear at exactly one
            // distinct cm position. Template elements (section dividers, content
            // containers) are placed at many positions within a single document as
            // the content flows, while a watermark stamp is anchored to a fixed
            // page location and therefore appears at only one position per doc.
            if (usage.cmKeys.size() != 1) return false;

            // Condition 3: that single position must differ between at least two docs
            if (referencePlacements == null) {
                referencePlacements = usage.cmKeys;
            } else if (!referencePlacements.equals(usage.cmKeys)) {
                positionVariesAcrossDocs = true;
            }
        }

        return positionVariesAcrossDocs;
    }

    private static Map<String, FormUsage> usageIn(File pdf) throws IOException {
        Map<String, FormUsage> result = new HashMap<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                PDFStreamParser parser = new PDFStreamParser(doc.getPage(p));
                parser.parse();
                Map<String, Set<String>> sites = new HashMap<>();
                for (FormStampLocator.StampSite site : FormStampLocator.locate(parser.getTokens())) {
                    sites.computeIfAbsent(site.formName, k -> new HashSet<>()).add(cmKey(site.cm));
                }

                PDResources res = doc.getPage(p).getResources();
                if (res == null) continue;
                for (Map.Entry<String, Set<String>> entry : sites.entrySet()) {
                    String hash = contentHashOf(res, entry.getKey());
                    if (hash == null) continue;
                    FormUsage existing = result.get(entry.getKey());
                    if (existing == null) {
                        result.put(entry.getKey(), new FormUsage(hash, new HashSet<>(entry.getValue())));
                    } else if (existing.contentHash.equals(hash)) {
                        existing.cmKeys.addAll(entry.getValue());
                    }
                    // else: same resource name on different pages maps to different content
                    // (page-specific forms re-using a slot name); treat as not a stamp.
                }
            }
        }
        return result;
    }

    private static String contentHashOf(PDResources resources, String formName) throws IOException {
        PDXObject xo = resources.getXObject(COSName.getPDFName(formName));
        if (!(xo instanceof PDFormXObject)) return null;
        COSStream stream = ((PDFormXObject) xo).getCOSObject();
        try (InputStream in = stream.createInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return sha256(out.toByteArray());
        }
    }

    private static String cmKey(float[] cm) {
        StringBuilder sb = new StringBuilder();
        for (float v : cm) {
            sb.append(String.format(Locale.ROOT, "%.2f ", v));
        }
        return sb.toString();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class FormUsage {
        final String contentHash;
        final Set<String> cmKeys;

        FormUsage(String contentHash, Set<String> cmKeys) {
            this.contentHash = contentHash;
            this.cmKeys = cmKeys;
        }
    }
}
