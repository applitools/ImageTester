package com.applitools.imagetester.TestObjects;

import com.applitools.ICheckSettings;
import com.applitools.eyes.AccessibilityRegionType;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.Region;
import com.applitools.eyes.images.ImagesCheckSettings;
import com.applitools.imagetester.lib.Config;

import java.awt.image.BufferedImage;

public class ImagesCheckSettingsFactory {

    private Region viewPortRegion;
    private ICheckSettings imagesCheckSettings;
    private final Config config;

    public ImagesCheckSettingsFactory(BufferedImage image, Config config) {
        this.imagesCheckSettings = new ImagesCheckSettings(image);
        this.config = config;
    }
    public ImagesCheckSettingsFactory(BufferedImage image, Config config, RectangleSize viewport) {
        this.imagesCheckSettings = config.captureRegion == null ?
                new ImagesCheckSettings(image) :
                new ImagesCheckSettings(image, config.captureRegion);
        this.config = config;
        this.viewPortRegion = new Region(0, 0, viewport.getWidth(), viewport.getHeight());
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

        if (config.accessibilityRegularTextFullPage) {
            this.imagesCheckSettings = this.imagesCheckSettings.accessibility(viewPortRegion, AccessibilityRegionType.RegularText);
        }
        if (config.accessibilityLargeTextFullPage) {
            this.imagesCheckSettings = this.imagesCheckSettings.accessibility(viewPortRegion, AccessibilityRegionType.LargeText);
        }
        if (config.accessibilityBoldTextFullPage) {
            this.imagesCheckSettings = this.imagesCheckSettings.accessibility(viewPortRegion, AccessibilityRegionType.BoldText);
        }
        if (config.accessibilityGraphicsFullPage) {
            this.imagesCheckSettings = this.imagesCheckSettings.accessibility(viewPortRegion, AccessibilityRegionType.GraphicalObject);
        }

        if (config.accessibilityIgnoreRegions != null) {
            // We have to loop because Eyes Images doesn't support Regions array
            for (Region acRegion : config.accessibilityIgnoreRegions ) {
                this.imagesCheckSettings = this.imagesCheckSettings.accessibility(acRegion, AccessibilityRegionType.IgnoreContrast);
            }
        }
        if (config.accessibilityRegularTextRegions != null) {
            for (Region acRegion: config.accessibilityRegularTextRegions) {
                this.imagesCheckSettings = this.imagesCheckSettings.accessibility(acRegion, AccessibilityRegionType.RegularText);
            }
        }
        if (config.accessibilityLargeTextRegions != null) {
            for (Region acRegion : config.accessibilityLargeTextRegions) {
                this.imagesCheckSettings = this.imagesCheckSettings.accessibility(acRegion, AccessibilityRegionType.LargeText);
            }
        }
        if (config.accessibilityBoldTextRegions != null) {
            for (Region acRegion : config.accessibilityBoldTextRegions) {
                this.imagesCheckSettings = this.imagesCheckSettings.accessibility(acRegion, AccessibilityRegionType.BoldText);
            }
        }
        if (config.accessibilityGraphicsRegions != null) {
            for (Region acRegion : config.accessibilityGraphicsRegions) {
                this.imagesCheckSettings = this.imagesCheckSettings.accessibility(acRegion, AccessibilityRegionType.GraphicalObject);
            }
        }

        return imagesCheckSettings;
    }
 }
