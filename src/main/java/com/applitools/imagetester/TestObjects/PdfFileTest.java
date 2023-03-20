package com.applitools.imagetester.TestObjects;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
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

    public TestResults run(Eyes eyes) throws Exception {
        // Needed for PDFBox to display JBig images within PDF renders
        IIORegistry.getDefaultInstance().registerServiceProvider(new JBIG2ImageReaderSpi());

        try (PDDocument document = PDDocument.load(file(), config().pdfPass)) {
            if (pageList_ == null || pageList_.isEmpty())
                pageList_ = Utils.generateRanage(document.getNumberOfPages() + 1, 1);
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (Integer page : pageList_) {
                try {
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(page - 1, config().DocumentConversionDPI);
                    logger().logPage(bim, name(), page);
                    if (!eyes.getIsOpen())
                        eyes.open(appName(), name(), viewport(bim));
                    eyes.check(
                            String.format("Page-%s", page),
                            new ImagesCheckSettingsFactory(bim, config(), viewport(bim)).create()
                    );
                    bim.getGraphics().dispose();
                    bim.flush();
                } catch (IOException e) {
                    logger().reportException(e, file().getAbsolutePath());
                }
            }
            return eyes.close(false);
        }
    }
}
