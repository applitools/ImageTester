package com.yanirta.BatchObjects;

import com.applitools.eyes.images.ImagesCheckSettings;
import com.yanirta.TestObjects.IDisposable;
import com.yanirta.TestObjects.ImagesCheckSettingsFactory;
import com.yanirta.TestObjects.TestBase;
import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.yanirta.lib.Config;
import com.yanirta.lib.Logger;
import com.yanirta.lib.Utils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFFileBatch extends BatchBase {
    private final PDDocument document_;
    private final PDFRenderer pdfRenderer_;
    private final Logger logger_;
    private int disposeCountdown_;

    private class PDFPageTest extends TestBase implements IDisposable {
        private final PDFFileBatch parent_;
        private final int page_;

        public PDFPageTest(File file, Config conf, int page, PDFFileBatch parentBatch) {
            super(file, conf);
            this.page_ = page;
            this.parent_ = parentBatch;
        }

        @Override
        public TestResults run(Eyes eyes) throws Exception {
            logger_.reportDebug("Rendering page %s ,num of pages %s\n", page_, parent_.document_.getNumberOfPages());
            BufferedImage bim = safeRender();
            if (!eyes.getIsOpen())
                eyes.open(appName(), name(), viewport(bim));
            eyes.check(name(), new ImagesCheckSettingsFactory(bim, config(), viewport(bim)).create());
            return eyes.close(false);
        }

        private BufferedImage safeRender() throws IOException {
            synchronized (parent_) {
                return parent_.pdfRenderer_.renderImageWithDPI(page_ - 1, config().DocumentConversionDPI);
            }
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public String name() {
            return super.name() + " Page - " + this.page_;
        }

        @Override
        public void dispose() {
            parent_.dispose();
        }
    }

    public PDFFileBatch(File file, Config conf) throws IOException {
        super(new BatchInfo(file.getName()));
        List<Integer> pageList_ = Utils.parsePagesNotation(conf.pages);

        this.logger_ = conf.logger;
        this.document_ = PDDocument.load(file, conf.pdfPass);

        int totalPages = document_.getNumberOfPages();
        if (pageList_ == null || pageList_.isEmpty())
            pageList_ = Utils.generateRanage(totalPages + 1, 1);

        this.disposeCountdown_ = pageList_.size();

        List<TestBase> pages = new ArrayList<>(pageList_.size());
        for (int page : pageList_)
            pages.add(new PDFPageTest(file, conf, page, this));

        addTests(pages);

        this.pdfRenderer_ = new PDFRenderer(document_);
    }

    public synchronized void dispose() {
        --disposeCountdown_;
        if (disposeCountdown_ == 0) {
            logger_.reportDebug("Disposing %s \n", batchInfo().getName());
            try {
                document_.close();
            } catch (IOException e) {
                logger_.reportException(e);
            }
        }
    }
}
