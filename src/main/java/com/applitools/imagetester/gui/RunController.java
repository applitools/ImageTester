package com.applitools.imagetester.gui;

import com.applitools.imagetester.ImageTester;
import com.applitools.imagetester.Suite;
import com.applitools.imagetester.lib.CompareRunner;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.PdfVectorWatermarkAutoMode;
import com.applitools.imagetester.lib.PdfWatermarkOutMode;
import com.applitools.imagetester.lib.RunConfig;
import com.applitools.imagetester.lib.RunConfigFactory;
import com.applitools.imagetester.lib.TestExecutor;
import com.applitools.imagetester.lib.Utils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class RunController {

    public static final class StartResult {
        public final String runId;
        public StartResult(String runId) { this.runId = runId; }
    }

    public static final class RunInProgressException extends RuntimeException {
        public RunInProgressException() { super("A run is already in progress."); }
    }

    public static final class MissingApiKeyException extends RuntimeException {
        public MissingApiKeyException() { super("No Applitools API key configured."); }
    }

    public static final class InvalidOptionsException extends RuntimeException {
        public InvalidOptionsException(String msg) { super(msg); }
    }

    /** Builds a RunConfig for one run from the full RunRequest. Allows tests to inject mocks. */
    @FunctionalInterface
    public interface RunConfigBuilder {
        RunConfig build(RunRequest req, Logger logger);
    }

    static RunConfig buildRunConfig(RunRequest req, Logger logger) {
        try {
            CommandLine cmd = new DefaultParser().parse(ImageTester.getOptions(),
                    RunRequestTranslator.toArgv(req));
            // Mirror main()'s -dv side effect (main applies it before the config mapping).
            if (cmd.hasOption("dv")) Utils.disableCertValidation();
            return RunConfigFactory.from(cmd, logger);
        } catch (ParseException e) {
            throw new InvalidOptionsException(e.getMessage());
        } catch (java.security.NoSuchAlgorithmException | java.security.KeyManagementException e) {
            throw new InvalidOptionsException("Could not disable SSL validation: " + e.getMessage());
        }
    }

    private static final RunConfigBuilder PRODUCTION_BUILDER = RunController::buildRunConfig;

    private final SecretsStore secrets_;
    private final RunStream runStream_;
    private final RunConfigBuilder factoryBuilder_;
    private final AtomicReference<RunState> state_ = new AtomicReference<>(RunState.Idle.INSTANCE);
    private final AtomicReference<TestExecutor> currentExecutor_ = new AtomicReference<>();
    private final AtomicReference<Path> currentSourceRoot_ = new AtomicReference<>();
    // Compare mode has no single root to prefix-check (doc1/doc2 can live in unrelated
    // directories), so it authorizes previews by exact canonical file path instead.
    private final AtomicReference<Set<Path>> currentComparePaths_ = new AtomicReference<>();
    private final ExecutorService runExecutor_ = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RunController-run");
        t.setDaemon(true);
        return t;
    });

    public RunController(SecretsStore secrets, RunStream runStream) {
        this(secrets, runStream, PRODUCTION_BUILDER);
    }

    public RunController(SecretsStore secrets, RunStream runStream, RunConfigBuilder factoryBuilder) {
        this.secrets_ = secrets;
        this.runStream_ = runStream;
        this.factoryBuilder_ = factoryBuilder;
    }

    public RunState snapshot() { return state_.get(); }
    public RunStream stream() { return runStream_; }
    public SecretsStore secrets() { return secrets_; }
    /** Root of the most recently validated source path, so the preview endpoint can reject paths outside it. */
    public Path sourceRoot() { return currentSourceRoot_.get(); }
    /** Exact canonical doc1/doc2 paths for the most recent compare-mode run, so the preview
     *  endpoint can authorize them without a shared root (they may live in unrelated directories). */
    public Set<Path> compareModePaths() { return currentComparePaths_.get(); }

    public void setSecretApiKey(String value) { secrets_.setApiKey(value); }

    public StartResult start(RunRequest req) {
        boolean compareMode = req.doc1Path != null && req.doc2Path != null;
        Path validatedDoc1 = compareMode ? SourcePathValidator.validate(req.doc1Path) : null;
        Path validatedDoc2 = compareMode ? SourcePathValidator.validate(req.doc2Path) : null;
        Path validated = compareMode ? null : SourcePathValidator.validate(req.sourcePath);
        String apiKey = secrets_.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) throw new MissingApiKeyException();

        validateWatermarkFlags(req); // throws InvalidOptionsException synchronously
        validateCompareModeFlags(req, compareMode); // throws InvalidOptionsException synchronously

        Logger logger = new Logger();
        RunConfig rc = factoryBuilder_.build(req, logger); // throws on malformed options synchronously

        if (compareMode) failOnPrecheckErrors(validatedDoc1.toFile(), validatedDoc2.toFile(), rc.config);

        RunState.Running running = new RunState.Running();
        // Race-safe transition: only one caller can win; stale Done is also replaced atomically.
        while (true) {
            RunState current = state_.get();
            if (current instanceof RunState.Running) throw new RunInProgressException();
            if (state_.compareAndSet(current, running)) break;
        }

        // Wipe SSE replay history so a tab that connects mid-run only sees events from *this* run.
        runStream_.resetReplay();
        // Emitted before the HTTP response returns so the frontend can enter "running" from the
        // stream itself instead of racing its optimistic dispatch against test-started.
        runStream_.emit(new SseEvent.RunStarted(running.runId));
        if (compareMode) {
            runExecutor_.submit(() -> executeCompareRun(validatedDoc1.toFile(), validatedDoc2.toFile(), apiKey, rc, logger, running));
        } else {
            runExecutor_.submit(() -> executeRun(validated.toFile(), req, apiKey, rc, logger, running));
        }
        return new StartResult(running.runId);
    }

    private static void validateWatermarkFlags(RunRequest req) {
        if (req.options == null) return;
        Object rwo = req.options.get("rwo");
        if (rwo == null || rwo.toString().isEmpty()) return;
        boolean auto = Boolean.TRUE.equals(req.options.get("rwauto"));
        Object rw = req.options.get("rw");
        boolean hasRw = rw != null && !rw.toString().trim().isEmpty();
        if (!auto && !hasRw) throw new InvalidOptionsException("-rwo requires -rw or -rwauto");
    }

    private static void validateCompareModeFlags(RunRequest req, boolean compareMode) {
        if (!compareMode) return;
        Object fn = req.options == null ? null : req.options.get("fn");
        boolean hasFn = fn != null && !fn.toString().trim().isEmpty();
        if (!hasFn) throw new InvalidOptionsException(
                "-doc1/-doc2 require -fn so the two documents share a test identity.");
    }

    /** ERROR-level precheck findings abort the run before run-started; warnings pass through. */
    private static void failOnPrecheckErrors(File doc1, File doc2, Config config) {
        for (com.applitools.imagetester.lib.PdfComparePrechecker.Finding finding
                : com.applitools.imagetester.lib.PdfComparePrechecker.check(doc1, doc2, config,
                        com.applitools.imagetester.lib.PdfComparePrechecker.MessageStyle.GUI)) {
            if (finding.severity == com.applitools.imagetester.lib.PdfComparePrechecker.Severity.ERROR) {
                throw new InvalidOptionsException(finding.message);
            }
        }
    }

    /** Live precheck for the GUI: validates paths, parses options, returns all findings. */
    public java.util.List<com.applitools.imagetester.lib.PdfComparePrechecker.Finding> precheckCompare(RunRequest req) {
        Path doc1 = SourcePathValidator.validate(req.doc1Path);
        Path doc2 = SourcePathValidator.validate(req.doc2Path);
        RunConfig rc = factoryBuilder_.build(req, new Logger());
        return com.applitools.imagetester.lib.PdfComparePrechecker.check(doc1.toFile(), doc2.toFile(), rc.config,
                com.applitools.imagetester.lib.PdfComparePrechecker.MessageStyle.GUI);
    }

    public void cancel() {
        TestExecutor executor = currentExecutor_.get();
        if (executor == null) return;
        executor.cancel();
    }

    private void executeRun(File source, RunRequest req, String apiKey, RunConfig rc, Logger logger, RunState.Running running) {
        long startedNs = System.nanoTime();
        Config config = rc.config;
        config.apiKey = apiKey;
        // The factory captured a null key at build time (no -k flag, no env var in the
        // installed app); every Eyes instance it creates needs the GUI-provided key.
        rc.factory.apiKey(apiKey);
        config.shouldThrowException = false; // CRITICAL: a thrown diff calls System.exit
        if (config.logger == null) config.logger = logger;

        Consumer<String> logListener = line -> runStream_.emit(new SseEvent.LogLine(LogRedactor.redact(line, apiKey)));
        config.logger.addListener(logListener);

        Object rwo = req.options == null ? null : req.options.get("rwo");
        if (rwo != null && !rwo.toString().isEmpty()) {
            runWatermarkOut(source, req, config.logger, running, startedNs);
            return;
        }

        // rwauto without rwo: clean source into a temp dir, then run the Suite on the cleaned output.
        // Temp dir creation is outside the Suite's try block so the finally block always fires.
        final File effectiveSource;
        if (req.options != null && Boolean.TRUE.equals(req.options.get("rwauto"))) {
            File tempDir = null;
            try {
                tempDir = Files.createTempDirectory("imagetester-rwauto-").toFile();
                Runtime.getRuntime().addShutdownHook(new Thread(deleteRecursively(tempDir)));
            } catch (IOException e) {
                config.logger.reportException(e);
            }
            effectiveSource = tempDir != null ? tempDir : source;
        } else {
            effectiveSource = source;
        }
        // Preview thumbnails are read from disk by path on request; scope the endpoint to files
        // under whatever directory the Suite actually runs against (source, or the rwauto temp dir).
        // Clear any leftover compare-mode authorization from a prior run so it can't outlive its run.
        currentComparePaths_.set(null);
        try {
            currentSourceRoot_.set(effectiveSource.getCanonicalFile().toPath());
        } catch (IOException e) {
            currentSourceRoot_.set(effectiveSource.getAbsoluteFile().toPath());
        }

        int passed = 0, failed = 0;
        try {
            EyesFactory factory = rc.factory;
            factory.logHandlerInstance(new EyesLogBridge(config.logger));
            TestExecutor executor = new TestExecutor(rc.threads, factory, config);
            currentExecutor_.set(executor);
            executor.setTestStartedListener(info -> runStream_.emit(new SseEvent.TestStarted(info.name, info.previewPath)));
            executor.setTestCompletionListener(result -> {
                String name = result.testResult != null ? result.testResult.getName() : "(unknown)";
                String status = result.testResult != null && result.testResult.isDifferent() ? "fail" : "pass";
                long ms = TimeUnit.NANOSECONDS.toMillis(result.runTimeNs);
                String dashboard = result.testResult != null ? result.testResult.getUrl() : null;
                runStream_.emit(new SseEvent.TestFinished(name, status, ms, dashboard, result.previewPath));
                RunState.TestRow row = new RunState.TestRow(name);
                row.status = status; row.durationMs = ms; row.dashboardUrl = dashboard; row.previewPath = result.previewPath;
                running.tests.add(row);
            });
            if (effectiveSource != source) {
                String rwText = req.options.get("rw") != null ? req.options.get("rw").toString() : null;
                PdfVectorWatermarkAutoMode.run(source, effectiveSource, rwText, config.logger);
            }
            config.logger.printMessage(String.format("Scanning source: %s%n", effectiveSource.getAbsolutePath()));
            Suite suite = Suite.create(effectiveSource.getCanonicalFile(), config, executor);
            config.logger.printMessage("Starting tests" + System.lineSeparator());
            suite.run();
        } catch (Throwable t) {
            config.logger.reportException(t);
        } finally {
            TestExecutor executor = currentExecutor_.getAndSet(null);
            if (executor != null && executor.isCancelled())
                config.logger.printMessage("Run cancelled" + System.lineSeparator());
            config.logger.removeListener(logListener);
            for (RunState.TestRow r : running.tests) {
                if ("pass".equals(r.status)) passed++;
                else if ("fail".equals(r.status)) failed++;
            }
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
            RunState.Done done = new RunState.Done(running.runId, running.tests, passed, failed, durationMs);
            state_.set(done);
            runStream_.emit(new SseEvent.RunFinished(passed, failed, durationMs));
        }
    }

    private void executeCompareRun(File doc1, File doc2, String apiKey, RunConfig rc, Logger logger, RunState.Running running) {
        long startedNs = System.nanoTime();
        Config config = rc.config;
        config.apiKey = apiKey;
        rc.factory.apiKey(apiKey);
        config.shouldThrowException = false; // CRITICAL: a thrown diff calls System.exit
        if (config.logger == null) config.logger = logger;

        Consumer<String> logListener = line -> runStream_.emit(new SseEvent.LogLine(LogRedactor.redact(line, apiKey)));
        config.logger.addListener(logListener);

        // Compare mode has no single root (doc1/doc2 can live in unrelated directories), so the
        // preview endpoint is authorized by exact canonical path instead. Clear any leftover
        // folder/file-mode root from a prior run so it can't outlive its run.
        currentSourceRoot_.set(null);
        currentComparePaths_.set(canonicalPathsOf(doc1, doc2));

        int passed = 0, failed = 0;
        try {
            String name = config.forcedName;
            runStream_.emit(new SseEvent.TestStarted(name, doc1.getAbsolutePath(), doc2.getAbsolutePath()));
            CompareRunner.CompareResult result = CompareRunner.run(doc1, doc2, config, rc.factory);
            String status = result.doc2Result != null && result.doc2Result.isDifferent() ? "fail" : "pass";
            String dashboard = result.doc2Result != null ? result.doc2Result.getUrl() : null;
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
            runStream_.emit(new SseEvent.TestFinished(name, status, ms, dashboard, doc1.getAbsolutePath(), doc2.getAbsolutePath()));
            RunState.TestRow row = new RunState.TestRow(name);
            row.status = status; row.durationMs = ms; row.dashboardUrl = dashboard;
            row.previewPath = doc1.getAbsolutePath(); row.doc2PreviewPath = doc2.getAbsolutePath();
            running.tests.add(row);
        } catch (Throwable t) {
            config.logger.reportException(t);
        } finally {
            config.logger.removeListener(logListener);
            for (RunState.TestRow r : running.tests) {
                if ("pass".equals(r.status)) passed++;
                else if ("fail".equals(r.status)) failed++;
            }
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
            RunState.Done done = new RunState.Done(running.runId, running.tests, passed, failed, durationMs);
            state_.set(done);
            runStream_.emit(new SseEvent.RunFinished(passed, failed, durationMs));
        }
    }

    private static Set<Path> canonicalPathsOf(File doc1, File doc2) {
        Set<Path> paths = new HashSet<>();
        paths.add(canonicalPath(doc1));
        paths.add(canonicalPath(doc2));
        return paths;
    }

    private static Path canonicalPath(File file) {
        try {
            return file.getCanonicalFile().toPath();
        } catch (IOException e) {
            return file.getAbsoluteFile().toPath();
        }
    }

    private static Runnable deleteRecursively(final File dir) {
        return new Runnable() {
            @Override
            public void run() {
                if (dir == null || !dir.exists()) return;
                deleteTree(dir);
            }

            private void deleteTree(File entry) {
                if (entry.isDirectory()) {
                    File[] children = entry.listFiles();
                    if (children != null) {
                        for (File child : children) deleteTree(child);
                    }
                }
                if (!entry.delete()) entry.deleteOnExit();
            }
        };
    }

    private void runWatermarkOut(File source, RunRequest req, Logger logger,
                                 RunState.Running running, long startedNs) {
        String outPath = req.options.get("rwo").toString();
        File outDir = new File(outPath);
        String rwText = req.options.get("rw") == null ? null : req.options.get("rw").toString();
        boolean auto = Boolean.TRUE.equals(req.options.get("rwauto"));
        try {
            // Dependent-flag guard mirrors ImageTester.validateWatermarkFlags: -rwo needs -rw or -rwauto.
            if (!auto && (rwText == null || rwText.trim().isEmpty()))
                throw new InvalidOptionsException("-rwo requires -rw or -rwauto");
            if (auto) PdfVectorWatermarkAutoMode.run(source, outDir, rwText, logger);
            else PdfWatermarkOutMode.run(source, rwText, outDir, logger);
        } catch (Throwable t) {
            logger.reportException(t);
        } finally {
            File[] files = outDir.listFiles();
            int count = files == null ? 0 : files.length;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
            state_.set(new RunState.Done(running.runId, running.tests, 0, 0, durationMs, outPath));
            runStream_.emit(new SseEvent.WatermarkCleaned(outPath, count, durationMs));
        }
    }
}
