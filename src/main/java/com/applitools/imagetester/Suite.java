package com.applitools.imagetester;

import org.apache.commons.io.comparator.NameFileComparator;

import com.applitools.imagetester.BatchObjects.Batch;
import com.applitools.imagetester.BatchObjects.BatchBase;
import com.applitools.imagetester.BatchObjects.PDFFileBatch;
import com.applitools.imagetester.TestObjects.BatchMappedPdfFileTest;
import com.applitools.imagetester.TestObjects.FolderTest;
import com.applitools.imagetester.TestObjects.ImageFileTest;
import com.applitools.imagetester.TestObjects.PdfFileTest;
import com.applitools.imagetester.TestObjects.TestBase;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Patterns;
import com.applitools.imagetester.lib.TestExecutor;
import com.applitools.imagetester.lib.converters.ConversionRegistry;
import com.applitools.imagetester.lib.converters.FormatConverter;
import com.applitools.imagetester.lib.converters.LibreOfficeConverter;
import com.applitools.imagetester.lib.converters.MarkdownToPdfConverter;
import com.applitools.imagetester.lib.converters.RtfToPdfConverter;
import com.applitools.imagetester.lib.converters.SkippedFileException;
import com.applitools.imagetester.lib.converters.TxtToPdfConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class Suite {

    private static final ConversionRegistry REGISTRY = buildRegistry();

    private static ConversionRegistry buildRegistry() {
        ConversionRegistry r = new ConversionRegistry();
        r.register(new TxtToPdfConverter());
        r.register(new MarkdownToPdfConverter());
        r.register(new RtfToPdfConverter());
        r.register(new LibreOfficeConverter());
        return r;
    }

    private final TestExecutor executor_;
    private final List<TestBase> tests_ = new ArrayList<>();
    private final List<BatchBase> batches_ = new ArrayList<>();

    public static Suite create(File file, Config conf, TestExecutor executor) {
        if (is(file, Patterns.IMAGE))
            conf.splitSteps = true;
        return new Suite(file, conf, executor);
    }

    private Suite(File file, Config conf, TestExecutor executor) {
        conf.logger.reportDiscovery(file);
        executor_ = executor;

        if (!file.exists())
            throw new RuntimeException(
                    String.format("Fatal! The path %s does not exists \n", file.getAbsolutePath()));
        if (file.isDirectory())
            conf.logger.printMessage(String.format("Discovering folder: %s%n", file.getName()));
        try {
            if (file.isFile()) {
                if (conf.regexFileNameFilter != null
                    && !Pattern.matches(conf.regexFileNameFilter, file.getName())) {
                    return;
                }
                BatchBase batch = null;
                TestBase test = null;
                if (conf.splitSteps) {
                    if (is(file, Patterns.PDF)) {
                        batch = new PDFFileBatch(file, conf);
                    }
                    if (is(file, Patterns.IMAGE)) {
                        test = new ImageFileTest(file, conf);
                    }
                } else {
                    if (is(file, Patterns.PDF)) {
                        test = conf.batchMapperPath == null ? new PdfFileTest(file, conf) : new BatchMappedPdfFileTest(file, conf);
                    } else {
                        test = tryConvert(file, conf);
                    }
                }
                if (batch != null && !batch.isEmpty())
                    batches_.add(batch);
                if (test != null && !test.isEmpty())
                    tests_.add(test);
                return;
            } else if (!conf.splitSteps) {
                FolderTest test = new FolderTest(file, conf);
                if (!test.isEmpty())
                    tests_.add(test);
            }

            Batch currBatch = new Batch(file);

            File[] children = file.listFiles();
            if (!conf.legacyFileOrder)
                Arrays.sort(Objects.requireNonNull(children), NameFileComparator.NAME_COMPARATOR);

            for (File child : Objects.requireNonNull(children)) {
                Suite curr = new Suite(child, conf, executor);
                currBatch.addTests(curr.tests_);
                batches_.addAll(curr.batches_);
            }
            batches_.add(currBatch);
        } catch (Exception e) {
            conf.logger.reportException(e, file.getAbsolutePath());
        }
    }

    private static TestBase tryConvert(File file, Config conf) {
        Optional<FormatConverter> match = REGISTRY.find(file);
        if (!match.isPresent()) return null;
        try {
            conf.logger.printMessage(String.format("Converting %s to PDF...%n", file.getName()));
            Path tempDir = Files.createTempDirectory("imagetester-");
            tempDir.toFile().deleteOnExit();
            File pdfTemp = match.get().convertToPdf(file, tempDir);
            pdfTemp.deleteOnExit();
            return new PdfFileTest(pdfTemp, conf, file);
        } catch (SkippedFileException e) {
            conf.skipTracker.record(e.getFile(), e.getReason());
            conf.logger.printMessage(String.format("Skipping %s: %s",
                    e.getFile().getName(), e.getReason()));
            return null;
        } catch (IOException e) {
            conf.logger.reportException(e, file.getAbsolutePath());
            conf.skipTracker.record(file, "conversion error: " + e.getMessage());
            return null;
        }
    }

    public void run() {
        for (TestBase test : tests_)
            executor_.enqueue(test, null);
        for (BatchBase batch : batches_)
            batch.run(executor_);

        executor_.join();
    }

    private static boolean is(File file, Pattern pattern) {
        return pattern.matcher(file.getName()).matches();
    }
}
