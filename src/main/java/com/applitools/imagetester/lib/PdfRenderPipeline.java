package com.applitools.imagetester.lib;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Renders PDF pages on a background thread pool ahead of the consumer while
 * preserving page order, so page rendering overlaps with the Eyes check of the
 * previous pages instead of blocking between checks.
 *
 * PDFBox documents are not thread-safe, so each render thread loads its own
 * PDDocument from the source file. The look-ahead window is bounded to cap how
 * many rendered pages are held in memory at once.
 */
public class PdfRenderPipeline implements Closeable {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    public static class RenderedPage {
        public final int pageNumber;
        public final BufferedImage image;
        public final IOException error;

        private RenderedPage(int pageNumber, BufferedImage image, IOException error) {
            this.pageNumber = pageNumber;
            this.image = image;
            this.error = error;
        }
    }

    private static class ThreadContext {
        final PDDocument document;
        final PDFRenderer renderer;

        ThreadContext(PDDocument document) {
            this.document = document;
            this.renderer = new PDFRenderer(document);
        }
    }

    private final File file_;
    private final Config config_;
    private final ExecutorService renderPool_;
    private final int windowSize_;
    private final Iterator<Integer> remainingPages_;
    private final Deque<Future<RenderedPage>> inFlight_ = new ArrayDeque<>();
    private final Queue<PDDocument> openedDocuments_ = new ConcurrentLinkedQueue<>();
    private final ThreadLocal<ThreadContext> threadContext_ = new ThreadLocal<>();

    public PdfRenderPipeline(File file, Config config, List<Integer> pages, int threads) {
        this.file_ = file;
        this.config_ = config;
        this.renderPool_ = Executors.newFixedThreadPool(threads);
        this.windowSize_ = threads + 1;
        this.remainingPages_ = pages.iterator();
        fillWindow();
    }

    public boolean hasNext() {
        return !inFlight_.isEmpty();
    }

    public RenderedPage next() throws Exception {
        RenderedPage page = inFlight_.remove().get();
        fillWindow();
        return page;
    }

    private void fillWindow() {
        while (inFlight_.size() < windowSize_ && remainingPages_.hasNext()) {
            final int page = remainingPages_.next();
            inFlight_.add(renderPool_.submit(() -> renderPage(page)));
        }
    }

    private RenderedPage renderPage(int page) {
        try {
            ThreadContext context = contextForThread();
            BufferedImage image = PdfPageRenderer.render(
                    context.document.getPage(page - 1), page - 1, context.renderer, config_);
            return new RenderedPage(page, image, null);
        } catch (IOException e) {
            return new RenderedPage(page, null, e);
        }
    }

    private ThreadContext contextForThread() throws IOException {
        ThreadContext context = threadContext_.get();
        if (context == null) {
            PDDocument document = PDDocument.load(file_, config_.pdfPass);
            openedDocuments_.add(document);
            context = new ThreadContext(document);
            threadContext_.set(context);
        }
        return context;
    }

    @Override
    public void close() {
        for (Future<RenderedPage> future : inFlight_)
            future.cancel(true);
        inFlight_.clear();

        // Documents may only be closed after every render thread has stopped using them.
        renderPool_.shutdownNow();
        try {
            renderPool_.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (PDDocument document : openedDocuments_) {
            try {
                document.close();
            } catch (IOException e) {
                config_.logger.reportException(e);
            }
        }
        openedDocuments_.clear();
    }
}
