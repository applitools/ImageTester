package com.applitools.imagetester;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import com.applitools.eyes.AccessibilityGuidelinesVersion;
import com.applitools.eyes.AccessibilityLevel;
import com.applitools.eyes.MatchLevel;
import com.applitools.imagetester.BatchMapper.BatchMapDeserializer;
import com.applitools.imagetester.Constants.ApplitoolsConstants;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.EyesUtilitiesConfig;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.PdfVectorWatermarkAutoMode;
import com.applitools.imagetester.lib.PdfWatermarkOutMode;
import com.applitools.imagetester.lib.RunConfig;
import com.applitools.imagetester.lib.RunConfigFactory;
import com.applitools.imagetester.lib.TestExecutor;
import com.applitools.imagetester.lib.Utils;

public class ImageTester {
    public static final String CUR_VER = "3.16.3";
    public static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    public static final String DEFAULT_THREADS = String.valueOf(DEFAULT_THREAD_COUNT);

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {

        CommandLineParser parser = new DefaultParser();
        Options options = getOptions();
        Logger logger = new Logger();

        // PDFBox generates fairly unhelpful logs - suppressing these by default
        java.util.logging.Logger.getLogger("org.apache.pdfbox").setLevel(java.util.logging.Level.OFF);

        if (java.util.Arrays.asList(args).contains("--gui")) {
            if (args.length != 1) {
                System.err.println("--gui must be the only argument. Got: " + java.util.Arrays.toString(args));
                return 2;
            }
            try {
                com.applitools.imagetester.gui.GuiLauncher.setDockIcon();
                com.applitools.imagetester.gui.GuiServer server = com.applitools.imagetester.gui.GuiServer.start();
                com.applitools.imagetester.gui.GuiLauncher.open("http://localhost:" + server.port());
                server.join();
                return 0;
            } catch (Exception e) {
                e.printStackTrace();
                return 1;
            }
        }

        try {
            CommandLine cmd = parser.parse(options, args);
            logger.setDebug(cmd.hasOption("debug"));
            logger.printVersion(CUR_VER);
            com.applitools.imagetester.lib.UpdateChecker.production().checkAsync(update ->
                    System.err.println("Note: ImageTester " + update.version
                            + " is available - " + update.releasePageUrl));

            if (cmd.getOptions().length == 0) {
                logger.printHelp(options);
                return 0;
            }

            if (cmd.hasOption("dv")) {
                logger.printMessage(Utils.CERT_VALIDATION_DISABLED_WARNING);
                Utils.disableCertValidation();
            }

            int watermarkValidation = validateWatermarkFlags(cmd, logger);
            if (watermarkValidation != 0) return watermarkValidation;

            int compareValidation = validateCompareFlags(cmd, logger);
            if (compareValidation != 0) return compareValidation;

            if (cmd.hasOption("rwauto") && cmd.hasOption("rwo")) {
                File inputRoot = new File(cmd.getOptionValue("f", "."));
                File outDir = new File(cmd.getOptionValue("rwo"));
                return PdfVectorWatermarkAutoMode.run(inputRoot, outDir, cmd.getOptionValue("rw"), logger);
            }

            if (cmd.hasOption("rwo")) {
                File inputRoot = new File(cmd.getOptionValue("f", "."));
                File outDir = new File(cmd.getOptionValue("rwo"));
                return PdfWatermarkOutMode.run(inputRoot, cmd.getOptionValue("rw"), outDir, logger);
            }

            File rwAutoCleanedDir = null;
            if (cmd.hasOption("rwauto")) {
                File originalRoot = new File(cmd.getOptionValue("f", "."));
                rwAutoCleanedDir = Files.createTempDirectory("imagetester-rwauto-").toFile();
                Runtime.getRuntime().addShutdownHook(new Thread(deleteRecursively(rwAutoCleanedDir)));
                int cleanResult = PdfVectorWatermarkAutoMode.run(
                        originalRoot, rwAutoCleanedDir, cmd.getOptionValue("rw"), logger);
                if (cleanResult != 0) return cleanResult;
            }

            String batchMapperPath = cmd.getOptionValue("mp", null);
            if (batchMapperPath != null) {
                runTestWithBatchMapper(logger, cmd);
                return 0;
            }

            if (cmd.hasOption("doc1")) {
                RunConfig compareRc = RunConfigFactory.from(cmd, logger);
                File doc1 = new File(cmd.getOptionValue("doc1"));
                File doc2 = new File(cmd.getOptionValue("doc2"));
                com.applitools.imagetester.lib.CompareRunner.run(doc1, doc2, compareRc.config, compareRc.factory);
                compareRc.config.closeBatches();
                return computeExitCode(compareRc.config, logger);
            }

            RunConfig rc = RunConfigFactory.from(cmd, logger);
            Config config = rc.config;
            EyesFactory factory = rc.factory;

            File root = rwAutoCleanedDir != null
                    ? rwAutoCleanedDir
                    : new File(cmd.getOptionValue("f", "."));

            TestExecutor executor = new TestExecutor(rc.threads, factory, config);
            Suite suite = Suite.create(root.getCanonicalFile(), config, executor);
            suite.run();
            config.closeBatches();
            return computeExitCode(config, logger);
        } catch (ParseException | IOException e) {
            logger.reportException(e);
            logger.printHelp(options);
            return 1;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.reportException(e);
            return 1;
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

    private static int validateWatermarkFlags(CommandLine cmd, Logger logger) {
        if (cmd.hasOption("rwo") && !cmd.hasOption("rw") && !cmd.hasOption("rwauto")) {
            logger.printMessage("ERROR: -rwo requires -rw or -rwauto to be specified.");
            return 1;
        }
        if (cmd.hasOption("rw")) {
            String rw = cmd.getOptionValue("rw");
            if (rw == null || rw.trim().isEmpty()) {
                logger.printMessage("ERROR: -rw value cannot be blank.");
                return 1;
            }
        }
        return 0;
    }

    private static int validateCompareFlags(CommandLine cmd, Logger logger) {
        boolean hasDoc1 = cmd.hasOption("doc1");
        boolean hasDoc2 = cmd.hasOption("doc2");
        if (!hasDoc1 && !hasDoc2) return 0;
        if (cmd.hasOption("f")) {
            logger.printMessage("ERROR: -doc1/-doc2 cannot be combined with -f.");
            return 1;
        }
        if (hasDoc1 != hasDoc2) {
            logger.printMessage(hasDoc1 ? "ERROR: -doc1 was given without -doc2." : "ERROR: -doc2 was given without -doc1.");
            return 1;
        }
        if (cmd.getOptionValue("fn") == null || cmd.getOptionValue("fn").trim().isEmpty()) {
            logger.printMessage("ERROR: -doc1/-doc2 require -fn so the two documents share a test identity.");
            return 1;
        }
        return 0;
    }

    private static int computeExitCode(Config config, Logger logger) {
        if (config.skipTracker.isEmpty()) return 0;
        printSkipSummary(config, logger);
        if (config.skipTracker.hasLibreOfficeMissing()) {
            printInstallHint(logger);
        }
        return 2;
    }

    private static void printSkipSummary(Config config, Logger logger) {
        logger.printMessage(String.format("Skipped %d file(s):", config.skipTracker.skips().size()));
        config.skipTracker.skips().forEach(skip ->
            logger.printMessage(String.format("  - %s: %s", skip.file.getName(), skip.reason))
        );
    }

    private static void printInstallHint(Logger logger) {
        logger.printMessage("");
        logger.printMessage("Some files required LibreOffice for conversion but it was not found on PATH.");
        logger.printMessage("Install LibreOffice to enable support for .doc/.docx/.ppt/.pptx and other office formats:");
        logger.printMessage("  macOS:   brew install --cask libreoffice");
        logger.printMessage("  Windows: winget install TheDocumentFoundation.LibreOffice");
        logger.printMessage("  Linux:   sudo apt-get install libreoffice");
    }

    /**
     * Running the ImageTester with the BatchMapper differs enough that it warrants a different execution logic
     * ImageTester will use this method for test execution when a batch mapper is specified with "-mp"
     *
     * @param logger Logger utility
     * @param cmd CommandLine tool that parses arguments and flags from CLI execution
     */
    private static void runTestWithBatchMapper(final Logger logger, final CommandLine cmd) {

        logger.printMessage("Running ImageTester with BatchMapper");

        try {
            String batchMapperPath = cmd.getOptionValue("mp", null);

            // Split each of the batch POJOs into a parallel stream and let JVM handle multithreading
            BatchMapDeserializer.readFile(batchMapperPath).parallelStream().forEach(currentBatch -> {
                logger.printBatchPojo(currentBatch);
                Config currentConfiguration = new Config();
                currentConfiguration.apiKey = cmd.getOptionValue("k", System.getenv(ApplitoolsConstants.APPLITOOLS_API_KEY));
                currentConfiguration.serverUrl = cmd.getOptionValue("s", System.getenv(ApplitoolsConstants.APPLITOOLS_SERVER_URL));
                                
                String[] proxySettings = cmd.getOptionValues("p");

                if(proxySettings == null) {
                    String proxyString = System.getenv(ApplitoolsConstants.APPLITOOLS_PROXY);
                    proxySettings = proxyString != null ? proxyString.split(",") : null;
                }
                currentConfiguration.setProxy(proxySettings);
                                
                String[] accessibilityOptions = cmd.getOptionValues("ac");
                accessibilityOptions = cmd.hasOption("ac") && accessibilityOptions == null ? new String[0] : accessibilityOptions;

                EyesFactory factory
                        = new EyesFactory(CUR_VER, logger)
                        .apiKey(currentConfiguration.apiKey)
                        .serverUrl(currentConfiguration.serverUrl)
                        .proxySettings(currentConfiguration.proxy_settings)
                        .matchLevel(currentBatch.matchLevel)
                        .branch(cmd.getOptionValue("br", null))
                        .parentBranch(cmd.getOptionValue("pb", null))
                        .baselineEnvName(cmd.getOptionValue("bn", null))
                        .baselineBranchName(cmd.getOptionValue("bb", null))
                        .logFile(cmd.getOptionValue("lf", null))
                        .hostOs(currentBatch.os)
                        .hostApp(currentBatch.browser)
                        .saveFailedTests(cmd.hasOption("as"))
                        .ignoreDisplacement(cmd.hasOption("id"))
                        .saveNewTests(!cmd.hasOption("pt"))
                        .imageCut(cmd.getOptionValues("ic"))
                        .accSettings(accessibilityOptions)
                        .deviceName(cmd.getOptionValue("dn", null));
                currentConfiguration.splitSteps = cmd.hasOption("st");
                currentConfiguration.logger = logger;
                currentConfiguration.appName = currentBatch.app;
                currentConfiguration.DocumentConversionDPI = Float.parseFloat(cmd.getOptionValue("di", "250"));
                currentConfiguration.renderThreads = Integer.parseInt(cmd.getOptionValue("rt", String.valueOf(currentConfiguration.renderThreads)));
                currentConfiguration.pdfPass = cmd.getOptionValue("pp", null);
                currentConfiguration.pages = currentBatch.pages;
                currentConfiguration.includePageNumbers = cmd.hasOption("pn");
                currentConfiguration.forcedName = currentBatch.testName;
                currentConfiguration.sequenceName = cmd.getOptionValue("sq", null);
                currentConfiguration.legacyFileOrder = cmd.hasOption("lo");
                currentConfiguration.normalizeFont = cmd.hasOption("nf");
                currentConfiguration.removeWatermarkText = cmd.getOptionValue("rw");
                currentConfiguration.removeWatermarkOutDir = cmd.getOptionValue("rwo");
                currentConfiguration.regexFileNameFilter = cmd.getOptionValue("rf");
                currentConfiguration.setViewport(StringUtils.isNoneBlank(currentBatch.viewport) ? currentBatch.viewport: null);
                currentConfiguration.setMatchSize(StringUtils.isNoneBlank(currentBatch.matchsize)? currentBatch.matchsize : null);
                currentConfiguration.setBatchInfo(cmd.getOptionValue("fb", null), cmd.hasOption("nc"));
                currentConfiguration.setIgnoreRegions(
                    StringUtils.isNoneBlank(currentBatch.ignoreRegions) ?
                    currentBatch.ignoreRegions :
                    cmd.getOptionValue("ir", null)
                );
                currentConfiguration.setContentRegions(
                    StringUtils.isNoneBlank(currentBatch.contentRegions) ?
                    currentBatch.contentRegions :
                    cmd.getOptionValue("cr", null)
                );
                currentConfiguration.setLayoutRegions(
                    StringUtils.isNoneBlank(currentBatch.layoutRegions) ?
                    currentBatch.layoutRegions :
                    cmd.getOptionValue("lr", null)
                );
                currentConfiguration.dontCloseBatches = cmd.hasOption("dcb");
                currentConfiguration.shouldThrowException = cmd.hasOption("te");
                currentConfiguration.setCaptureRegion(cmd.getOptionValue("rc", null));
                currentConfiguration.setMatchTimeout(cmd.getOptionValue("mt", null));
                currentConfiguration.setProperties(cmd.getOptionValue("pr", null));

                // Full page for ac regions capability
                if (cmd.hasOption("arr") && currentConfiguration.accessibilityRegularTextRegions == null) {
                    currentConfiguration.accessibilityRegularTextFullPage = true;
                }
                if (cmd.hasOption("arl") && currentConfiguration.accessibilityLargeTextRegions == null) {
                    currentConfiguration.accessibilityLargeTextFullPage = true;
                }
                if (cmd.hasOption("arb") && currentConfiguration.accessibilityBoldTextRegions == null) {
                    currentConfiguration.accessibilityBoldTextFullPage = true;
                }
                if (cmd.hasOption("arg") && currentConfiguration.accessibilityGraphicsRegions== null) {
                    currentConfiguration.accessibilityGraphicsFullPage = true;
                }

                try {
                    File root = new File(currentBatch.filePath);
                    int maxThreads = Integer.parseInt(cmd.getOptionValue("th", DEFAULT_THREADS));
                    Suite suite = Suite.create(
                            root.getCanonicalFile(),
                            currentConfiguration,
                            new TestExecutor(maxThreads, factory, currentConfiguration)
                    );
                    currentConfiguration.eyesUtilsConf = new EyesUtilitiesConfig(cmd);
                    suite.run();
                } catch (IOException e) {
                    logger.printMessage("Could not find file to test upon");
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                } finally {
                    currentConfiguration.closeBatches();
                }
            });
        } catch (Exception e) {
            logger.reportException(e);
            e.printStackTrace();
        }
    }

    public static Options getOptions() {
        Options options = new Options();
        options.addOption(Option.builder("k")
                .longOpt("apiKey")
                .desc("Applitools api key")
                .hasArg()
                .argName("apikey")
                .build());
        options.addOption(Option.builder("a")
                .longOpt("AppName")
                .desc("Set own application name, default: ImageTester")
                .hasArg()
                .argName("name")
                .build());
        options.addOption(Option.builder("f")
                .longOpt("folder")
                .desc("Set the root folder to start the analysis, default: \\.")
                .hasArg()
                .argName("path")
                .build());
        options.addOption(Option.builder("doc1")
                .desc("First document for direct two-document comparison (use with -doc2 and -fn)")
                .hasArg()
                .argName("path")
                .build());
        options.addOption(Option.builder("doc2")
                .desc("Second document for direct two-document comparison (use with -doc1 and -fn)")
                .hasArg()
                .argName("path")
                .build());
        options.addOption(Option.builder("p")
                .longOpt("proxy")
                .desc("Set proxy address")
                .numberOfArgs(3)
                .optionalArg(true)
                .valueSeparator(',') //, and not ; to avoid bash commands separation
                .argName("url [,user,password]")
                .build()
        );
        options.addOption(Option.builder("s")
                .longOpt("server")
                .desc("Set Applitools server url")
                .hasArg()
                .argName("url")
                .build()
        );
        options.addOption(Option.builder("ml")
                .longOpt("matchLevel")
                .desc(String.format("Set match level to one of [%s], default = Strict", Utils.getEnumValues(MatchLevel.class)))
                .hasArg()
                .argName("level")
                .build());
        options.addOption(Option.builder("br")
                .longOpt("branch")
                .desc("Set branch name")
                .hasArg()
                .argName("name")
                .build());
        options.addOption(Option.builder("pb")
                .longOpt("parentBranch")
                .desc("Set parent branch name, optional when working with branches")
                .hasArg()
                .argName("name")
                .build());
        options.addOption(Option.builder("bn")
                .longOpt("baseline")
                .desc("Set baseline name")
                .hasArg()
                .argName("name")
                .build());
        options.addOption(Option.builder("bb")
                .longOpt("baselineBranchName")
                .desc("Set baseline branch name")
                .hasArg()
                .argName("name")
                .build());
        options.addOption(Option.builder("vs")
                .longOpt("viewportsize")
                .desc("Declare viewport size identifier <width>x<height> ie. 1000x600, if not set,default will be first image's size of every test")
                .hasArg()
                .argName("size")
                .build());
        options.addOption(Option.builder("ms")
                .longOpt("matchsize")
                .desc("Match the size of the images to a specific width/height ie. `1000x`- adjust by width, `x600`-adjust by height, `1000x600`- fit to the exact size, note, may loose proportions")
                .hasArg()
                .argName("size")
                .build());
        options.addOption(Option.builder("lf")
                .longOpt("logFile")
                .desc("Specify Applitools log-file")
                .hasArg()
                .optionalArg(true)
                .argName("file")
                .build());
        options.addOption(Option.builder("as")
                .longOpt("autoSave")
                .desc("Automatically save failed tests. Waring, might save buggy baselines without human inspection. ")
                .hasArg(false)
                .build());
        options.addOption(Option.builder("os")
                .longOpt("hostOs")
                .desc("Set OS identifier for the screens under test")
                .hasArg()
                .argName("os")
                .build());
        options.addOption(Option.builder("ap")
                .longOpt("hostApp")
                .desc("Set Host-app identifier for the screens under test")
                .hasArg()
                .argName("app")
                .build());
        options.addOption(Option.builder("en")
                .longOpt("environmentName")
                .desc("Set environment name identifier for test")
                .hasArg()
                .argName("env")
                .build());
        options.addOption(Option.builder("di")
                .longOpt("dpi")
                .desc("PDF conversion dots per inch parameter default value 300")
                .hasArg()
                .argName("Dpi")
                .build());
        options.addOption(Option.builder("sp")
                .longOpt("selectedPages")
                .desc("Document pages to validate, default is the entire document")
                .hasArg()
                .argName("Pages")
                .build());
        options.addOption(Option.builder("tp")
                .longOpt("pdfTrim")
                .desc("Trim print margins from PDF pages before comparing. Bare `-tp` (or `-tp auto`) detects the trim area from TrimBox metadata or crop marks; `<width>x<height>` (PDF points) crops a centered box ie. 603x774")
                .hasArg()
                .optionalArg(true)
                .argName("auto|size")
                .build());
        options.addOption(Option.builder("sq")
                .longOpt("sequenceName")
                .desc("Set the batch sequenceName for applitools' insights")
                .hasArg()
                .argName("name")
                .build());
        options.addOption(Option.builder("id")
                .longOpt("ignoreDisplacement")
                .desc("Ignore displacement of shifting elements")
                .hasArg(false)
                .build());
        options.addOption(Option.builder("pn")
                .longOpt("pageNumbers")
                .desc("Include page numbers on document with selected pages (sp)")
                .hasArg(false)
                .build());
        options.addOption(Option.builder("st")
                .longOpt("split")
                .desc("Split tests to single-step tests")
                .hasArg(false)
                .build());
        options.addOption(Option.builder("debug")
                .hasArg(false)
                .desc("Turn on debug prints")
                .build());
        options.addOption(Option.builder("log")
                .hasArg(false)
                .desc("Turn on log prints")
                .build());
        options.addOption(Option.builder("pt")
                .longOpt("promptNewTests")
                .hasArg(false)
                .desc("Prompt new tests")
                .build());
        options.addOption(Option.builder("dv")
                .hasArg(false)
                .desc("Disable SSL certificate validation. !!!Unsecured!!!")
                .build());
        options.addOption(Option.builder("pp")
                .longOpt("PDFPassword")
                .desc("PDF Password")
                .hasArg()
                .argName("Password")
                .build());
        options.addOption(Option.builder("rt")
                .longOpt("renderThreads")
                .desc("Number of parallel page-render threads for multi-page PDF tests, default: min(4, cores - 1). Set 1 to disable parallel rendering.")
                .hasArg()
                .argName("units")
                .build());
        options.addOption(Option.builder("th")
                .longOpt("threads")
                .desc("Specify how many threads will be running the suite, default: 2 x available CPU cores")
                .hasArg()
                .argName("units")
                .build());
        options.addOption(Option.builder("fb")
                .longOpt("flatbatch")
                .desc("Aggregate all test results in a single batch (aka flat-batch)")
                .hasArg()
                .argName("name")
                .build());
        options.addOption(Option.builder("fn")
                .longOpt("forcedName")
                .desc("Force name for all tests, (will make all folders/files to be matched with a single baseline)")
                .hasArg()
                .argName("testName")
                .build());
        options.addOption(Option.builder("nc")
                .longOpt("notifyCompletion")
                .desc("Send batch notifications on completion")
                .hasArg(false)
                .build());
        options.addOption(Option.builder("ic")
                .longOpt("imageCut")
                .desc("set pixels to cut from each side (one or more) in the format [header,footer,left,right],partial missing notations ie: '-ic ,,10,4' ")
                .hasArgs()
                .valueSeparator(',')
                .build());
        options.addOption(Option.builder("lo")
                .longOpt("legacyFileOrder")
                .desc("Use legacy files order to comply with baselines that were created with versions below 2.0")
                .hasArg(false)
                .build());
        options.addOption(Option.builder("ac")
                .longOpt("accessibility")
                .desc("Set accessibility validation options in the format [Level:GuidelineVer], default: \"AA:WCAG_2_0\", including partial notations ie: \":WCAG_2_1\"")
                .numberOfArgs(2)
                .optionalArg(true)
                .valueSeparator(':') //, and not ; to avoid bash commands separation
                .argName(String.format("[%s]:[%s]", Utils.getEnumValues(AccessibilityLevel.class), Utils.getEnumValues(AccessibilityGuidelinesVersion.class)))
                .build());
        options.addOption(Option.builder("dcb")
                .longOpt("dontCloseBatch")
                .desc("Don't automatically close batch when tests are finished running")
                .hasArg(false)
                .build());
        options.addOption(Option.builder("mp")
                .longOpt("mapperPath")
                .desc("Path to Batch Mapper CSV, to be used with BatchMapper jar")
                .hasArgs()
                .build());
        options.addOption(Option.builder("ir")
                .longOpt("ignoreRegions")
                .desc("Parameters for ignore regions [x, y, width, height]")
                .hasArgs()
                .build());
        options.addOption(Option.builder("cr")
                .longOpt("contentRegions")
                .desc("Parameters for content regions [x, y, width, height]")
                .hasArgs()
                .build());
        options.addOption(Option.builder("lr")
                .longOpt("layoutRegions")
                .desc("Parameters for layout regions [x, y, width, height]")
                .hasArgs()
                .build());
        options.addOption(Option.builder("te")
                .longOpt("throwExceptions")
                .desc("Throw exceptions on test failure")
                .build());
        options.addOption(Option.builder("ari")
                .longOpt("accessibility region: ignore")
                .desc("Parameters for accessibility ignore regions [x, y, width, height]")
                .hasArgs()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("arr")
                .longOpt("accessibility region: regular text")
                .desc("Parameters for accessibility regular text regions [x, y, width, height]")
                .hasArgs()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("arl")
                .longOpt("accessibility region: large text")
                .desc("Parameters for accessibility large text regions [x, y, width, height]")
                .hasArgs()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("arb")
                .longOpt("accessibility region: bold text")
                .desc("Parameters for accessibility bold text regions [x, y, width, height]")
                .hasArgs()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("arg")
                .longOpt("accessibility region: graphic")
                .desc("Parameters for accessibility graphics regions [x, y, width, height]")
                .hasArgs()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("rc")
                .longOpt("regionCapture")
                .desc("Tests specific region of images and PDFs.\nexample: `-rc 0,200,1000,1000`")
                .hasArgs()
                .optionalArg(false)
                .build());
        options.addOption(Option.builder("mt")
                .longOpt("matchTimeout")
                .desc("Set value for match timeout and retry timeout in ms(minimum 500).\nexample: `-mt 2000`")
                .hasArgs()
                .optionalArg(false)
                .build());
        options.addOption(Option.builder("dn")
                .longOpt("deviceName")
                .desc("Set device name metadata.\nexample: `-dn 'my device'`")
                .hasArgs()
                .optionalArg(false)
                .build());
        options.addOption(Option.builder("rf")
            .longOpt("regexFilter")
            .desc("Test files with name that matches regexFilter pattern.\nexample: `-rf 'Quarterly_Report.*'`")
            .hasArg()
            .argName("regex")
            .build());
        options.addOption(Option.builder("pr")
            .longOpt("properties")
            .desc("Eyes Properties")
            .hasArgs()
            .build());
        options.addOption(Option.builder("nf")
            .longOpt("normalizeFont")
            .desc("Normalize all PDF fonts to Helvetica 12pt before rendering. Useful for ignoring font styling changes in visual comparisons.")
            .hasArg(false)
            .build());

        options.addOption(Option.builder("rw")
            .longOpt("removeWatermark")
            .desc("Remove text watermarks matching the given string from PDFs " +
                  "before rendering (case-insensitive, exact match). Body text " +
                  "matching the hint is also removed — pick a distinctive watermark word.")
            .hasArg()
            .argName("text")
            .build());

        options.addOption(Option.builder("rwo")
            .longOpt("removeWatermarkOut")
            .desc("Standalone mode: write watermark-cleaned PDFs to the given " +
                  "directory and exit. Combine with -rw or -rwauto. No upload to Applitools.")
            .hasArg()
            .argName("dir")
            .build());

        options.addOption(Option.builder("rwauto")
            .longOpt("removeWatermarkAuto")
            .desc("Auto-detect a vector watermark shared across all PDFs in the input " +
                  "directory by its fill color, then strip only paths in that color " +
                  "from each PDF, leaving all other content intact. Requires at least " +
                  "2 input PDFs from the same source. With -rwo, writes cleaned PDFs to " +
                  "that directory and exits. Without -rwo, cleans to a temp directory " +
                  "and uploads cleaned PDFs to Applitools.")
            .hasArg(false)
            .build());

        EyesUtilitiesConfig.injectOptions(options);
        return options;
    }
}