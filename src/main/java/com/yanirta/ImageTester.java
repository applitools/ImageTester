package com.yanirta;

import com.applitools.eyes.AccessibilityGuidelinesVersion;
import com.applitools.eyes.AccessibilityLevel;
import com.applitools.eyes.MatchLevel;
import com.yanirta.lib.*;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class ImageTester {
    private static final String cur_ver = ImageTester.class.getPackage().getImplementationVersion();

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = getOptions();
        Logger logger = new Logger();

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

            Config config = new Config();
            config.apiKey = cmd.getOptionValue("k", System.getenv("APPLITOOLS_API_KEY"));
            config.serverUrl = cmd.getOptionValue("s", null);
            config.setProxy(cmd.getOptionValues("p"));
            String[] accessibilityOptions = cmd.getOptionValues("ac");
            accessibilityOptions = cmd.hasOption("ac") && accessibilityOptions == null ? new String[0] : accessibilityOptions;

            // Eyes factory
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
                    .saveFaliedTests(cmd.hasOption("as"))
                    .ignoreDisplacement(cmd.hasOption("id"))
                    .saveNewTests(!cmd.hasOption("pn"))
                    .imageCut(cmd.getOptionValues("ic"))
                    .accSettings(accessibilityOptions);


            config.splitSteps = cmd.hasOption("st");
            config.logger = logger;

            config.appName = cmd.getOptionValue("a", "ImageTester");
            config.DocumentConversionDPI = Float.valueOf(cmd.getOptionValue("di", "250"));
            config.pdfPass = cmd.getOptionValue("pp", null);
            config.pages = cmd.getOptionValue("sp", null);
            config.includePageNumbers = cmd.hasOption("pn");
            config.forcedName = cmd.getOptionValue("fn", null);
            config.sequenceName = cmd.getOptionValue("sq", null);
            config.legacyFileOrder = cmd.hasOption("lo");
            config.setViewport(cmd.getOptionValue("vs", null));
            config.setMatchSize(cmd.getOptionValue("ms", null));
            config.setBatchInfo(
                    cmd.getOptionValue("fb", null),
                    cmd.hasOption("nc"));
            config.dontCloseBatches = cmd.hasOption("dcb");

            File root = new File(cmd.getOptionValue("f", "."));
            int maxThreads = Integer.parseInt(cmd.getOptionValue("th", "3"));

            // Tests executor
            TestExecutor executor = new TestExecutor(maxThreads, factory, config);

            // Suite
            Suite suite = Suite.create(root.getCanonicalFile(), config, executor);

            // EyesUtilities config
            config.eyesUtilsConf = new EyesUtilitiesConfig(cmd);

            if (suite == null) {
                System.out.println("Nothing to test!\n");
                System.exit(0);
            }

            suite.run();

            //close batches before exit
            config.closeBatches();

            //exit
            System.exit(0);
        } catch (ParseException e) {
            logger.reportException(e);
            logger.printHelp(options);
            System.exit(-1);
        } catch (IOException e) {
            logger.reportException(e);
            logger.printHelp(options);
            System.exit(-1);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (KeyManagementException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    //k,a,f,p,s,ml,bd,pb,bn,vs,lf,as,os,ap,di,sp,pn,pp,th
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

        EyesUtilitiesConfig.injectOptions(options);

        return options;
    }
}
