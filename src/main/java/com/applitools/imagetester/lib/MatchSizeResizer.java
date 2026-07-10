package com.applitools.imagetester.lib;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

import org.apache.commons.lang3.StringUtils;
import org.imgscalr.Scalr;

/**
 * Applies the -ms (match size) option to an image: exact WxH when both dimensions
 * are given (may distort), otherwise proportional scaling by the given dimension.
 * Shared by the image-file and PDF-page test paths so both honor -ms identically.
 */
public final class MatchSizeResizer {

    private MatchSizeResizer() {
    }

    public static BufferedImage resize(BufferedImage image, Config config) {
        boolean hasWidth = StringUtils.isNotBlank(config.matchWidth);
        boolean hasHeight = StringUtils.isNotBlank(config.matchHeight);
        if (!hasWidth && !hasHeight)
            return image;

        Dimension target = targetDimensions(image.getWidth(), image.getHeight(), config, hasWidth, hasHeight);
        if (hasWidth && hasHeight)
            return Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_EXACT, target.width, target.height);
        return Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, target.width, target.height);
    }

    private static Dimension targetDimensions(int oldWidth, int oldHeight, Config config,
                                              boolean hasWidth, boolean hasHeight) {
        if (hasWidth && hasHeight)
            return new Dimension(Integer.parseInt(config.matchWidth), Integer.parseInt(config.matchHeight));
        if (hasWidth) {
            float ratio = Float.parseFloat(config.matchWidth) / oldWidth;
            return new Dimension(Integer.parseInt(config.matchWidth), Math.round(oldHeight * ratio));
        }
        float ratio = Float.parseFloat(config.matchHeight) / oldHeight;
        return new Dimension(Math.round(oldWidth * ratio), Integer.parseInt(config.matchHeight));
    }
}
