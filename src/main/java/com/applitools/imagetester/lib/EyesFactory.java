package com.applitools.imagetester.lib;

import com.applitools.eyes.*;
import com.applitools.eyes.images.Eyes;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public class EyesFactory {
    private final String version;
    private final Logger logger;
    private String apiKey;
    private String serverUrl;
    private String matchLevel;
    private ProxySettings proxySettings;
    private String branch;
    private String parentBranch;
    private String envName;
    private String logFilename;
    private String hostOs;
    private String hostApp;
    private String environmentName;
    private boolean saveFailed = false;
    private boolean ignoreDisplacement = false;
    private boolean saveNewTests = false;
    private int[] cutValues;
    private AccessibilitySettings accSettings = null;
    private boolean logHandler;

    public EyesFactory(String ver, Logger logger) {
        this.version = ver;
        this.logger = logger;
    }

    public synchronized Eyes build() throws RuntimeException {
        Eyes eyes = new Eyes() {
            @Override
            public String getBaseAgentId() {
                return String.format("ImageTester/%s [%s]", version, super.getBaseAgentId());
            }
        };
        eyes.setApiKey(this.apiKey);

        //flags
        eyes.setSaveFailedTests(saveFailed);
        eyes.setIgnoreDisplacements(ignoreDisplacement);
        eyes.setSaveNewTests(saveNewTests);
        //String params
        if (StringUtils.isNotBlank(this.serverUrl))
            eyes.setServerUrl(this.serverUrl);
        if (StringUtils.isNotBlank(this.matchLevel))
            eyes.setMatchLevel(Utils.parseEnum(MatchLevel.class, this.matchLevel));
        if (StringUtils.isNotBlank(this.branch))
            eyes.setBranchName(this.branch);
        if (StringUtils.isNotBlank(this.parentBranch))
            eyes.setParentBranchName(this.parentBranch);
        if (StringUtils.isNotBlank(this.envName))
            eyes.setBaselineEnvName(this.envName);
        if (StringUtils.isNotBlank(this.hostOs))
            eyes.setHostOS(this.hostOs);
        if (StringUtils.isNotBlank(this.hostApp))
            eyes.setHostApp(this.hostApp);
        if (StringUtils.isNotBlank(this.environmentName))
            eyes.setEnvName(this.environmentName);
        if (StringUtils.isNotBlank(this.logFilename))
            eyes.setLogHandler(new FileLogger(this.logFilename, true, true));
        if (this.proxySettings != null)
            eyes.setProxy(proxySettings);
        if (this.cutValues != null) {
            int header = cutValues[0];
            int footer = cutValues.length > 1 ? cutValues[1] : 0;
            int left = cutValues.length > 2 ? cutValues[2] : 0;
            int right = cutValues.length == 4 ? cutValues[3] : 0;
            eyes.setImageCut(new UnscaledFixedCutProvider(header, footer, left, right));
        }

        if (StringUtils.isNotBlank(this.parentBranch) && StringUtils.isBlank(this.branch))
            throw new RuntimeException("Parent Branches (pb) should be combined with branches (br).");
        if (this.accSettings != null)
            eyes.setAccessibilityValidation(this.accSettings);
        if (logHandler)
            eyes.setLogHandler(new StdoutLogHandler(true));

        return eyes;
    }

    public EyesFactory apiKey(String key) {
        this.apiKey = key;
        return this;
    }

    public EyesFactory serverUrl(String uri) {
        this.serverUrl = uri;
        return this;
    }

    public EyesFactory matchLevel(String ml) {
        this.matchLevel = ml;
        return this;
    }

    public EyesFactory proxySettings(ProxySettings proxySettings) {
        this.proxySettings = proxySettings;
        return this;
    }

    public EyesFactory branch(String branch) {
        this.branch = branch;
        return this;
    }

    public EyesFactory parentBranch(String parentBranch) {
        this.parentBranch = parentBranch;
        return this;
    }

    public EyesFactory baselineEnvName(String envName) {
        this.envName = envName;
        return this;
    }

    public EyesFactory logFile(String filename) {
        this.logFilename = filename;
        return this;
    }

    public EyesFactory hostOs(String os) {
        this.hostOs = os;
        return this;
    }

    public EyesFactory hostApp(String app) {
        this.hostApp = app;
        return this;
    }

    public EyesFactory environmentName(String environmentName) {
        this.environmentName = environmentName;
        return this;
    }

    public EyesFactory saveFaliedTests(boolean saveFailed) {
        this.saveFailed = saveFailed;
        return this;
    }

    public EyesFactory ignoreDisplacement(boolean ignore) {
        this.ignoreDisplacement = ignore;
        return this;
    }

    public EyesFactory saveNewTests(boolean save) {
        this.saveNewTests = save;
        return this;
    }

    public EyesFactory imageCut(String[] cutArgs) {
        if (cutArgs == null)
            return this;
        if (cutArgs.length > 4)
            throw new IllegalArgumentException("Image cut must contain at most four parameters (header,footer,left,right)");
        this.cutValues = Arrays
                .stream(cutArgs)
                .mapToInt((s) -> StringUtils.isNumeric(s) ? Integer.parseInt(s) : 0)
                .toArray();
        return this;
    }

    public EyesFactory accSettings(String[] settings) {
        if (settings == null)
            return this;
        AccessibilityLevel level = AccessibilityLevel.AA;
        AccessibilityGuidelinesVersion version = AccessibilityGuidelinesVersion.WCAG_2_0;
        if (settings.length > 2)
            throw new IllegalArgumentException("incorrect Accessibility setting");
        if (settings.length > 0) {
            level = StringUtils.isNotBlank(settings[0]) ? Utils.parseEnum(AccessibilityLevel.class, settings[0], "_") : level;
            version = settings.length == 2 && StringUtils.isNotBlank(settings[1]) ? Utils.parseEnum(AccessibilityGuidelinesVersion.class, settings[1], "_") : version;
        }
        this.accSettings = new AccessibilitySettings(level, version);
        return this;
    }

    public EyesFactory logHandler(boolean logHandler) {
        this.logHandler = logHandler;
        return this;
	}
}
