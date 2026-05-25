package com.applitools.imagetester.lib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Implements the -rwauto runtime mode.
 *
 * Groups PDFs by their containing directory and computes a separate watermark
 * fingerprint per group, so heterogeneous inputs (e.g. one folder containing
 * pre/ and uat/ subfolders with different environment watermarks) clean
 * correctly. Each group needs at least 2 PDFs for the cross-document
 * intersection to be meaningful; groups smaller than that are skipped with a
 * notice.
 *
 * Output preserves the input's directory structure under outDir.
 */
public final class PdfVectorWatermarkAutoMode {

    static final String SINGLE_PDF_NOTICE =
        "\nHeads up: you're testing one PDF on its own." +
        "\n" +
        "\nImageTester removes watermarks by comparing PDFs against each other -- so it" +
        "\nneeds at least one other similar PDF to do its job." +
        "\n" +
        "\nTo remove the watermark from your PDF:" +
        "\n" +
        "\n  ->  Find any other PDF from the same source (any other report, invoice, or" +
        "\n      email from the same system works -- they don't need to match in content)." +
        "\n" +
        "\n  ->  Put both PDFs in a folder, and point ImageTester at the folder" +
        "\n      instead of the single file." +
        "\n" +
        "\n  ->  ImageTester will detect and remove the watermark from every PDF before" +
        "\n      uploading to Applitools." +
        "\n" +
        "\nIf you don't have another PDF to use, reach out to your Applitools support" +
        "\nengineer." +
        "\n" +
        "\nNothing uploaded. Re-run once you have at least 2 PDFs." +
        "\n";

    private PdfVectorWatermarkAutoMode() {
    }

    public static int run(File inputRoot, File outDir, String optionalTextHint, Logger logger) {
        if (!inputRoot.exists()) {
            logger.printMessage("ERROR: input path does not exist: " + inputRoot.getAbsolutePath());
            return 1;
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            logger.printMessage("ERROR: could not create output directory: " + outDir.getAbsolutePath());
            return 1;
        }

        Map<File, List<File>> groups = groupPdfsByDirectory(inputRoot);
        Map<File, List<File>> fingerprintable = new LinkedHashMap<>();
        for (Map.Entry<File, List<File>> entry : groups.entrySet()) {
            if (entry.getValue().size() >= 2) fingerprintable.put(entry.getKey(), entry.getValue());
        }
        if (fingerprintable.isEmpty()) {
            logger.printMessage(SINGLE_PDF_NOTICE);
            return 1;
        }

        int processed = 0;
        for (Map.Entry<File, List<File>> entry : fingerprintable.entrySet()) {
            File groupDir = entry.getKey();
            List<File> pdfs = entry.getValue();
            String groupLabel = groupLabel(inputRoot, groupDir);

            Set<String> pathFingerprint;
            Set<String> varyingOpSeqs;
            Set<String> varyingForms;
            try {
                pathFingerprint = PathFingerprinter.intersection(pdfs);
                varyingOpSeqs = OpSequenceVarianceFinder.findVarying(pdfs);
                varyingForms = VaryingFormFinder.findVarying(pdfs);
            } catch (IOException e) {
                logger.printMessage(String.format(
                        "Failed to compute watermark fingerprint for %s: %s",
                        groupLabel, e.getMessage()));
                continue;
            }
            logger.printMessage(String.format(
                    "[%s] Watermark fingerprint: %d shared shape(s), %d varying op-seq(s), %d varying form(s) across %d PDF(s)",
                    groupLabel, pathFingerprint.size(), varyingOpSeqs.size(), varyingForms.size(), pdfs.size()));

            for (File pdf : pdfs) {
                File output = resolveOutput(inputRoot, outDir, pdf);
                if (!output.getParentFile().exists() && !output.getParentFile().mkdirs()) {
                    logger.printMessage("Failed to create output directory: " + output.getParentFile().getAbsolutePath());
                    continue;
                }
                try {
                    cleanOnePdf(pdf, pathFingerprint, varyingOpSeqs, varyingForms, optionalTextHint, output);
                    processed++;
                } catch (IOException e) {
                    logger.printMessage("Failed to clean " + pdf.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
        logger.printMessage(String.format("Cleaned %d PDF(s) written to %s",
                processed, outDir.getAbsolutePath()));
        return 0;
    }

    private static void cleanOnePdf(File input, Set<String> pathFingerprint, Set<String> varyingOpSeqs,
                                     Set<String> varyingForms, String optionalTextHint, File output)
            throws IOException {
        boolean hasTextHint = optionalTextHint != null && !optionalTextHint.trim().isEmpty();
        try (PDDocument source = PDDocument.load(input);
             PDDocument cleaned = new PDDocument()) {
            for (int i = 0; i < source.getNumberOfPages(); i++) {
                PDPage page = source.getPage(i);
                if (hasTextHint) {
                    page = PdfWatermarkRemover.remove(page, optionalTextHint);
                }
                cleaned.addPage(page);
            }
            VectorWatermarkRemover.removeFromAllPages(cleaned, pathFingerprint, varyingOpSeqs);
            FormXObjectStripper.emptyForms(cleaned, varyingForms);
            cleaned.save(output);
        }
    }

    private static Map<File, List<File>> groupPdfsByDirectory(File root) {
        Map<File, List<File>> result = new LinkedHashMap<>();
        if (root.isFile()) {
            if (isPdf(root)) {
                File parent = root.getParentFile() != null ? root.getParentFile() : root;
                List<File> list = new ArrayList<>();
                list.add(root);
                result.put(parent, list);
            }
            return result;
        }
        walkAndGroup(root, result);
        return result;
    }

    private static void walkAndGroup(File dir, Map<File, List<File>> acc) {
        File[] children = dir.listFiles();
        if (children == null) return;
        List<File> directPdfs = new ArrayList<>();
        List<File> subdirs = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory()) subdirs.add(child);
            else if (isPdf(child)) directPdfs.add(child);
        }
        if (!directPdfs.isEmpty()) {
            Collections.sort(directPdfs);
            acc.put(dir, directPdfs);
        }
        Collections.sort(subdirs);
        for (File sub : subdirs) walkAndGroup(sub, acc);
    }

    private static boolean isPdf(File f) {
        return f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private static String groupLabel(File root, File groupDir) {
        Path rootPath = root.toPath().toAbsolutePath().normalize();
        Path groupPath = groupDir.toPath().toAbsolutePath().normalize();
        if (rootPath.equals(groupPath)) return rootPath.getFileName() != null ? rootPath.getFileName().toString() : ".";
        if (!groupPath.startsWith(rootPath)) return groupDir.getName();
        return rootPath.relativize(groupPath).toString().replace('\\', '/');
    }

    private static File resolveOutput(File inputRoot, File outDir, File pdf) {
        Path rootPath = (inputRoot.isFile() ? inputRoot.getParentFile() : inputRoot)
                .toPath().toAbsolutePath().normalize();
        Path pdfPath = pdf.toPath().toAbsolutePath().normalize();
        if (!pdfPath.startsWith(rootPath)) return new File(outDir, pdf.getName());
        Path relative = rootPath.relativize(pdfPath);
        return new File(outDir, relative.toString());
    }
}
