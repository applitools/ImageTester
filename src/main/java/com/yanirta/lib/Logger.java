package com.yanirta.lib;

import com.applitools.eyes.BatchInfo;
import com.yanirta.BatchMapper.BatchMapPojo;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.log4j.Level;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Logger {
    private final PrintStream out_;
    private boolean debug_;
    private final SimpleDateFormat dateFormatter_ = new SimpleDateFormat("HH:mm:ss");

    public Logger() {
        this(System.out, false);
    }

    public Logger(PrintStream out, boolean debug) {
        this.out_ = out;
        this.debug_ = debug;
        // This part disables log4j warnings
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.OFF);
    }

    public void setDebug(boolean debug) {
        this.debug_ = debug;
    }

    public void setDebug() {
        setDebug(true);
    }

    public void printBatchPojo(BatchMapPojo batchMapPojo) {
        out_.printf("%s \n", batchMapPojo);
    }

    public void printProgress(int curr, int total) {
        out_.printf("[%s/%s] \n", curr, total);
    }

    private void printPrefix() {
        if (debug_) {
            Date date = new Date(System.currentTimeMillis());
            out_.printf("[%s] [%s] ", dateFormatter_.format(date), Thread.currentThread().getName());
        }
    }

    public void printMessage(String msg) {
        out_.print(msg);
    }

    public void reportDebug(String format, Object... args) {
        if (!debug_) return;
        printPrefix();
        out_.printf(format, args);
    }

    public void reportDiscovery(File file) {
        if (!debug_) return;
        printPrefix();
        if (file.isDirectory())
            out_.printf("Discovering folder %s \n", file.getAbsolutePath());
        else
            out_.printf("Enqueuing file %s \n", file.getAbsolutePath());
    }

    public void reportResult(ExecutorResult result) {
        printPrefix();
        if (debug_) out_.printf("[%d Msec] ", TimeUnit.NANOSECONDS.toMillis(result.runTimeNs));
        String status = result.testResult != null ? result.testResult.getStatus().toString() : "N/A";
        out_.printf("[%s], %s \n", status, result.testResult);
    }

    public void reportException(Throwable e) {
        reportException(e, null);
    }

    public void reportException(Throwable e, String filename) {
        printPrefix();
        if (filename != null && !filename.isEmpty())
            out_.printf("File: %s \n", filename);

        switch (e.getClass().getSimpleName()) {
            case "FileNotFoundException":
                out_.printf("The file was not found \n");
                break;
            case "IOException":
                out_.printf("Error, Please check that the file is accessible, readable and not exclusively locked. ");
                out_.printf("%s\n", e.getMessage());
                break;
            case "DocumentException":
            case "RendererException":
                out_.printf("Unable to process document, %s \n", e.getMessage());
                break;
            case "UnsatisfiedLinkError":
                out_.printf("Error, Please make sure tesseract and ghostscript are installed and in path! ");
                out_.printf("%s\n", e.getMessage());
                break;
            default:
                out_.printf("Unexpected error, %s, %s \n", e.getClass().getName(), e.getMessage());
                break;
        }

        if (debug_) {
            e.printStackTrace(out_);
        }
    }

    public void printVersion(String cur_ver) {
        out_.printf("ImageTester version %s \n", cur_ver);
    }

    public void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ImageTester [-k <api-key>] [options]", options);
    }

    public void logPage(BufferedImage bim, String testname, Integer page) {
        try {
            logPage_(bim, testname, page);
        } catch (IOException e) {
            reportException(e);
        }
    }

    private void logPage_(BufferedImage bim, String testname, Integer page) throws IOException {
        if (!debug_) return;
        File debugOutFolder = new File(System.getProperty("user.dir"), "debug");
        if (!debugOutFolder.exists())
            debugOutFolder.mkdir();

        File pageImg = new File(debugOutFolder, String.format("%s_page_%s.png", testname, page));
        ImageIO.write(bim, "png", pageImg);
    }
}
