package com.applitools.imagetester.lib;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.exceptions.DiffsFoundException;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.BatchObjects.PDFFileBatch;
import com.applitools.imagetester.TestObjects.ImageFileTest;
import com.applitools.imagetester.TestObjects.IDisposable;
import com.applitools.imagetester.TestObjects.PdfFileTest;
import com.applitools.imagetester.TestObjects.TestBase;
import com.applitools.imagetester.lib.converters.ConversionRegistry;
import com.applitools.imagetester.lib.converters.PdfConversion;
import com.applitools.imagetester.lib.converters.SkipTracker;
import com.applitools.imagetester.lib.converters.SkippedFileException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        return run(doc1, doc2, config, factory, () -> false);
    }

    /**
     * Cancellation is soft: {@code isCancelled} is polled between tests and between documents,
     * never mid-test — interrupting an in-flight Eyes call corrupts the shared universal-core
     * session and breaks the next run.
     */
    public static CompareResult run(File doc1, File doc2, Config config, EyesFactory factory,
                                    java.util.function.BooleanSupplier isCancelled) throws IOException {
        return run(doc1, doc2, config, factory, isCancelled, PdfConversion.defaultRegistry());
    }

    /** {@code registry} is injectable so tests can simulate converter outcomes (e.g. LibreOffice missing). */
    static CompareResult run(File doc1, File doc2, Config config, EyesFactory factory,
                             java.util.function.BooleanSupplier isCancelled,
                             ConversionRegistry registry) throws IOException {
        // Office/text documents are converted to PDF up front so every later step (precheck,
        // page-range validation, the test objects) sees the same inputs the folder-scan path
        // would. Images and PDFs pass through untouched.
        File input1 = toComparable("Doc 1", doc1, config, registry);
        File input2 = toComparable("Doc 2", doc2, config, registry);

        // GUI runs also precheck in RunController.start; this gate stays so the CLI -doc1/-doc2
        // path is covered -- don't deduplicate.
        for (PdfComparePrechecker.Finding finding
                : PdfComparePrechecker.check(input1, input2, config, PdfComparePrechecker.MessageStyle.CLI)) {
            if (finding.severity == PdfComparePrechecker.Severity.ERROR) {
                throw new RuntimeException(finding.message);
            }
            config.logger.printMessage(String.format("Precheck %s: %s%n", finding.severity, finding.message));
        }

        validatePageRangeFits(input1, config);
        validatePageRangeFits(input2, config);

        boolean hasAccessibilityValidation = factory.hasAccessibilityValidation();

        if (isCancelled.getAsBoolean()) return new CompareResult(null, null);

        config.logger.printMessage(String.format("Comparing Doc 1 (baseline): %s%n", doc1.getName()));
        TestResults doc1Result = runAllSequentially(buildTests(input1, doc1, config), factory, config, hasAccessibilityValidation, isCancelled);

        if (isCancelled.getAsBoolean()) {
            config.logger.printMessage(String.format("Run cancelled — skipping Doc 2%n"));
            return new CompareResult(doc1Result, null);
        }

        config.logger.printMessage(String.format("Comparing Doc 2: %s%n", doc2.getName()));
        TestResults doc2Result = runAllSequentially(buildTests(input2, doc2, config), factory, config, hasAccessibilityValidation, isCancelled);

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

    /**
     * Returns the file the test objects should actually open: the input itself for images and
     * PDFs, a converted temp PDF for everything the folder-scan path can convert. Unlike Suite,
     * which records a skip and moves on to the next file, compare mode has nothing to move on
     * to - a document that can't be converted fails the run with the reason (and the install
     * hint when the reason is a missing LibreOffice).
     */
    private static File toComparable(String label, File doc, Config config, ConversionRegistry registry)
            throws IOException {
        String name = doc.getName();
        if (Patterns.IMAGE.matcher(name).matches() || Patterns.PDF.matcher(name).matches()) return doc;
        Optional<File> converted;
        try {
            converted = PdfConversion.convertToTempPdf(doc, registry, config.logger);
        } catch (SkippedFileException e) {
            // Not recorded in skipTracker: the exception carries the reason, and the run
            // ends here, so the end-of-run skip summary would never print anyway.
            String message = String.format("%s (%s) could not be converted to PDF: %s", label, name, e.getReason());
            if (SkipTracker.REASON_LIBREOFFICE_MISSING.equals(e.getReason())) {
                message += "\n" + SkipTracker.LIBREOFFICE_INSTALL_HINT;
            }
            throw new RuntimeException(message, e);
        }
        if (!converted.isPresent()) {
            throw new RuntimeException("Unsupported file type for comparison: " + name
                    + " (supported: images, PDFs, and documents that can be converted to PDF"
                    + " such as DOC/DOCX, PPT/PPTX, XLS/XLSX, TXT, MD and RTF)");
        }
        return converted.get();
    }

    /**
     * Mirrors Suite.create()'s branching for a single file. {@code input} is what gets opened
     * (image, PDF, or converted temp PDF); {@code source} is the user's original file so test
     * names and the Filename property report "quote.docx", not the temp PDF.
     */
    private static List<TestBase> buildTests(File input, File source, Config config) throws IOException {
        if (Patterns.IMAGE.matcher(input.getName()).matches()) {
            return java.util.Collections.singletonList(new ImageFileTest(input, config));
        }
        if (config.splitSteps) {
            return new ArrayList<>(new PDFFileBatch(input, config).tests());
        }
        return java.util.Collections.singletonList(new PdfFileTest(input, config, source));
    }

    /**
     * Same per-test boilerplate TestExecutor.enqueue() runs, just sequential and without the
     * thread pool. Also mirrors TestExecutor.join()'s reportResult/reportResultAccessibility
     * calls, which CompareRunner would otherwise silently skip by not going through TestExecutor
     * at all - the log pane would show nothing for either document without this.
     */
    private static TestResults runAllSequentially(List<TestBase> tests, EyesFactory factory, Config config,
                                                  boolean hasAccessibilityValidation,
                                                  java.util.function.BooleanSupplier isCancelled) {
        TestResults last = null;
        for (TestBase test : tests) {
            if (isCancelled.getAsBoolean()) break;
            long startNanos = System.nanoTime();
            Eyes eyes = factory.build();
            TestResults result = test.runSafe(eyes);
            // runSafe already aborted a cancelled test asynchronously; the synchronous
            // abort here would block on the core's never-arriving results.
            if (!isCancelled.getAsBoolean()) Utils.abortQuietly(eyes, config.logger);
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
