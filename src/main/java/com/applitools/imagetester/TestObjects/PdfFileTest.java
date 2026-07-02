package com.applitools.imagetester.TestObjects;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.PdfPageRenderer;
import com.applitools.imagetester.lib.PdfRenderPipeline;
import com.applitools.imagetester.lib.Utils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.spi.IIORegistry;
import org.apache.pdfbox.jbig2.JBIG2ImageReaderSpi;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PdfFileTest extends DocumentTestBase {

    public PdfFileTest(File file, Config conf) {
        super(file, conf);
    }

    public PdfFileTest(File file, Config conf, File source) {
        super(file, conf, source);
    }

    public TestResults run(Eyes eyes) throws Exception {
        // Needed for PDFBox to display JBig images within PDF renders
        IIORegistry.getDefaultInstance().registerServiceProvider(new JBIG2ImageReaderSpi());

        if (config().renderThreads > 1)
            return runPipelined(eyes, config().renderThreads);
        return runSerial(eyes);
    }

    private TestResults runSerial(Eyes eyes) throws Exception {
        try (PDDocument document = PDDocument.load(file(), config().pdfPass)) {
            if (pageList_ == null || pageList_.isEmpty())
                pageList_ = Utils.generateRange(document.getNumberOfPages() + 1, 1);
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (Integer page : pageList_) {
                try {
                    BufferedImage bim = PdfPageRenderer.render(
                            document.getPage(page - 1), page - 1, pdfRenderer, config());
                    checkPage(eyes, page, bim);
                } catch (IOException e) {
                    logger().reportException(e, file().getAbsolutePath());
                }
            }
            return eyes.close(false);
        }
    }

    private TestResults runPipelined(Eyes eyes, int renderThreads) throws Exception {
        if (pageList_ == null || pageList_.isEmpty()) {
            try (PDDocument document = PDDocument.load(file(), config().pdfPass)) {
                pageList_ = Utils.generateRange(document.getNumberOfPages() + 1, 1);
            }
        }
        if (pageList_.size() <= 1)
            return runSerial(eyes);

        try (PdfRenderPipeline pipeline = new PdfRenderPipeline(file(), config(), pageList_, renderThreads)) {
            while (pipeline.hasNext()) {
                PdfRenderPipeline.RenderedPage page = pipeline.next();
                if (page.error != null) {
                    logger().reportException(page.error, file().getAbsolutePath());
                    continue;
                }
                checkPage(eyes, page.pageNumber, page.image);
            }
            return eyes.close(false);
        }
    }

    private void checkPage(Eyes eyes, int page, BufferedImage bim) {
        logger().logPage(bim, name(), page);
        if (!eyes.getIsOpen())
            eyes.open(appName(), name(), viewport(bim));
        eyes.check(
                String.format("Page-%s", page),
                new ImagesCheckSettingsFactory(bim, config(), viewport(bim)).create()
        );
        bim.getGraphics().dispose();
        bim.flush();
    }
}
