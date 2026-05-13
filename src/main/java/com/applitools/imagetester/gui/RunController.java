package com.applitools.imagetester.gui;

import com.applitools.imagetester.Suite;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.ExecutorResult;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.TestExecutor;

import java.io.File;
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

    /** Builds an EyesFactory pre-configured with the api key + match level for one run. Allows tests to inject mocks. */
    @FunctionalInterface
    public interface EyesFactoryBuilder {
        EyesFactory build(String apiKey, String matchLevel, Logger logger);
    }

    private static final String CUR_VER = "3.10.0";

    private static final EyesFactoryBuilder PRODUCTION_BUILDER =
        (apiKey, matchLevel, logger) ->
            new EyesFactory(CUR_VER, logger).apiKey(apiKey).matchLevel(matchLevel);

    private final SecretsStore secrets_;
    private final RunStream runStream_;
    private final EyesFactoryBuilder factoryBuilder_;
    private final AtomicReference<RunState> state_ = new AtomicReference<>(RunState.Idle.INSTANCE);
    private final ExecutorService runExecutor_ = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RunController-run");
        t.setDaemon(true);
        return t;
    });

    public RunController(SecretsStore secrets, RunStream runStream) {
        this(secrets, runStream, PRODUCTION_BUILDER);
    }

    public RunController(SecretsStore secrets, RunStream runStream, EyesFactoryBuilder factoryBuilder) {
        this.secrets_ = secrets;
        this.runStream_ = runStream;
        this.factoryBuilder_ = factoryBuilder;
    }

    public RunState snapshot() { return state_.get(); }
    public RunStream stream() { return runStream_; }
    public SecretsStore secrets() { return secrets_; }

    public void setSecretApiKey(String value) { secrets_.setApiKey(value); }

    public StartResult start(String sourcePath, String matchLevelStr) {
        Path validated = SourcePathValidator.validate(sourcePath);
        String apiKey = secrets_.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) throw new MissingApiKeyException();

        RunState.Running running = new RunState.Running();
        // Race-safe transition: only one caller can win; stale Done is also replaced atomically.
        while (true) {
            RunState current = state_.get();
            if (current instanceof RunState.Running) throw new RunInProgressException();
            if (state_.compareAndSet(current, running)) break;
        }

        runExecutor_.submit(() -> executeRun(validated.toFile(), matchLevelStr, apiKey, running));
        return new StartResult(running.runId);
    }

    // Best-effort cancel — TestExecutor has no public cancel API in v1.
    // TODO(gui-team): wire interrupt when TestExecutor exposes a cancel hook (#issue).
    public void cancel() {}

    private void executeRun(File source, String matchLevelStr, String apiKey, RunState.Running running) {
        long startedNs = System.nanoTime();
        Config config = new Config();
        config.apiKey = apiKey;
        config.logger = new Logger();
        // CRITICAL: without this, TestExecutor.join() may call System.exit on a diff
        config.shouldThrowException = false;

        Consumer<String> logListener = line -> runStream_.emit(new SseEvent.LogLine(LogRedactor.redact(line, apiKey)));
        config.logger.addListener(logListener);

        int passed = 0;
        int failed = 0;
        try {
            EyesFactory factory = factoryBuilder_.build(apiKey, matchLevelStr, config.logger);
            TestExecutor executor = new TestExecutor(2, factory, config);
            executor.setTestCompletionListener(result -> {
                String name = result.testResult != null ? result.testResult.getName() : "(unknown)";
                String status = result.testResult != null && result.testResult.isDifferent() ? "fail" : "pass";
                long ms = TimeUnit.NANOSECONDS.toMillis(result.runTimeNs);
                String dashboard = result.testResult != null ? result.testResult.getUrl() : null;
                runStream_.emit(new SseEvent.TestFinished(name, status, ms, dashboard));
                RunState.TestRow row = new RunState.TestRow(name);
                row.status = status;
                row.durationMs = ms;
                row.dashboardUrl = dashboard;
                running.tests.add(row);
            });
            Suite suite = Suite.create(source.getCanonicalFile(), config, executor);
            suite.run();
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
}
