package com.applitools.imagetester.lib;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.ProxySettings;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.Region;
import com.applitools.eyes.fluent.BatchClose;
import com.applitools.eyes.fluent.EnabledBatchClose;
import com.applitools.imagetester.Constants.ApplitoolsConstants;
import com.applitools.imagetester.lib.converters.SkipTracker;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;

public class Config {
    private static final int MAX_DEFAULT_RENDER_THREADS = 4;

    public RectangleSize viewport;
    public int renderThreads = defaultRenderThreads();
    public String appName = "ImageTester";
    public float DocumentConversionDPI = 250;
    public boolean splitSteps = false;
    public String pages = null;
    public String pdfPass = null;
    public boolean includePageNumbers = false;
    public Logger logger = new Logger();
    public SkipTracker skipTracker = new SkipTracker();
    public EyesUtilitiesConfig eyesUtilsConf;
    public BatchInfo flatBatch = null;
    public String forcedName = null;
    public String sequenceName = null;
    public boolean notifyOnComplete = false;
    public String apiKey;
    public String serverUrl;
    public ProxySettings proxy_settings = null;
    public String matchWidth = null;
    public String matchHeight = null;
    public boolean legacyFileOrder = false;
    public boolean dontCloseBatches = false;
    public String batchMapperPath = null;
    public Region[] ignoreRegions = null;
    public Region[] layoutRegions = null;
    public Region[] contentRegions = null;
    public boolean shouldThrowException = false;
    public boolean normalizeFont = false;
    public String removeWatermarkText = null;
    public String removeWatermarkOutDir = null;
    private final HashSet<String> batchesIdListForBatchClose = new HashSet<>();
    public Region[] accessibilityIgnoreRegions = null;
    public Region[] accessibilityRegularTextRegions = null;
    public Region[] accessibilityLargeTextRegions = null;
    public Region[] accessibilityBoldTextRegions = null;
    public Region[] accessibilityGraphicsRegions = null;
    public boolean accessibilityRegularTextFullPage = false;
    public boolean accessibilityLargeTextFullPage = false;
    public boolean accessibilityBoldTextFullPage = false;
    public boolean accessibilityGraphicsFullPage = false;
    public Region captureRegion;
    public String matchTimeout;
    public String deviceName;
    public String regexFileNameFilter;
    public String[][] properties = null;

