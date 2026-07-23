package com.applitools.imagetester.lib;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.exceptions.DiffsFoundException;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.BatchObjects.PDFFileBatch;
import com.applitools.imagetester.TestObjects.ImageFileTest;
import com.applitools.imagetester.TestObjects.IDisposable;
import com.applitools.imagetester.TestObjects.PdfFileTest;
import com.applitools.imagetester.TestObjects.TestBase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Runs exactly two documents against each other through Eyes: doc1's test(s) run and
 * close completely first (establishing/updating the baseline under Config.forcedName),
 * then doc2's test(s) run and are compared against whatever doc1 just saved. Reuses the
 * same per-file test objects the folder-scan path uses, so every Config-driven option
 * (regions, watermark, proxy, Selected Pages, enterprise downloads) applies unchanged.
 */
public final class CompareRunner {

    private CompareRunner() {}

    public static final class CompareResult {
        public final TestResults doc1Result;
        public final TestResults doc2Result;
        public CompareResult(TestResults doc1Result, TestResults doc2Result) {
            this.doc1Result = doc1Result;
            this.doc2Result = doc2Result;
        }
    }

    public static CompareResult run(File doc1, File doc2, Config config, EyesFactory factory) throws IOException {
        // GUI runs also precheck in RunController.start; this gate stays so the CLI -doc1/-doc2
        // path is covered — don't deduplicate.
        for (PdfComparePrechecker.Finding finding : PdfComparePrechecker.check(doc1, doc2, config)) {
            if (finding.severity == PdfComparePrechecker.Severity.ERROR) {
                throw new RuntimeException(finding.message);
            }
            config.logger.printMessage(String.format("Precheck %s: %s%n", finding.severity, finding.message));
        }

        validatePageRangeFits(doc1, config);
        validatePageRangeFits(doc2, config);

        boolean hasAccessibilityValidation = factory.hasAccessibilityValidation();

        config.logger.printMessage(String.format("Comparing Doc 1 (baseline): %s%n", doc1.getName()));
        TestResults doc1Result = runAllSequentially(buildTests(doc1, config), factory, config, hasAccessibilityValidation);

        config.logger.printMessage(String.format("Comparing Doc 2: %s%n", doc2.getName()));
        TestResults doc2Result = runAllSequentially(buildTests(doc2, config), factory, config, hasAccessibilityValidation);

        if (config.shouldThrowException && doc2Result != null && doc2Result.isDifferent()) {
            throw new DiffsFoundException(doc2Result, doc2Result.getId(), doc2Result.getName());
        }
        return new CompareResult(doc1Result, doc2Result);
    }

    private static void validatePageRangeFits(File doc, Config config) throws IOException {
        if (config.pages == null || !Patterns.PDF.matcher(doc.getName()).matches()) return;
        List<Integer> requested = Utils.parsePagesNotation(config.pages);
        if (requested == null || requested.isEmpty()) return;
        int maxRequested = requested.stream().mapToInt(Integer::intValue).max().orElse(0);
        int actualPages;
        try (PDDocument document = PDDocument.load(doc, config.pdfPass)) {
            actualPages = document.getNumberOfPages();
        }
        if (maxRequested > actualPages) {
            int deficit = maxRequested - actualPages;
            throw new RuntimeException(String.format(
                    "Selected pages (%s) requests page %d, but %s only has %d page(s) (short by %d page(s)).",
                    config.pages, maxRequested, doc.getName(), actualPages, deficit));
        }
    }

    /** Mirrors Suite.create()'s branching for a single file, scoped to images/PDFs only. */
    private static List<TestBase> buildTests(File doc, Config config) throws IOException {
        if (Patterns.IMAGE.matcher(doc.getName()).matches()) {
            return java.util.Collections.singletonList(new ImageFileTest(doc, config));
        }
        if (!Patterns.PDF.matcher(doc.getName()).matches()) {
            throw new RuntimeException(
                    "Unsupported file type for comparison: " + doc.getName() + " (only images and PDFs are supported)");
        }
        if (config.splitSteps) {
            return new ArrayList<>(new PDFFileBatch(doc, config).tests());
        }
        return java.util.Collections.singletonList(new PdfFileTest(doc, config));
    }

    /**
     * Same per-test boilerplate TestExecutor.enqueue() runs, just sequential and without the
     * thread pool. Also mirrors TestExecutor.join()'s reportResult/reportResultAccessibility
     * calls, which CompareRunner would otherwise silently skip by not going through TestExecutor
     * at all - the log pane would show nothing for either document without this.
     */
    private static TestResults runAllSequentially(List<TestBase> tests, EyesFactory factory, Config config, boolean hasAccessibilityValidation) {
        TestResults last = null;
        for (TestBase test : tests) {
            long startNanos = System.nanoTime();
            Eyes eyes = factory.build();
            TestResults result = test.runSafe(eyes);
            eyes.abortIfNotClosed();
            if (eyes.getBatch() != null) config.addBatchIdToCloseList(eyes.getBatch().getId());
            eyes.setBatch(null);
            if (test instanceof IDisposable) ((IDisposable) test).dispose();
            long endNanos = System.nanoTime();

            ExecutorResult er = new ExecutorResult(result, endNanos - startNanos);
            config.logger.reportResult(er);
            if (hasAccessibilityValidation) config.logger.reportResultAccessibility(er);

            last = result;
        }
        return last;
    }
}
