package com.applitools.imagetester.TestObjects;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageFileTest extends TestBase {

    public ImageFileTest(File file, Config config) {
        super(file, config);
    }

    @Override
    public TestResults run(Eyes eyes) throws Exception {
        File actualFile = prepareImageFile(file());

        BufferedImage image = getImage(actualFile);
        eyes.open(appName(), name(), viewport(image));
        eyes.check(
            actualFile.getName(),
            new ImagesCheckSettingsFactory(image, config(), viewport(image)).create()
        );
        return eyes.close(true);
    }

    private File prepareImageFile(File inputFile) throws IOException {
        return isTiff(inputFile) ? convertTiffToPng(inputFile) : inputFile;
    }

    private boolean isTiff(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".tif") || name.endsWith(".tiff");
    }

    private File convertTiffToPng(File tiffFile) throws IOException {
        BufferedImage image = readImageAsRgb(tiffFile);
        File output = new File(tiffFile.getParent(), replaceExtension(tiffFile.getName(), "png"));
        ImageIO.write(image, "png", output);
        return output;
    }

    private BufferedImage readImageAsRgb(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Unsupported image format or corrupt file: " + file.getAbsolutePath());
        }

        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgbImage = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = rgbImage.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            return rgbImage;
        }

        return image;
    }

    private String replaceExtension(String filename, String newExt) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1)
            ? filename + "." + newExt
            : filename.substring(0, dotIndex) + "." + newExt;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}