    // Leave one core for the Eyes check thread and the universal-core process.
    private static int defaultRenderThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.min(MAX_DEFAULT_RENDER_THREADS, Math.max(1, cores - 1));
    }

    public void setViewport(String viewport) {
        if (viewport == null) return;
        String[] dims = viewport.split("x");
        if (dims.length != 2)
            throw new RuntimeException("invalid viewport-size, make sure the call is -vs <width>x<height>");
        this.viewport = new RectangleSize(
                Integer.parseInt(dims[0]),
                Integer.parseInt(dims[1]));
    }

    public void setCaptureRegion(String captureRegionSpecs) {
        if (captureRegionSpecs == null) return;
        String[] regionSpecs = captureRegionSpecs.split(",");
        if (regionSpecs.length != 4)
            throw new RuntimeException("Invalid region capture values, make sure the call is -rc <left>,<top>,<width>,<height>");
        this.captureRegion = new Region(
                Integer.parseInt(regionSpecs[0]),
                Integer.parseInt(regionSpecs[1]),
                Integer.parseInt(regionSpecs[2]),
                Integer.parseInt(regionSpecs[3])
        );
    }

    public void setProxy(String[] proxy) {
        if (proxy != null && proxy.length > 0)
            if (proxy.length == 1) {
                logger.reportDebug("Using proxy %s \n", proxy[0]);
                proxy_settings = new ProxySettings(proxy[0]);
            } else if (proxy.length == 3) {
                logger.reportDebug("Using proxy %s with user %s and pass %s \n", proxy[0], proxy[1], proxy[2]);
                proxy_settings = new ProxySettings(proxy[0], proxy[1], proxy[2]);
            } else
                throw new RuntimeException("Proxy setting are invalid");
    }

    public void setMatchSize(String size) {
        if (size == null)
            return;
        String[] dims = size.split("x");
        matchWidth = dims[0];
        if (dims.length > 1)
            matchHeight = dims[1];
    }

    //set batch related info
    public void setBatchInfo(String flatBatchArg, boolean notifyOnComplete) {
        this.notifyOnComplete = notifyOnComplete;
        //set batch- take flat batch if described- get environment variables values unless overwritten
        String batchNameToAdd = System.getenv(ApplitoolsConstants.APPLITOOLS_JOB_NAME);
        String batchIdToAdd = System.getenv(ApplitoolsConstants.APPLITOOLS_BATCH_ID);

        //set flat batch- config.notify complete must be before this set
        if (StringUtils.isNoneBlank(flatBatchArg)) {
            String[] batch_parts = flatBatchArg.split("<>");
            //check if batch id was specified
            batchNameToAdd = batch_parts[0];
            batchIdToAdd = batch_parts.length > 1 ? batch_parts[1] : null;
        }

        //if flat batch name is not empty initialize flat batch
        if (StringUtils.isNoneBlank(batchNameToAdd)) {
            flatBatch = new BatchInfo(batchNameToAdd);
            //if flat batch id is not empty set batch id
            if (StringUtils.isNoneBlank(batchIdToAdd))
                flatBatch.setId(batchIdToAdd);
        }
    }

    //add batch id to list
    public void addBatchIdToCloseList(String batchId) {
        batchesIdListForBatchClose.add(batchId);
    }

    //close batches
    public void closeBatches() {
        if (notifyOnComplete) {
            BatchClose batchClose = new BatchClose();
            batchClose.setApiKey(apiKey);
            if (serverUrl != null)
                batchClose.setUrl(serverUrl);
            if (proxy_settings != null)
                batchClose.setProxy(proxy_settings);
            EnabledBatchClose enabledBatchClose = batchClose.setBatchId(new ArrayList<>(batchesIdListForBatchClose));
            if (!dontCloseBatches) enabledBatchClose.close();
        }
    }

    private Region[] generateRegionsArray(String regionOption) {
        String[] regionStrings = regionOption.split("\\|");
        return Arrays.stream(regionStrings)
            .map(regionString -> {
                String[] regionParameters = regionString.split(",");
                return new Region(
                    Integer.parseInt(regionParameters[0]),
                    Integer.parseInt(regionParameters[1]),
                    Integer.parseInt(regionParameters[2]),
                    Integer.parseInt(regionParameters[3])
                );
            })
            .toArray(Region[]::new);
    }

    private Region[] parseRegions(String input, String label) {
        if (input == null) return null;
        try {
            return generateRegionsArray(input);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            logger.printMessage("Error parsing parameters for " + label + ". " +
                    "Please ensure that the " + label + " are in the format x,y,width,height|x,y,width,height...");
            return null;
        }
    }

    public void setIgnoreRegions(String ignoreRegionsOption) {
        Region[] parsed = parseRegions(ignoreRegionsOption, "ignore regions");
        if (parsed != null) this.ignoreRegions = parsed;
    }

    public void setContentRegions(String contentRegionsOption) {
        Region[] parsed = parseRegions(contentRegionsOption, "content regions");
        if (parsed != null) this.contentRegions = parsed;
    }

    public void setLayoutRegions(String layoutRegionsOption) {
        Region[] parsed = parseRegions(layoutRegionsOption, "layout regions");
        if (parsed != null) this.layoutRegions = parsed;
    }

    public void setAccessibilityIgnoreRegions(String accessibilityIgnoreRegions) {
        Region[] parsed = parseRegions(accessibilityIgnoreRegions, "accessibility ignore regions");
        if (parsed != null) this.accessibilityIgnoreRegions = parsed;
    }

    public void setAccessibilityRegularTextRegions(String accessibilityRegularTextRegions) {
        Region[] parsed = parseRegions(accessibilityRegularTextRegions, "accessibility regular text regions");
        if (parsed != null) this.accessibilityRegularTextRegions = parsed;
    }

    public void setAccessibilityLargeTextRegions(String accessibilityLargeTextRegions) {
        Region[] parsed = parseRegions(accessibilityLargeTextRegions, "accessibility large text regions");
        if (parsed != null) this.accessibilityLargeTextRegions = parsed;
    }

    public void setAccessibilityBoldTextRegions(String accessibilityBoldTextRegions) {
        Region[] parsed = parseRegions(accessibilityBoldTextRegions, "accessibility bold text regions");
        if (parsed != null) this.accessibilityBoldTextRegions = parsed;
    }

    public void setAccessibilityGraphicsRegions(String accessibilityGraphicsRegions) {
        Region[] parsed = parseRegions(accessibilityGraphicsRegions, "accessibility graphics regions");
        if (parsed != null) this.accessibilityGraphicsRegions = parsed;
    }

    public void setProperties(String propArgument) {

        if (propArgument == null || propArgument.isEmpty()) {
            return;
        }

        boolean isValidFormat = Arrays.stream(propArgument.split("\\|"))
            .allMatch(s -> s.matches("[^:]+:[^:]+"));

        if (!isValidFormat) {
            throw new IllegalArgumentException("Argument properties (-pr) does not follow the "
                + "'key:value' format for all segments separated by \"|\".");
        }

        properties = Arrays.stream(propArgument.split("\\|"))
            .map(prop -> prop.split(":", 2))
            .toArray(String[][]::new);
    }

    public void setMatchTimeout(String matchTimeout) {
        this.matchTimeout = matchTimeout;
    }

    public String getMatchTimeout() {
        return this.matchTimeout;
    }
}
