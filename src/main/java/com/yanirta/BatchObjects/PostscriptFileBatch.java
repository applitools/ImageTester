package com.yanirta.BatchObjects;

import com.yanirta.TestObjects.TestBase;
import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.yanirta.lib.Config;
import com.yanirta.lib.Utils;
import org.ghost4j.document.DocumentException;
import org.ghost4j.document.PSDocument;
import org.ghost4j.renderer.RendererException;
import org.ghost4j.renderer.SimpleRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PostscriptFileBatch extends BatchBase {
    private List<Integer> pageList_;
    private PSDocument document_ = new PSDocument();
    private SimpleRenderer renderer_ = new SimpleRenderer();

    private class PostscriptPageTest extends TestBase {

        private final int pageNumber_;
        private final Image image_;

        public PostscriptPageTest(File file, Config conf, int pageNumber, Image img, PostscriptFileBatch batch) {
            super(file, conf);
            this.pageNumber_ = pageNumber;
            this.image_ = img;
        }

        @Override
        public TestResults run(Eyes eyes) throws Exception {
            Graphics2D bGr = null;
            try {
                BufferedImage image = new BufferedImage(
                        image_.getWidth(null),
                        image_.getHeight(null),
                        BufferedImage.TYPE_INT_ARGB);
                if (!eyes.getIsOpen())
                    eyes.open(appName(), name(), viewport(image));
                // Draw the image on to the buffered image
                bGr = image.createGraphics();
                bGr.drawImage(image_, 0, 0, null);
                eyes.checkImage(image, String.format("Page-%s", pageNumber_));

                return eyes.close(false);
            } finally {
                if (bGr != null) bGr.dispose();
            }
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public String name() {
            return super.name() + " Page - " + this.pageNumber_;
        }
    }

    public PostscriptFileBatch(File file, Config conf) throws IOException, RendererException, DocumentException {
        super(new BatchInfo(file.getName()));
        this.renderer_.setResolution(Math.round(conf.DocumentConversionDPI));
        this.document_.load(file);
        this.pageList_ = Utils.parsePagesNotation(conf.pages);
        List<Image> images = this.renderer_.render(this.document_);
        if (this.pageList_ == null || this.pageList_.isEmpty())
            this.pageList_ = Utils.generateRanage(images.size() + 1, 1);

        List<TestBase> pages = new ArrayList<>(pageList_.size());
        int currPage = 1;
        for (Image img : images) {
            if (this.pageList_.contains(currPage))
                pages.add(new PostscriptPageTest(file, conf, currPage, img, this));
            ++currPage;
        }

        addTests(pages);
    }
}
