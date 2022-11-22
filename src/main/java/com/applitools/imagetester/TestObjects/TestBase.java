package com.applitools.imagetester.TestObjects;

import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;
import com.applitools.imagetester.lib.Utils;

import org.apache.commons.lang3.StringUtils;
import org.imgscalr.Scalr;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public abstract class TestBase implements ITest {
    private static final String FILE_NAME_PROP = "Filename";
    private final File file_;
    private final Config conf_;

    public TestBase(File file, Config conf) {
        this.file_ = file;
        this.conf_ = conf;
    }

    public File file() {
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
            return file_.getName();
    }

    public TestResults runSafe(Eyes eyes) {
        try {
            eyes.addProperty(FILE_NAME_PROP, file_.getName());
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
        BufferedImage bim = ImageIO.read(img);
        if (StringUtils.isNotBlank(conf_.matchWidth) || StringUtils.isNotBlank(conf_.matchHeight)) {
            //Resize the image
            Dimension dim = getNewDimensions_(bim.getWidth(), bim.getHeight());
            BufferedImage scaledImg = Scalr.resize(bim, Scalr.Method.ULTRA_QUALITY, dim.width, dim.height);
            bim = null; //perhaps a better disposal required
            bim = scaledImg;
        }
        return bim;
    }

    private Dimension getNewDimensions_(int oldWidth, int oldHeight) {
        if (StringUtils.isNotBlank(conf_.matchWidth) && StringUtils.isNotBlank(conf_.matchHeight))
            return new Dimension(Integer.parseInt(conf_.matchWidth), Integer.parseInt(conf_.matchHeight));
        else if (StringUtils.isNotBlank(conf_.matchWidth)) {
            // scale by width
            float ratio = Float.parseFloat(conf_.matchWidth) / oldWidth;
            return new Dimension(Integer.parseInt(conf_.matchWidth), Math.round(oldHeight * ratio));
        } else if (StringUtils.isNotBlank(conf_.matchHeight)) {
            // scale by height
            float ratio = Float.parseFloat(conf_.matchHeight) / oldHeight;
            return new Dimension(Math.round(oldWidth * ratio), Integer.parseInt(conf_.matchHeight));
        } else throw new RuntimeException("The new dimensions were not provided correctly");
    }
}
