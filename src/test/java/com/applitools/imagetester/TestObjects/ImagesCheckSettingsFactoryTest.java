package com.applitools.imagetester.TestObjects;

import com.applitools.ICheckSettings;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.Region;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;

import org.junit.Before;
import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

public class ImagesCheckSettingsFactoryTest {

    private Config config;
    private BufferedImage image;
    private RectangleSize viewport;

    @Before
    public void setUp() {
        config = new Config();
        config.logger = new Logger();
        image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        viewport = new RectangleSize(100, 100);
    }

    @Test
    public void create_noRegions_returnsCheckSettings() {
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        ICheckSettings settings = factory.create();
        assertNotNull(settings);
    }

    @Test
    public void create_withIgnoreRegions() {
        config.ignoreRegions = new Region[]{new Region(10, 10, 50, 50)};
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_withContentRegions() {
        config.contentRegions = new Region[]{new Region(10, 10, 50, 50)};
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_withLayoutRegions() {
        config.layoutRegions = new Region[]{new Region(10, 10, 50, 50)};
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_withCaptureRegion() {
        config.captureRegion = new Region(0, 0, 50, 50);
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_withAccessibilityRegularTextFullPage() {
        config.accessibilityRegularTextFullPage = true;
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_withAccessibilityLargeTextFullPage() {
        config.accessibilityLargeTextFullPage = true;
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_withAccessibilityBoldTextFullPage() {
        config.accessibilityBoldTextFullPage = true;
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_withAccessibilityGraphicsFullPage() {
        config.accessibilityGraphicsFullPage = true;
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_withAccessibilitySpecificRegions() {
        config.accessibilityIgnoreRegions = new Region[]{new Region(0, 0, 100, 100)};
        config.accessibilityRegularTextRegions = new Region[]{new Region(0, 0, 50, 50)};
        config.accessibilityLargeTextRegions = new Region[]{new Region(0, 0, 50, 50)};
        config.accessibilityBoldTextRegions = new Region[]{new Region(0, 0, 50, 50)};
        config.accessibilityGraphicsRegions = new Region[]{new Region(0, 0, 50, 50)};

        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_withAllRegionTypes() {
        config.ignoreRegions = new Region[]{new Region(10, 10, 50, 50)};
        config.contentRegions = new Region[]{new Region(20, 20, 30, 30)};
        config.layoutRegions = new Region[]{new Region(30, 30, 20, 20)};
        config.accessibilityRegularTextFullPage = true;

        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config, viewport);
        assertNotNull(factory.create());
    }

    @Test
    public void create_twoArgConstructor_works() {
        ImagesCheckSettingsFactory factory = new ImagesCheckSettingsFactory(image, config);
        assertNotNull(factory.create());
    }
}
