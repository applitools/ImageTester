package com.applitools.imagetester;

import com.applitools.eyes.AccessibilityGuidelinesVersion;
import com.applitools.eyes.AccessibilityLevel;
import com.applitools.eyes.MatchLevel;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.Region;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.EyesUtilitiesConfig;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.RunConfig;
import com.applitools.imagetester.lib.RunConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Argv-to-behavior contract for every CLI flag. Each test parses a realistic
 * command line through the real Options + DefaultParser, so a commons-cli
 * behavior change (like 1.6.0 dropping valueSeparator on optionalArg options,
 * which silently broke -ac, #49) turns CI red instead of shipping.
 */
public class CliContractTest {

    private static CommandLine parse(String... args) throws Exception {
        return new DefaultParser().parse(ImageTester.getOptions(), args);
    }

    private static RunConfig runConfig(String... args) throws Exception {
        return RunConfigFactory.from(parse(args), new Logger());
    }

    private static Config configFor(String... args) throws Exception {
        return runConfig(args).config;
    }

    private static Eyes eyesFor(String... args) throws Exception {
        return runConfig(args).factory.build();
    }

    // --- Connection: -k -s -p ---

    @Test
    public void apiKeyFlagSetsEyesApiKey() throws Exception {
        assertEquals("key", eyesFor("-k", "key").getApiKey());
    }

    @Test
    public void serverFlagSetsConfigServerUrl() throws Exception {
        assertEquals("https://eyes.example.com",
                configFor("-k", "key", "-s", "https://eyes.example.com").serverUrl);
    }

    @Test
    public void serverFlagSetsEyesServerUrl() throws Exception {
        Eyes eyes = eyesFor("-k", "key", "-s", "https://eyes.example.com");
        assertTrue(String.valueOf(eyes.getServerUrl()),
                String.valueOf(eyes.getServerUrl()).contains("eyes.example.com"));
    }

    @Test
    public void proxyFlagWithUrlOnlySetsProxyUri() throws Exception {
        assertEquals("http://proxy:8080",
                configFor("-k", "key", "-p", "http://proxy:8080").proxy_settings.getUri());
    }

    @Test
    public void proxyFlagSplitsCommaSeparatedCredentials() throws Exception {
        Config config = configFor("-k", "key", "-p", "http://proxy:8080,user,secret");
        assertEquals("secret", config.proxy_settings.getPassword());
    }

    @Test
    public void proxyFlagSplitsUriBeforeCredentials() throws Exception {
        Config config = configFor("-k", "key", "-p", "http://proxy:8080,user,secret");
        assertEquals("http://proxy:8080", config.proxy_settings.getUri());
    }

    @Test
    public void proxyFlagKeepsEmptyTrailingPassword() throws Exception {
        Config config = configFor("-k", "key", "-p", "http://proxy:8080,user,");
        assertEquals("", config.proxy_settings.getPassword());
    }

    @Test
    public void proxyFlagKeepsCommaInsidePassword() throws Exception {
        Config config = configFor("-k", "key", "-p", "http://proxy:8080,user,pa,ss");
        assertEquals("pa,ss", config.proxy_settings.getPassword());
    }

    // --- Identity: -a -fn -sq -fb -nc -dcb ---

    @Test
    public void appNameFlagSetsConfigAppName() throws Exception {
        assertEquals("MyApp", configFor("-k", "key", "-a", "MyApp").appName);
    }

    @Test
    public void appNameDefaultsToImageTester() throws Exception {
        assertEquals("ImageTester", configFor("-k", "key").appName);
    }

    @Test
    public void forcedNameFlagSetsForcedName() throws Exception {
        assertEquals("MyTest", configFor("-k", "key", "-fn", "MyTest").forcedName);
    }

    @Test
    public void sequenceNameFlagSetsSequenceName() throws Exception {
        assertEquals("seq1", configFor("-k", "key", "-sq", "seq1").sequenceName);
    }

    @Test
    public void flatBatchFlagSetsBatchName() throws Exception {
        assertEquals("MyBatch", configFor("-k", "key", "-fb", "MyBatch").flatBatch.getName());
    }

    @Test
    public void flatBatchFlagWithIdSetsBatchId() throws Exception {
        assertEquals("batch-1", configFor("-k", "key", "-fb", "MyBatch<>batch-1").flatBatch.getId());
    }

    @Test
    public void notifyCompletionFlagEnablesNotifyOnComplete() throws Exception {
        assertTrue(configFor("-k", "key", "-nc").notifyOnComplete);
    }

    @Test
    public void dontCloseBatchFlagEnablesDontCloseBatches() throws Exception {
        assertTrue(configFor("-k", "key", "-dcb").dontCloseBatches);
    }

    // --- Baseline selection: -br -pb -bn -bb -en -os -ap -dn ---

    @Test
    public void branchFlagSetsEyesBranchName() throws Exception {
        assertEquals("dev", eyesFor("-k", "key", "-br", "dev").getBranchName());
    }

    @Test
    public void parentBranchFlagSetsEyesParentBranchName() throws Exception {
        assertEquals("main", eyesFor("-k", "key", "-br", "dev", "-pb", "main").getParentBranchName());
    }

    @Test
    public void baselineFlagSetsEyesBaselineEnvName() throws Exception {
        assertEquals("base", eyesFor("-k", "key", "-bn", "base").getBaselineEnvName());
    }

    @Test
    public void baselineBranchFlagSetsEyesBaselineBranchName() throws Exception {
        assertEquals("bl", eyesFor("-k", "key", "-bb", "bl").getBaselineBranchName());
    }

    @Test
    public void environmentNameFlagSetsEyesEnvName() throws Exception {
        assertEquals("staging", eyesFor("-k", "key", "-en", "staging").getEnvName());
    }

    @Test
    public void hostOsFlagSetsEyesHostOs() throws Exception {
        assertEquals("Win11", eyesFor("-k", "key", "-os", "Win11").getHostOS());
    }

    @Test
    public void hostAppFlagSetsEyesHostApp() throws Exception {
        assertEquals("Chrome", eyesFor("-k", "key", "-ap", "Chrome").getHostApp());
    }

    @Test
    public void deviceNameFlagSetsEyesDeviceInfo() throws Exception {
        assertEquals("Pixel 9",
                eyesFor("-k", "key", "-dn", "Pixel 9").getConfiguration().getDeviceInfo());
    }

    // --- Matching: -ml -id -mt -as -pt ---

    @Test
    public void matchLevelFlagSetsEyesMatchLevel() throws Exception {
        assertEquals(MatchLevel.LAYOUT, eyesFor("-k", "key", "-ml", "Layout").getMatchLevel());
    }

    @Test
    public void ignoreDisplacementFlagEnablesIgnoreDisplacements() throws Exception {
        assertTrue(eyesFor("-k", "key", "-id").getIgnoreDisplacements());
    }

    @Test
    public void matchTimeoutFlagSetsMatchTimeout() throws Exception {
        assertEquals("2000", configFor("-k", "key", "-mt", "2000").matchTimeout);
    }

    @Test
    public void autoSaveFlagEnablesSaveFailedTests() throws Exception {
        assertTrue(eyesFor("-k", "key", "-as").getSaveFailedTests());
    }

    @Test
    public void promptNewTestsFlagDisablesSaveNewTests() throws Exception {
        assertFalse(eyesFor("-k", "key", "-pt").getSaveNewTests());
    }

    @Test
    public void saveNewTestsDefaultsToTrue() throws Exception {
        assertTrue(eyesFor("-k", "key").getSaveNewTests());
    }

    // --- Geometry: -vs -ms -ic -rc ---

    @Test
    public void viewportSizeFlagSetsConfigViewport() throws Exception {
        assertEquals(new RectangleSize(1000, 600),
                configFor("-k", "key", "-vs", "1000x600").viewport);
    }

    @Test
    public void matchSizeFlagSetsMatchWidth() throws Exception {
        assertEquals("1000", configFor("-k", "key", "-ms", "1000x600").matchWidth);
    }

    @Test
    public void matchSizeFlagSetsMatchHeight() throws Exception {
        assertEquals("600", configFor("-k", "key", "-ms", "1000x600").matchHeight);
    }

    @Test
    public void matchSizeHeightOnlySetsOnlyHeight() throws Exception {
        assertEquals("600", configFor("-k", "key", "-ms", "x600").matchHeight);
    }

    @Test
    public void matchSizeWidthOnlyLeavesHeightNull() throws Exception {
        assertNull(configFor("-k", "key", "-ms", "1000x").matchHeight);
    }

    @Test
    public void imageCutFlagSplitsFourValues() throws Exception {
        assertArrayEquals(new String[]{"10", "20", "30", "40"},
                parse("-ic", "10,20,30,40").getOptionValues("ic"));
    }

    @Test
    public void imageCutFlagKeepsEmptyLeadingValues() throws Exception {
        assertArrayEquals(new String[]{"", "", "10", "4"},
                parse("-ic", ",,10,4").getOptionValues("ic"));
    }

    @Test
    public void regionCaptureFlagSetsCaptureRegion() throws Exception {
        assertEquals(new Region(0, 200, 1000, 1000),
                configFor("-k", "key", "-rc", "0,200,1000,1000").captureRegion);
    }

    // --- Regions: -ir -cr -lr ---

    @Test
    public void ignoreRegionsFlagSetsIgnoreRegions() throws Exception {
        assertEquals(new Region(100, 100, 100, 100),
                configFor("-k", "key", "-ir", "100,100,100,100").ignoreRegions[0]);
    }

    @Test
    public void contentRegionsFlagSetsContentRegions() throws Exception {
        assertEquals(new Region(200, 200, 200, 200),
                configFor("-k", "key", "-cr", "200,200,200,200").contentRegions[0]);
    }

    @Test
    public void layoutRegionsFlagSetsLayoutRegions() throws Exception {
        assertEquals(new Region(300, 300, 300, 300),
                configFor("-k", "key", "-lr", "300,300,300,300").layoutRegions[0]);
    }

    @Test
    public void pipeSeparatedRegionsProduceMultipleRegions() throws Exception {
        assertEquals(2, configFor("-k", "key", "-ir", "1,2,3,4|5,6,7,8").ignoreRegions.length);
    }

    // --- Accessibility: -ac -ari -arr -arl -arb -arg ---

    @Test
    public void bareAcFlagDefaultsToLevelAA() throws Exception {
        assertEquals(AccessibilityLevel.AA,
                eyesFor("-k", "key", "-ac").getAccessibilityValidation().getLevel());
    }

    @Test
    public void bareAcFlagDefaultsToWcag20() throws Exception {
        assertEquals(AccessibilityGuidelinesVersion.WCAG_2_0,
                eyesFor("-k", "key", "-ac").getAccessibilityValidation().getGuidelinesVersion());
    }

    @Test
    public void acFlagLevelOnlySetsLevel() throws Exception {
        assertEquals(AccessibilityLevel.AAA,
                eyesFor("-k", "key", "-ac", "AAA").getAccessibilityValidation().getLevel());
    }

    @Test
    public void acFlagCombinedTokenSetsGuidelinesVersion() throws Exception {
        assertEquals(AccessibilityGuidelinesVersion.WCAG_2_1,
                eyesFor("-k", "key", "-ac", "AA:WCAG_2_1").getAccessibilityValidation().getGuidelinesVersion());
    }

    @Test
    public void acFlagColonPrefixedVersionKeepsDefaultLevel() throws Exception {
        assertEquals(AccessibilityLevel.AA,
                eyesFor("-k", "key", "-ac", ":WCAG_2_1").getAccessibilityValidation().getLevel());
    }

    @Test
    public void accessibilityIgnoreRegionsFlagSetsRegions() throws Exception {
        assertEquals(new Region(1, 2, 3, 4),
                configFor("-k", "key", "-ari", "1,2,3,4").accessibilityIgnoreRegions[0]);
    }

    @Test
    public void bareAriFlagParsesWithoutRegions() throws Exception {
        assertNull(configFor("-k", "key", "-ari").accessibilityIgnoreRegions);
    }

    @Test
    public void accessibilityRegularTextRegionsFlagSetsRegions() throws Exception {
        assertEquals(new Region(1, 2, 3, 4),
                configFor("-k", "key", "-arr", "1,2,3,4").accessibilityRegularTextRegions[0]);
    }

    @Test
    public void bareArrFlagEnablesFullPageRegularText() throws Exception {
        assertTrue(configFor("-k", "key", "-arr").accessibilityRegularTextFullPage);
    }

    @Test
    public void accessibilityLargeTextRegionsFlagSetsRegions() throws Exception {
        assertEquals(new Region(1, 2, 3, 4),
                configFor("-k", "key", "-arl", "1,2,3,4").accessibilityLargeTextRegions[0]);
    }

    @Test
    public void bareArlFlagEnablesFullPageLargeText() throws Exception {
        assertTrue(configFor("-k", "key", "-arl").accessibilityLargeTextFullPage);
    }

    @Test
    public void accessibilityBoldTextRegionsFlagSetsRegions() throws Exception {
        assertEquals(new Region(1, 2, 3, 4),
                configFor("-k", "key", "-arb", "1,2,3,4").accessibilityBoldTextRegions[0]);
    }

    @Test
    public void bareArbFlagEnablesFullPageBoldText() throws Exception {
        assertTrue(configFor("-k", "key", "-arb").accessibilityBoldTextFullPage);
    }

    @Test
    public void accessibilityGraphicsRegionsFlagSetsRegions() throws Exception {
        assertEquals(new Region(1, 2, 3, 4),
                configFor("-k", "key", "-arg", "1,2,3,4").accessibilityGraphicsRegions[0]);
    }

    @Test
    public void bareArgFlagEnablesFullPageGraphics() throws Exception {
        assertTrue(configFor("-k", "key", "-arg").accessibilityGraphicsFullPage);
    }

    // --- Documents: -di -sp -pn -pp -tp -nf -rf ---

    @Test
    public void dpiFlagSetsDocumentConversionDpi() throws Exception {
        assertEquals(400f, configFor("-k", "key", "-di", "400").DocumentConversionDPI, 0.001f);
    }

    @Test
    public void dpiDefaultsTo250() throws Exception {
        assertEquals(250f, configFor("-k", "key").DocumentConversionDPI, 0.001f);
    }

    @Test
    public void selectedPagesFlagSetsPages() throws Exception {
        assertEquals("1-3", configFor("-k", "key", "-sp", "1-3").pages);
    }

    @Test
    public void pageNumbersFlagEnablesIncludePageNumbers() throws Exception {
        assertTrue(configFor("-k", "key", "-pn").includePageNumbers);
    }

    @Test
    public void pdfPasswordFlagSetsPdfPass() throws Exception {
        assertEquals("s3cret", configFor("-k", "key", "-pp", "s3cret").pdfPass);
    }

    @Test
    public void barePdfTrimFlagEnablesAutoTrim() throws Exception {
        assertEquals(Config.PDF_TRIM_AUTO, configFor("-k", "key", "-tp").pdfTrim);
    }

    @Test
    public void pdfTrimFlagWithSizeKeepsExplicitValue() throws Exception {
        assertEquals("603x774", configFor("-k", "key", "-tp", "603x774").pdfTrim);
    }

    @Test
    public void normalizeFontFlagEnablesNormalizeFont() throws Exception {
        assertTrue(configFor("-k", "key", "-nf").normalizeFont);
    }

    @Test
    public void regexFilterFlagSetsFileNameFilter() throws Exception {
        assertEquals("Report.*", configFor("-k", "key", "-rf", "Report.*").regexFileNameFilter);
    }

    // --- Execution: -th -rt -st -lo -te -f ---

    @Test
    public void threadsFlagSetsExecutorThreads() throws Exception {
        assertEquals(7, runConfig("-k", "key", "-th", "7").threads);
    }

    @Test
    public void renderThreadsFlagSetsRenderThreads() throws Exception {
        assertEquals(3, configFor("-k", "key", "-rt", "3").renderThreads);
    }

    @Test
    public void splitFlagEnablesSplitSteps() throws Exception {
        assertTrue(configFor("-k", "key", "-st").splitSteps);
    }

    @Test
    public void legacyFileOrderFlagEnablesLegacyOrder() throws Exception {
        assertTrue(configFor("-k", "key", "-lo").legacyFileOrder);
    }

    @Test
    public void throwExceptionsFlagEnablesShouldThrow() throws Exception {
        assertTrue(configFor("-k", "key", "-te").shouldThrowException);
    }

    @Test
    public void folderFlagParsesPath() throws Exception {
        assertEquals("some/dir", parse("-f", "some/dir").getOptionValue("f"));
    }

    // --- Properties: -pr ---

    @Test
    public void propertiesFlagParsesKeyValuePair() throws Exception {
        assertArrayEquals(new String[]{"env", "prod"},
                configFor("-k", "key", "-pr", "env:prod").properties[0]);
    }

    @Test
    public void propertiesFlagParsesMultiplePairs() throws Exception {
        assertEquals(2, configFor("-k", "key", "-pr", "a:1|b:2").properties.length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void propertiesFlagWithoutColonThrows() throws Exception {
        configFor("-k", "key", "-pr", "noColon");
    }

    // --- Logging: -lf -debug -log -dv ---

    @Test
    public void bareLogFileFlagParses() throws Exception {
        assertTrue(parse("-k", "key", "-lf").hasOption("lf"));
    }

    @Test
    public void logFileFlagWithValueParses() throws Exception {
        assertEquals("log.txt", parse("-k", "key", "-lf", "log.txt").getOptionValue("lf"));
    }

    @Test
    public void debugFlagParses() throws Exception {
        assertTrue(parse("-debug").hasOption("debug"));
    }

    // The universal SDK ignores setLogHandler (returns NullLogHandler and writes its own
    // file logs), so -log has no observable downstream effect — pin the parse contract only.
    @Test
    public void logFlagParses() throws Exception {
        assertTrue(parse("-log").hasOption("log"));
    }

    @Test
    public void disableCertValidationFlagParses() throws Exception {
        assertTrue(parse("-dv").hasOption("dv"));
    }

    // --- Watermarks: -rw -rwo -rwauto ---

    @Test
    public void removeWatermarkFlagSetsWatermarkText() throws Exception {
        assertEquals("DRAFT", configFor("-k", "key", "-rw", "DRAFT").removeWatermarkText);
    }

    @Test
    public void removeWatermarkOutFlagSetsOutDir() throws Exception {
        assertEquals("out", configFor("-k", "key", "-rw", "DRAFT", "-rwo", "out").removeWatermarkOutDir);
    }

    @Test
    public void removeWatermarkAutoFlagParses() throws Exception {
        assertTrue(parse("-rwauto").hasOption("rwauto"));
    }

    // --- Compare mode / batch mapper: -doc1 -doc2 -mp ---

    @Test
    public void doc1FlagParsesPath() throws Exception {
        assertEquals("a.pdf", parse("-doc1", "a.pdf", "-doc2", "b.pdf").getOptionValue("doc1"));
    }

    @Test
    public void doc2FlagParsesPath() throws Exception {
        assertEquals("b.pdf", parse("-doc1", "a.pdf", "-doc2", "b.pdf").getOptionValue("doc2"));
    }

    @Test
    public void mapperPathFlagParsesPath() throws Exception {
        assertEquals("map.csv", parse("-mp", "map.csv").getOptionValue("mp"));
    }

    // --- EyesUtilities: -vk -of -gd -gi -gg ---

    @Test
    public void getDiffsWithViewKeyEnablesDownloadDiffs() throws Exception {
        EyesUtilitiesConfig utils = new EyesUtilitiesConfig(parse("-k", "key", "-gd", "-vk", "vkey"));
        assertTrue(utils.getDownloadDiffs());
    }

    @Test
    public void getImagesWithViewKeyEnablesGetImages() throws Exception {
        EyesUtilitiesConfig utils = new EyesUtilitiesConfig(parse("-k", "key", "-gi", "-vk", "vkey"));
        assertTrue(utils.getGetImages());
    }

    @Test
    public void getGifsWithViewKeyEnablesGetGifs() throws Exception {
        EyesUtilitiesConfig utils = new EyesUtilitiesConfig(parse("-k", "key", "-gg", "-vk", "vkey"));
        assertTrue(utils.getGetGifs());
    }

    @Test
    public void viewKeyFlagSetsViewKey() throws Exception {
        EyesUtilitiesConfig utils = new EyesUtilitiesConfig(parse("-k", "key", "-gd", "-vk", "vkey"));
        assertEquals("vkey", utils.getViewKey());
    }

    @Test
    public void outFolderFlagSetsDestinationFolder() throws Exception {
        EyesUtilitiesConfig utils = new EyesUtilitiesConfig(
                parse("-k", "key", "-gd", "-vk", "vkey", "-of", "results"));
        assertEquals("results", utils.getDestinationFolder());
    }

    @Test(expected = ParseException.class)
    public void getDiffsWithoutViewKeyThrows() throws Exception {
        Assume.assumeTrue(System.getenv("APPLITOOLS_VIEW_KEY") == null);
        new EyesUtilitiesConfig(parse("-k", "key", "-gd"));
    }

    // --- Whole-run argument validation (exits before any Eyes work) ---

    @Test
    public void noArgsExitsZero() {
        assertEquals(0, ImageTester.run(new String[]{}));
    }

    @Test
    public void unknownFlagExitsWithParseError() {
        assertEquals(1, ImageTester.run(new String[]{"-zz"}));
    }

    @Test
    public void guiFlagWithExtraArgsExitsWithUsageError() {
        assertEquals(2, ImageTester.run(new String[]{"--gui", "-k", "key"}));
    }

    @Test
    public void rwoWithoutRwExitsWithError() {
        assertEquals(1, ImageTester.run(new String[]{"-k", "key", "-rwo", "out"}));
    }

    @Test
    public void mapperWithMissingCsvExitsWithError() {
        assertEquals(1, ImageTester.run(new String[]{"-k", "key", "-mp", "does-not-exist.csv"}));
    }

    @Test
    public void blankRwValueExitsWithError() {
        assertEquals(1, ImageTester.run(new String[]{"-k", "key", "-f", ".", "-rw", ""}));
    }
}
