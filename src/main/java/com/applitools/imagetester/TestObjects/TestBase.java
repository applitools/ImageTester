package com.applitools.imagetester.TestObjects;

import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.MatchSizeResizer;
import com.applitools.imagetester.lib.Utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public abstract class TestBase implements ITest {
    private static final String FILE_NAME_PROP = "Filename";
    private final File file_;
    private final File source_;
    private final Config conf_;

    public TestBase(File file, Config conf) {
        this(file, conf, file);
    }

    public TestBase(File file, Config conf, File source) {
        this.file_ = file;
        this.source_ = source != null ? source : file;
        this.conf_ = conf;
    }

    public File file() {
        return file_;
    }

    /** The file to render as a GUI status-row thumbnail. Overridden where file() isn't a displayable image/PDF. */
    public File previewFile() {
        return file_;
    }

    public Config config() {
        return conf_;
    }

    public String appName() {
        return this.conf_.appName;
    }

    @Override
    public RectangleSize viewport(BufferedImage image) {
        if (this.conf_.viewport == null && image != null)
            return new RectangleSize(image.getWidth(), image.getHeight());
        return this.conf_.viewport;
    }

    @Override
    public RectangleSize viewport() {
        return viewport(null);
    }

    public String name() {
        if (conf_.forcedName != null)
            return conf_.forcedName;
        else
            return source_.getName();
    }

    public TestResults runSafe(Eyes eyes) {
        try {
            if (conf_.properties != null && conf_.properties.length > 0) {
                for(int i = 0; i < conf_.properties.length; i++) {
                    eyes.addProperty(conf_.properties[i][0], conf_.properties[i][1]);
                }
            }
            eyes.addProperty(FILE_NAME_PROP, source_.getName());
            TestResults res = run(eyes);
            Utils.handleResultsDownload(conf_.eyesUtilsConf, res);
            return res;
        } catch (Exception e) {
            logger().reportException(e);
        } finally {
            eyes.abortIfNotClosed();
            eyes.clearProperties();
        }

        return null;
    }

    public Logger logger() {
        return conf_.logger;
    }

    protected BufferedImage getImage(File img) throws IOException {
        return MatchSizeResizer.resize(ImageIO.read(img), conf_);
    }
}
