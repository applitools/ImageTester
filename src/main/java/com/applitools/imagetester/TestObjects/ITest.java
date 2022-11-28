package com.applitools.imagetester.TestObjects;

import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;

import java.awt.image.BufferedImage;

public interface ITest {
    TestResults run(Eyes eyes) throws Exception;

    String appName();

    String name();

    RectangleSize viewport(BufferedImage image);

    RectangleSize viewport();

    boolean isEmpty();
}
