package com.yanirta.TestObjects;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.yanirta.lib.Config;
import org.ghost4j.document.PSDocument;
import org.ghost4j.renderer.SimpleRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class PostscriptTest extends DocumentTestBase {
    public PostscriptTest(File file, Config conf) {
        super(file, conf);
    }

    @Override
    public TestResults run(Eyes eyes) throws Exception {
        PSDocument document = new PSDocument();
        SimpleRenderer renderer = new SimpleRenderer();
        renderer.setResolution(Math.round(config().DocumentConversionDPI));

        document.load(file());
        List<Image> images = renderer.render(document);
        int page = 0;
        for (Image step : images) {
            ++page;
            if (this.pageList_ != null && !this.pageList_.isEmpty() && !this.pageList_.contains(page))
                continue;
            BufferedImage image = new BufferedImage(
                    step.getWidth(null),
                    step.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB);
            if (!eyes.getIsOpen())
                eyes.open(appName(), name(), viewport(image));
            // Draw the image on to the buffered image
            Graphics2D bGr = image.createGraphics();
            bGr.drawImage(step, 0, 0, null);
            eyes.checkImage(image, String.format("Page-%s", page));
            bGr.dispose();
        }
        return eyes.close(false);
    }
}
