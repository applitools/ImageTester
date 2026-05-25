package com.applitools.imagetester.lib;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Computes the cross-document intersection of path-shape hashes. Returns the
 * set of hashes whose paths appear in every input PDF — the watermark template
 * fingerprint. Paths unique to any one document drop out of the intersection.
 */
public final class PathFingerprinter {

    private PathFingerprinter() {
    }

    public static Set<String> intersection(List<File> pdfs) throws IOException {
        if (pdfs.isEmpty()) return new HashSet<>();
        Set<String> fingerprint = null;
        for (File pdf : pdfs) {
            Set<String> hashes = hashesIn(pdf);
            if (fingerprint == null) {
                fingerprint = hashes;
            } else {
                fingerprint.retainAll(hashes);
            }
        }
        return fingerprint;
    }

    private static Set<String> hashesIn(File pdf) throws IOException {
        Set<String> hashes = new HashSet<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDPage page = doc.getPage(i);
                PDFStreamParser parser = new PDFStreamParser(page);
                parser.parse();
                hashes.addAll(PathFingerprint.hashesFor(parser.getTokens()));
            }
        }
        return hashes;
    }
}
