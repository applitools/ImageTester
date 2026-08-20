package com.applitools.imagetester.lib;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import com.applitools.imagetester.BatchMapper.BatchMapPojo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Logger {
    private static final String SUPPORT_HINT =
            "If the problem persists, please contact support at support@applitools.com \n";

    private final PrintStream out_;
    private boolean debug_;
    private final SimpleDateFormat dateFormatter_ = new SimpleDateFormat("HH:mm:ss");
    private final List<Consumer<String>> listeners_ = new CopyOnWriteArrayList<>();

    public Logger() { this(System.out, false); }

    public Logger(PrintStream out, boolean debug) {
        this.out_ = out;
        this.debug_ = debug;
    }

    public void setDebug(boolean debug) { this.debug_ = debug; }
    public void setDebug() { setDebug(true); }

    public void addListener(Consumer<String> listener) { listeners_.add(listener); }
    public void removeListener(Consumer<String> listener) { listeners_.remove(listener); }

    private void emit(String message) {
        out_.print(message);
        for (Consumer<String> l : listeners_) {
            try { l.accept(message); } catch (Throwable ignored) { /* never let a listener disrupt logging */ }
        }
    }

    public void printBatchPojo(BatchMapPojo batchMapPojo) {
        emit(String.format("%s \n", batchMapPojo));
    }

    public void printProgress(int curr, int total) {
        // CLI-only progress: GUI surfaces this via test-started/finished rows, where "[N/total]" is redundant noise.
        out_.print(String.format("[%s/%s] \n", curr, total));
    }

    public void printHeartbeat(String name, long elapsedSeconds) {
        emit(String.format("Still running... %s - %ds elapsed \n", name, elapsedSeconds));
    }

    private String prefix() {
        if (!debug_) return "";
        Date date = new Date(System.currentTimeMillis());
        return String.format("[%s] [%s] ", dateFormatter_.format(date), Thread.currentThread().getName());
    }

    public void printMessage(String msg) {
        emit(msg);
    }

    public void reportDebug(String format, Object... args) {
        if (!debug_) return;
        emit(prefix() + String.format(format, args));
    }

    public void reportDiscovery(File file) {
        if (!debug_) return;
        if (file.isDirectory())
            emit(prefix() + String.format("Discovering folder %s \n", file.getAbsolutePath()));
        else
            emit(prefix() + String.format("Enqueuing file %s \n", file.getAbsolutePath()));
    }

    public void reportResult(ExecutorResult result) {
        // No result means the failure was already reported via reportException —
        // an "[N/A], null" line adds nothing.
        if (result.testResult == null) return;
        StringBuilder sb = new StringBuilder(prefix());
        if (debug_) sb.append(String.format("[%d Msec] ", TimeUnit.NANOSECONDS.toMillis(result.runTimeNs)));
        sb.append(String.format("[%s], %s \n", result.testResult.getStatus().toString(), result.testResult));
        emit(sb.toString());
    }

    public void reportResultAccessibility(ExecutorResult result) {
        if (result.testResult == null || result.testResult.getAccessibilityStatus() == null) return;
        emit(String.format(
            "Accessibility: [%s], Level: [%s], Version: [%s] \n",
            result.testResult.getAccessibilityStatus().getStatus().toString(),
            result.testResult.getAccessibilityStatus().getLevel().toString(),
            result.testResult.getAccessibilityStatus().getVersion().toString()
        ));
    }

    public void reportException(Throwable e) { reportException(e, null); }

    public void reportException(Throwable e, String filename) {
        StringBuilder sb = new StringBuilder(prefix());
        if (filename != null && !filename.isEmpty())
            sb.append(String.format("File: %s \n", filename));
        switch (e.getClass().getSimpleName()) {
            case "FileNotFoundException":
                sb.append("The file was not found \n"); break;
            case "IOException":
                sb.append("Error, Please check that the file is accessible, readable and not exclusively locked. ");
                sb.append(String.format("%s\n", e.getMessage())); break;
            case "DocumentException":
            case "RendererException":
                sb.append(String.format("Unable to process document, %s \n", e.getMessage())); break;
            case "UnsatisfiedLinkError":
                sb.append("Error, Please make sure tesseract and ghostscript are installed and in path! ");
                sb.append(String.format("%s\n", e.getMessage())); break;
            case "ExecutionException":
                sb.append(String.format("%s\n", e.getMessage())); break;
            case "EyesException":
                sb.append(String.format("%s \n", e.getMessage()));
                if (isInvalidApiKey(e))
                    sb.append("Are you testing against a private cloud? Be sure to set your Applitools server URL — the Connection tab in the GUI, or -s on the command line. \n");
                else
                    sb.append(SUPPORT_HINT);
                break;
            default:
                if (e.getMessage() != null)
                    sb.append(String.format("Error: %s \n", e.getMessage()));
                else
                    sb.append(String.format("Unexpected error (%s) \n", e.getClass().getSimpleName()));
                sb.append(SUPPORT_HINT);
                break;
        }
        if (debug_) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());
        }
        emit(sb.toString());
    }

    // An invalid key against the public cloud is often really a missing private-cloud server URL.
    private static boolean isInvalidApiKey(Throwable e) {
        String message = e.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("api key") && lower.contains("invalid");
    }

    public void printVersion(String cur_ver) {
        emit(String.format("ImageTester version %s \n", cur_ver));
    }

    public void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ImageTester [-k <api-key>] [options]", options);
    }

    public void logPage(BufferedImage bim, String testname, Integer page) {
        try { logPage_(bim, testname, page); } catch (IOException e) { reportException(e); }
    }

    private void logPage_(BufferedImage bim, String testname, Integer page) throws IOException {
        if (!debug_) return;
        File debugOutFolder = new File(System.getProperty("user.dir"), "debug");
        if (!debugOutFolder.exists()) debugOutFolder.mkdir();
        File pageImg = new File(debugOutFolder, String.format("%s_page_%s.png", testname, page));
        ImageIO.write(bim, "png", pageImg);
    }
}
