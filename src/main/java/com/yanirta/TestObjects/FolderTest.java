package com.yanirta.TestObjects;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.yanirta.lib.Config;
import com.yanirta.lib.Patterns;
import org.apache.commons.io.comparator.NameFileComparator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class FolderTest extends TestBase {
    private final File[] steps_;

    public FolderTest(File folder, Config conf) {
        super(folder, conf);
        if (!folder.isDirectory())
            throw new RuntimeException("FolderTest object can't process non-folder object");
        FilenameFilter imageFilesFilter = (dir, name) -> Patterns.IMAGE.matcher(name).matches();
        this.steps_ = folder.listFiles(imageFilesFilter);
        if (!conf.legacyFileOrder)
            Arrays.sort(Objects.requireNonNull(this.steps_), NameFileComparator.NAME_COMPARATOR);
    }

    public TestResults run(Eyes eyes) throws Exception {
        for (File img : steps_) {
            try {
                BufferedImage image = getImage(img);
                if (!eyes.getIsOpen()) eyes.open(appName(), name(), viewport(image));
                eyes.check(
                		img.getName(),
                        new ImagesCheckSettingsFactory(image, config(), viewport(image)).create()
                );
                image = null;
            } catch (IOException e) {
                logger().reportException(e, img.getAbsolutePath());
            }
        }
        return eyes.close(false);
    }

    public boolean isEmpty() {
        return steps_.length == 0;
    }
}
