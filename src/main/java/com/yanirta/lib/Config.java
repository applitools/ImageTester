package com.yanirta.lib;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.ProxySettings;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.fluent.BatchClose;
import com.applitools.eyes.fluent.EnabledBatchClose;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;

public class Config {
    public RectangleSize viewport;
    public String appName = "ImageTester";
    public float DocumentConversionDPI = 250;
    public boolean splitSteps = false;
    public String pages = null;
    public String pdfPass = null;
    public boolean includePageNumbers = false;
    public Logger logger = new Logger();
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
    private HashSet<String> batchesIdListForBatchClose = new HashSet<>();

    public void setViewport(String viewport) {
        if (viewport == null) return;
        String[] dims = viewport.split("x");
        if (dims.length != 2)
            throw new RuntimeException("invalid viewport-size, make sure the call is -vs <width>x<height>");
        this.viewport = new RectangleSize(
                Integer.parseInt(dims[0]),
                Integer.parseInt(dims[1]));
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
        return;
    }

    //set batch related info
    public void setBatchInfo(String flatBatchArg, boolean notifyOnComplete) {
        this.notifyOnComplete = notifyOnComplete;
        //set batch- take flat batch if described- get environment variables values unless overwritten
        String batchNameToAdd = System.getenv("JOB_NAME");
        String batchIdToAdd = System.getenv("APPLITOOLS_BATCH_ID");

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
}
