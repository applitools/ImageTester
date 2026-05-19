package com.applitools.imagetester.lib;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Strips drawn paths from a PDDocument whose position-invariant shape hash
 * appears in a supplied fingerprint set. Intended to receive a fingerprint
 * produced by {@link PathFingerprinter#intersection(java.util.List)} from a
 * batch of sample PDFs.
 */
public final class VectorWatermarkRemover {

    private static final Set<String> PATH_OPS = new HashSet<>(Arrays.asList(
            "m", "l", "c", "v", "y", "h", "re"));
    private static final Set<String> PAINT_OPS = new HashSet<>(Arrays.asList(
            "S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "n"));

    private VectorWatermarkRemover() {
    }

    public static void removeFromAllPages(PDDocument doc, Set<String> fingerprint) throws IOException {
        removeFromAllPages(doc, fingerprint, java.util.Collections.<String>emptySet());
    }

    public static void removeFromAllPages(PDDocument doc, Set<String> coordHashes, Set<String> opSeqHashes)
            throws IOException {
        if ((coordHashes == null || coordHashes.isEmpty()) && (opSeqHashes == null || opSeqHashes.isEmpty())) return;
        Set<String> coordSet = coordHashes == null ? java.util.Collections.<String>emptySet() : coordHashes;
        Set<String> opSeqSet = opSeqHashes == null ? java.util.Collections.<String>emptySet() : opSeqHashes;
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            PDFStreamParser parser = new PDFStreamParser(page);
            parser.parse();
            List<Object> cleaned = stripFingerprintedPaths(parser.getTokens(), coordSet, opSeqSet);

            COSStream newStream = new COSStream();
            try (OutputStream out = newStream.createOutputStream()) {
                new ContentStreamWriter(out).writeTokens(cleaned);
            }
            page.getCOSObject().setItem(COSName.CONTENTS, newStream);
        }
    }

    private static List<Object> stripFingerprintedPaths(List<Object> tokens,
                                                        Set<String> coordHashes,
                                                        Set<String> opSeqHashes) {
        List<Object> result = new ArrayList<>();
        List<Object> currentPath = new ArrayList<>();
        List<Object> argBuffer = new ArrayList<>();
        boolean inPath = false;

        for (Object t : tokens) {
            if (t instanceof Operator) {
                String op = ((Operator) t).getName();
                if (PATH_OPS.contains(op)) {
                    currentPath.addAll(argBuffer);
                    currentPath.add(t);
                    argBuffer.clear();
                    inPath = true;
                } else if (PAINT_OPS.contains(op) && inPath) {
                    currentPath.addAll(argBuffer);
                    currentPath.add(t);
                    argBuffer.clear();

                    List<PathFingerprint.PathHashes> hashes = PathFingerprint.pathHashesFor(currentPath);
                    boolean strip = !hashes.isEmpty() && (
                            coordHashes.contains(hashes.get(0).coordHash)
                            || opSeqHashes.contains(hashes.get(0).opSeqHash));
                    if (!strip) {
                        result.addAll(currentPath);
                    }
                    currentPath.clear();
                    inPath = false;
                } else {
                    if (inPath) {
                        result.addAll(currentPath);
                        currentPath.clear();
                        inPath = false;
                    }
                    result.addAll(argBuffer);
                    result.add(t);
                    argBuffer.clear();
                }
            } else {
                argBuffer.add(t);
            }
        }
        result.addAll(argBuffer);
        return result;
    }
}
