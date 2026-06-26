package com.applitools.imagetester.gui;

import com.applitools.imagetester.ImageTester;
import com.applitools.imagetester.Suite;
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

    public void setSecretApiKey(String value) { secrets_.setApiKey(value); }

    public StartResult start(RunRequest req) {
        Path validated = SourcePathValidator.validate(req.sourcePath);
        String apiKey = secrets_.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) throw new MissingApiKeyException();

        validateWatermarkFlags(req); // throws InvalidOptionsException synchronously

        Logger logger = new Logger();
        RunConfig rc = factoryBuilder_.build(req, logger); // throws on malformed options synchronously

        RunState.Running running = new RunState.Running();
        // Race-safe transition: only one caller can win; stale Done is also replaced atomically.
        while (true) {
            RunState current = state_.get();
            if (current instanceof RunState.Running) throw new RunInProgressException();
            if (state_.compareAndSet(current, running)) break;
        }

        // Wipe SSE replay history so a tab that connects mid-run only sees events from *this* run.
        runStream_.resetReplay();
        runExecutor_.submit(() -> executeRun(validated.toFile(), req, apiKey, rc, logger, running));
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

    public void cancel() {
        TestExecutor executor = currentExecutor_.get();
        if (executor == null) return;
        executor.cancel();
    }

    private void executeRun(File source, RunRequest req, String apiKey, RunConfig rc, Logger logger, RunState.Running running) {
        long startedNs = System.nanoTime();
        Config config = rc.config;
        config.apiKey = apiKey;
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

        int passed = 0, failed = 0;
        try {
            EyesFactory factory = rc.factory;
            factory.logHandlerInstance(new EyesLogBridge(config.logger));
            TestExecutor executor = new TestExecutor(rc.threads, factory, config);
            currentExecutor_.set(executor);
            executor.setTestStartedListener(name -> runStream_.emit(new SseEvent.TestStarted(name)));
            executor.setTestCompletionListener(result -> {
                String name = result.testResult != null ? result.testResult.getName() : "(unknown)";
                String status = result.testResult != null && result.testResult.isDifferent() ? "fail" : "pass";
                long ms = TimeUnit.NANOSECONDS.toMillis(result.runTimeNs);
                String dashboard = result.testResult != null ? result.testResult.getUrl() : null;
                runStream_.emit(new SseEvent.TestFinished(name, status, ms, dashboard));
                RunState.TestRow row = new RunState.TestRow(name);
                row.status = status; row.durationMs = ms; row.dashboardUrl = dashboard;
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
