package com.yanirta.TestObjects;

import com.applitools.ICheckSettings;
import com.applitools.eyes.images.ImagesCheckSettings;
import com.yanirta.lib.Config;

import java.awt.image.BufferedImage;

public class ImagesCheckSettingsFactory {

    private ICheckSettings imagesCheckSettings;
    private final Config config;

    public ImagesCheckSettingsFactory(BufferedImage image, Config config) {
        this.imagesCheckSettings = new ImagesCheckSettings(image);
        this.config = config;
    }

    public ICheckSettings create() {
        if (config.layoutRegions != null) {
            this.imagesCheckSettings = this.imagesCheckSettings.layout(config.layoutRegions);
        }
        if (config.ignoreRegions != null) {
            this.imagesCheckSettings = this.imagesCheckSettings.ignore(config.ignoreRegions);
        }
        if (config.contentRegions != null) {
            this.imagesCheckSettings = this.imagesCheckSettings.content(config.contentRegions);
        }
        return imagesCheckSettings;
    }
 }
