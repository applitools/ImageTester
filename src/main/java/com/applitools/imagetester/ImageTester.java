package com.applitools.imagetester;

import java.io.File;
import java.io.IOException;
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
import com.applitools.imagetester.lib.TestExecutor;
import com.applitools.imagetester.lib.Utils;

public class ImageTester {
    private static final String cur_ver = "3.5.2";

    public static void main(String[] args) {

        CommandLineParser parser = new DefaultParser();
        Options options = getOptions();
        Logger logger = new Logger();

        // PDFBox generates fairly unhelpful logs - suppressing these by default
        java.util.logging.Logger.getLogger("org.apache.pdfbox").setLevel(java.util.logging.Level.OFF);

        try {
            CommandLine cmd = parser.parse(options, args);
            logger.setDebug(cmd.hasOption("debug"));
            logger.printVersion(cur_ver);

            if (cmd.getOptions().length == 0) {
                logger.printHelp(options);
                return;
            }

            if (cmd.hasOption("dv"))
                Utils.disableCertValidation();

            String batchMapperPath = cmd.getOptionValue("mp", null);
            if (batchMapperPath != null) {
                runTestWithBatchMapper(logger, cmd);
            }

            Config config = new Config();
            config.apiKey = cmd.getOptionValue("k", System.getenv(ApplitoolsConstants.APPLITOOLS_API_KEY));
            config.serverUrl = cmd.getOptionValue("s", System.getenv(ApplitoolsConstants.APPLITOOLS_SERVER_URL));

            String[] proxySettings = cmd.getOptionValues("p");

            if(proxySettings == null) {
                String proxyString = System.getenv(ApplitoolsConstants.APPLITOOLS_PROXY);
                proxySettings = proxyString != null ? proxyString.split(",") : null;
            }

            config.setProxy(proxySettings);

            String[] accessibilityOptions = cmd.getOptionValues("ac");
            accessibilityOptions = cmd.hasOption("ac") && accessibilityOptions == null ? new String[0] : accessibilityOptions;

            EyesFactory factory
                    = new EyesFactory(cur_ver, logger)
                    .apiKey(config.apiKey)
                    .serverUrl(config.serverUrl)
                    .proxySettings(config.proxy_settings)
                    .matchLevel(cmd.getOptionValue("ml", null))
                    .branch(cmd.getOptionValue("br", null))
                    .parentBranch(cmd.getOptionValue("pb", null))
                    .baselineEnvName(cmd.getOptionValue("bn", null))
                    .logFile(cmd.getOptionValue("lf", null))
                    .hostOs(cmd.getOptionValue("os", null))
                    .hostApp(cmd.getOptionValue("ap"))
                    .environmentName(cmd.getOptionValue("en"))
                    .saveFaliedTests(cmd.hasOption("as"))
                    .ignoreDisplacement(cmd.hasOption("id"))
                    .saveNewTests(!cmd.hasOption("pn"))
                    .imageCut(cmd.getOptionValues("ic"))
                    .accSettings(accessibilityOptions)
                    .logHandler(cmd.hasOption("log"))
                    .deviceName(cmd.getOptionValue("dn", null));

            config.splitSteps = cmd.hasOption("st");
            config.logger = logger;
            config.appName = cmd.getOptionValue("a", "ImageTester");
            config.DocumentConversionDPI = Float.parseFloat(cmd.getOptionValue("di", "250"));
            config.pdfPass = cmd.getOptionValue("pp", null);
            config.pages = cmd.getOptionValue("sp", null);
            config.includePageNumbers = cmd.hasOption("pn");
            config.forcedName = cmd.getOptionValue("fn", null);
            config.sequenceName = cmd.getOptionValue("sq", null);
            config.legacyFileOrder = cmd.hasOption("lo");
            config.dontCloseBatches = cmd.hasOption("dcb");
            config.shouldThrowException = cmd.hasOption("te");
            config.regexFileNameFilter = cmd.getOptionValue("rf");
            config.setViewport(cmd.getOptionValue("vs", null));
            config.setMatchSize(cmd.getOptionValue("ms", null));
            config.setBatchInfo(cmd.getOptionValue("fb", null), cmd.hasOption("nc"));
            config.setIgnoreRegions(cmd.getOptionValue("ir", null));
            config.setContentRegions(cmd.getOptionValue("cr", null));
            config.setLayoutRegions(cmd.getOptionValue("lr", null));
            config.setAccessibilityIgnoreRegions(cmd.getOptionValue("ari", null));
            config.setAccessibilityRegularTextRegions(cmd.getOptionValue("arr", null));
            config.setAccessibilityLargeTextRegions(cmd.getOptionValue("arl", null));
            config.setAccessibilityBoldTextRegions(cmd.getOptionValue("arb", null));
            config.setAccessibilityGraphicsRegions(cmd.getOptionValue("arg", null));
            config.setCaptureRegion(cmd.getOptionValue("rc", null));
            config.setMatchTimeout(cmd.getOptionValue("mt", null));

            // Full page for ac regions capability
            if (cmd.hasOption("arr") && config.accessibilityRegularTextRegions == null) {
                config.accessibilityRegularTextFullPage = cmd.hasOption("arr") && config.accessibilityRegularTextRegions == null;
            }
            if (cmd.hasOption("arl") && config.accessibilityLargeTextRegions == null) {
                config.accessibilityLargeTextFullPage = true;
            }
            if (cmd.hasOption("arb") && config.accessibilityBoldTextRegions == null) {
                config.accessibilityBoldTextFullPage = true;
            }
            if (cmd.hasOption("arg") && config.accessibilityGraphicsRegions== null) {
                config.accessibilityGraphicsFullPage = true;
            }

            File root = new File(cmd.getOptionValue("f", "."));

            int maxThreads = Integer.parseInt(cmd.getOptionValue("th", "3"));
            TestExecutor executor = new TestExecutor(maxThreads, factory, config);

            Suite suite = Suite.create(root.getCanonicalFile(), config, executor);

            config.eyesUtilsConf = new EyesUtilitiesConfig(cmd);

            suite.run();

            config.closeBatches();

            System.exit(0);
        } catch (ParseException | IOException e) {
            logger.reportException(e);
            logger.printHelp(options);
            System.exit(-1);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.reportException(e);
            System.exit(-1);
        }
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
                        = new EyesFactory(cur_ver, logger)
                        .apiKey(currentConfiguration.apiKey)
                        .serverUrl(currentConfiguration.serverUrl)
                        .proxySettings(currentConfiguration.proxy_settings)
                        .matchLevel(currentBatch.matchLevel)
                        .branch(cmd.getOptionValue("br", null))
                        .parentBranch(cmd.getOptionValue("pb", null))
                        .baselineEnvName(cmd.getOptionValue("bn", null))
                        .logFile(cmd.getOptionValue("lf", null))
                        .hostOs(currentBatch.os)
                        .hostApp(currentBatch.browser)
                        .saveFaliedTests(cmd.hasOption("as"))
                        .ignoreDisplacement(cmd.hasOption("id"))
                        .saveNewTests(!cmd.hasOption("pn"))
                        .imageCut(cmd.getOptionValues("ic"))
                        .accSettings(accessibilityOptions)
                        .deviceName(cmd.getOptionValue("dn", null));
                currentConfiguration.splitSteps = cmd.hasOption("st");
                currentConfiguration.logger = logger;
                currentConfiguration.appName = currentBatch.app;
                currentConfiguration.DocumentConversionDPI = Float.parseFloat(cmd.getOptionValue("di", "250"));
                currentConfiguration.pdfPass = cmd.getOptionValue("pp", null);
                currentConfiguration.pages = currentBatch.pages;
                currentConfiguration.includePageNumbers = cmd.hasOption("pn");
                currentConfiguration.forcedName = currentBatch.testName;
                currentConfiguration.sequenceName = cmd.getOptionValue("sq", null);
                currentConfiguration.legacyFileOrder = cmd.hasOption("lo");
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

                // Full page for ac regions capability
                if (cmd.hasOption("arr") && currentConfiguration.accessibilityRegularTextRegions == null) {
                    currentConfiguration.accessibilityRegularTextFullPage = cmd.hasOption("arr") && currentConfiguration.accessibilityRegularTextRegions == null;
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
                    int maxThreads = Integer.parseInt(cmd.getOptionValue("th", "3"));
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
        } finally {
            System.exit(0);
        }
    }

    private static Options getOptions() {
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
        options.addOption(Option.builder("pn")
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
        options.addOption(Option.builder("th")
                .longOpt("threads")
                .desc("Specify how many threads will be running the suite")
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
            .desc("Test files with name that matches regexFilter pattern.\nexample: `-rf 'Quarterly_Report_*'")
            .hasArgs()
            .optionalArg(false)
            .build());


        EyesUtilitiesConfig.injectOptions(options);
        return options;
    }
}