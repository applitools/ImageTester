package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;

import org.junit.Test;

public class MatchSizeResizerTest {

    private static final int SOURCE_WIDTH = 200;
    private static final int SOURCE_HEIGHT = 100;

    @Test
    public void resize_toExactSizeWhenBothDimensionsGiven() {
        BufferedImage result = MatchSizeResizer.resize(sourceImage(), configWithMatchSize("50x80"));
        assertEquals("Width mismatch", 50, result.getWidth());
        assertEquals("Height mismatch", 80, result.getHeight());
    }

    @Test
    public void resize_scalesByWidthPreservingRatioWhenOnlyWidthGiven() {
        BufferedImage result = MatchSizeResizer.resize(sourceImage(), configWithMatchSize("100x"));
        assertEquals("Width mismatch", 100, result.getWidth());
        assertEquals("Height mismatch", 50, result.getHeight());
    }

    @Test
    public void resize_scalesByHeightPreservingRatioWhenOnlyHeightGiven() {
        BufferedImage result = MatchSizeResizer.resize(sourceImage(), configWithMatchSize("x50"));
        assertEquals("Width mismatch", 100, result.getWidth());
        assertEquals("Height mismatch", 50, result.getHeight());
    }

    @Test
    public void resize_returnsSameImageWhenMatchSizeNotConfigured() {
        BufferedImage source = sourceImage();
        assertSame(source, MatchSizeResizer.resize(source, new Config()));
    }

    private BufferedImage sourceImage() {
        return new BufferedImage(SOURCE_WIDTH, SOURCE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    }

    private Config configWithMatchSize(String size) {
        Config config = new Config();
        config.setMatchSize(size);
        return config;
    }
}
