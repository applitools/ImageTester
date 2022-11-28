package com.applitools.imagetester.TestObjects;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;

import java.awt.image.BufferedImage;
import java.io.File;

public class ImageFileTest extends TestBase {
    public ImageFileTest(File file, Config conf) {
        super(file, conf);
    }

    @Override
    public TestResults run(Eyes eyes) throws Exception {
        BufferedImage image = getImage(file());
        eyes.open(appName(), name(), viewport(image));
        eyes.check(
                file().getName(),
                new ImagesCheckSettingsFactory(image, config(), viewport(image)).create()
        );
        image = null;
        return eyes.close(false);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
