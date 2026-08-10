package com.applitools.imagetester.lib.testdata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the full -nf corpus to disk for manual inspection or a real Eyes
 * run. The JUnit suites build the same shapes through NfTestPdfBuilder into
 * temporary folders; this main() is the human-facing path.
 */
public final class NfCorpusWriter {

    private static final String DEFAULT_OUTPUT_DIR = "target/nf-corpus";

    private NfCorpusWriter() {
    }

    public static void main(String[] args) {
        File dir = new File(args.length > 0 ? args[0] : DEFAULT_OUTPUT_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Could not create output directory: " + dir.getAbsolutePath());
            System.exit(1);
        }
        try {
            List<File> written = writeAll(dir);
            for (File f : written) {
                System.out.println(f.getPath());
            }
            System.out.println("Wrote " + written.size() + " PDFs to " + dir.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Corpus generation failed: " + e.getMessage());
            System.exit(1);
        }
    }

    public static List<File> writeAll(File dir) throws IOException {
        List<File> files = new ArrayList<>();
        files.add(NfTestPdfBuilder.createInvoice(dir, "invoice-a.pdf", NfTestPdfBuilder.THEME_A));
        files.add(NfTestPdfBuilder.createInvoice(dir, "invoice-b.pdf", NfTestPdfBuilder.THEME_B));
        files.add(NfTestPdfBuilder.createReport(dir, "report-a.pdf", NfTestPdfBuilder.THEME_A));
        files.add(NfTestPdfBuilder.createReport(dir, "report-b.pdf", NfTestPdfBuilder.THEME_B));
        files.add(NfTestPdfBuilder.createLetter(dir, "letter-a.pdf", NfTestPdfBuilder.THEME_A));
        files.add(NfTestPdfBuilder.createLetter(dir, "letter-b.pdf", NfTestPdfBuilder.THEME_B));
        files.add(NfTestPdfBuilder.createEncodingMismatchWinAnsi(dir, "encoding-mismatch-a.pdf"));
        files.add(NfTestPdfBuilder.createEncodingMismatchIdentityH(dir, "encoding-mismatch-b.pdf"));
        files.add(NfTestPdfBuilder.createRotated(dir, "rotated.pdf"));
        files.add(NfTestPdfBuilder.createCropBoxed(dir, "cropbox.pdf"));
        files.add(NfTestPdfBuilder.createSubsetIdentityH(dir, "subset-identity-h.pdf"));
        files.add(NfTestPdfBuilder.createDifferencesEncoded(dir, "differences-encoding.pdf"));
        files.add(NfTestPdfBuilder.createHelloWinAnsi(dir, "differences-control.pdf"));
        files.add(NfTestPdfBuilder.createSpacingDoc(dir, "spacing-plain.pdf", false));
        files.add(NfTestPdfBuilder.createSpacingDoc(dir, "spacing-ops.pdf", true));
        return files;
    }
}
