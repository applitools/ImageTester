package com.applitools.imagetester.lib;

import com.applitools.imagetester.Constants.ApplitoolsConstants;
import com.applitools.imagetester.ImageTester;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

public final class RunConfigFactory {

    private RunConfigFactory() {}

    public static RunConfig from(CommandLine cmd, Logger logger) throws ParseException {
        Config config = new Config();
        config.apiKey = cmd.getOptionValue("k", System.getenv(ApplitoolsConstants.APPLITOOLS_API_KEY));
        config.serverUrl = cmd.getOptionValue("s", System.getenv(ApplitoolsConstants.APPLITOOLS_SERVER_URL));

        String[] proxySettings = cmd.getOptionValues("p");
        if (proxySettings == null) {
            String proxyString = System.getenv(ApplitoolsConstants.APPLITOOLS_PROXY);
            proxySettings = proxyString != null ? proxyString.split(",") : null;
        }
        config.setProxy(proxySettings);

        String[] accessibilityOptions = cmd.getOptionValues("ac");
        accessibilityOptions = cmd.hasOption("ac") && accessibilityOptions == null ? new String[0] : accessibilityOptions;

        EyesFactory factory = new EyesFactory(ImageTester.CUR_VER, logger)
                .apiKey(config.apiKey)
                .serverUrl(config.serverUrl)
                .proxySettings(config.proxy_settings)
                .matchLevel(cmd.getOptionValue("ml", null))
                .branch(cmd.getOptionValue("br", null))
                .parentBranch(cmd.getOptionValue("pb", null))
                .baselineEnvName(cmd.getOptionValue("bn", null))
                .baselineBranchName(cmd.getOptionValue("bb", null))
                .logFile(cmd.getOptionValue("lf", null))
                .hostOs(cmd.getOptionValue("os", null))
                .hostApp(cmd.getOptionValue("ap"))
                .environmentName(cmd.getOptionValue("en"))
                .saveFailedTests(cmd.hasOption("as"))
                .ignoreDisplacement(cmd.hasOption("id"))
                .saveNewTests(!cmd.hasOption("pt"))
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
        config.normalizeFont = cmd.hasOption("nf");
        config.removeWatermarkText = cmd.getOptionValue("rw");
        config.removeWatermarkOutDir = cmd.getOptionValue("rwo");
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
        config.setProperties(cmd.getOptionValue("pr", null));

        if (cmd.hasOption("arr") && config.accessibilityRegularTextRegions == null)
            config.accessibilityRegularTextFullPage = true;
        if (cmd.hasOption("arl") && config.accessibilityLargeTextRegions == null)
            config.accessibilityLargeTextFullPage = true;
        if (cmd.hasOption("arb") && config.accessibilityBoldTextRegions == null)
            config.accessibilityBoldTextFullPage = true;
        if (cmd.hasOption("arg") && config.accessibilityGraphicsRegions == null)
            config.accessibilityGraphicsFullPage = true;

        config.eyesUtilsConf = new EyesUtilitiesConfig(cmd);

        int threads = Integer.parseInt(cmd.getOptionValue("th", ImageTester.DEFAULT_THREADS));
        return new RunConfig(config, factory, threads);
    }
}
