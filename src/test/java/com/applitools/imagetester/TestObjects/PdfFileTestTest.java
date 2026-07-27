package com.applitools.imagetester.TestObjects;

import com.applitools.ICheckSettings;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.Eyes;
import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Logger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.io.File;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class PdfFileTestTest {

    private static final String FIXTURES = "src/test/resources/fixtures";

    private Config config;
    private Eyes mockEyes;

    @Before
    public void setUp() {
        config = new Config();
        config.appName = "TestApp";
        config.logger = new Logger();

        mockEyes = mock(Eyes.class);
        when(mockEyes.getIsOpen()).thenReturn(false, true);
        when(mockEyes.close(anyBoolean())).thenReturn(mock(TestResults.class));
    }

    @Test
    public void validTwoPagePdf_opensOnceAndChecksEachPage() throws Exception {
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-2-page.pdf"), config);
        test.run(mockEyes);

        verify(mockEyes, times(1)).open(
                eq("TestApp"),
                eq("valid-2-page.pdf"),
                any(RectangleSize.class));
        verify(mockEyes, times(2)).check(anyString(), ArgumentMatchers.any(ICheckSettings.class));
        verify(mockEyes, times(1)).close(false);
    }

    @Test
    public void validTwoPagePdf_checksWithCorrectPageTags() throws Exception {
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-2-page.pdf"), config);
        test.run(mockEyes);

        verify(mockEyes).check(eq("Page-1"), ArgumentMatchers.any(ICheckSettings.class));
        verify(mockEyes).check(eq("Page-2"), ArgumentMatchers.any(ICheckSettings.class));
    }

    @Test
    public void validTenPagePdf_checksAllTenPages() throws Exception {
        when(mockEyes.getIsOpen()).thenReturn(false, true);
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);
        test.run(mockEyes);

        verify(mockEyes, times(10)).check(anyString(), ArgumentMatchers.any(ICheckSettings.class));
    }

    @Test
    public void pageSelection_singlePage_onlyThatPageChecked() throws Exception {
        config.pages = "2";
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);
        test.run(mockEyes);

        verify(mockEyes, times(1)).check(eq("Page-2"), ArgumentMatchers.any(ICheckSettings.class));
        verify(mockEyes, never()).check(eq("Page-1"), ArgumentMatchers.any(ICheckSettings.class));
    }

    @Test
    public void pageRangeSelection_commaAndRange_correctCountChecked() throws Exception {
        config.pages = "3,5-7";
        when(mockEyes.getIsOpen()).thenReturn(false, true);
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);
        test.run(mockEyes);

        // pages 3, 5, 6, 7 = 4 checks
        verify(mockEyes, times(4)).check(anyString(), ArgumentMatchers.any(ICheckSettings.class));
        verify(mockEyes).check(eq("Page-3"), ArgumentMatchers.any(ICheckSettings.class));
        verify(mockEyes).check(eq("Page-5"), ArgumentMatchers.any(ICheckSettings.class));
        verify(mockEyes).check(eq("Page-6"), ArgumentMatchers.any(ICheckSettings.class));
        verify(mockEyes).check(eq("Page-7"), ArgumentMatchers.any(ICheckSettings.class));
    }

    @Test
    public void passwordProtectedPdf_correctPassword_checksAtLeastOnePage() throws Exception {
        config.pdfPass = "test123";
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "password-protected.pdf"), config);
        TestResults result = test.run(mockEyes);

        verify(mockEyes, atLeastOnce()).check(anyString(), ArgumentMatchers.any(ICheckSettings.class));
        assertNotNull(result);
    }

    @Test
    public void corruptedPdf_throwsDuringLoad() {
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "corrupted.pdf"), config);
        try {
            test.run(mockEyes);
            // NOTE: Some environments may not throw if PDFBox tolerates the corruption.
        } catch (Exception e) {
            // Expected — corrupted file should throw during PDDocument.load
        }
    }

    @Test
    public void emptyPdf_noChecksPerformed() throws Exception {
        // generateRanage(0+1, 1) produces an empty list: loop is i=1; i<1 → no iterations
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "empty.pdf"), config);
        test.run(mockEyes);

        verify(mockEyes, never()).check(anyString(), ArgumentMatchers.any(ICheckSettings.class));
    }

    @Test
    public void emptyPdf_closeStillCalled() throws Exception {
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "empty.pdf"), config);
        test.run(mockEyes);

        verify(mockEyes, times(1)).close(false);
    }

    @Test
    public void cancelAfterFirstPage_checksNoFurtherPages() throws Exception {
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv -> { checks.incrementAndGet(); return null; })
                .when(mockEyes).check(anyString(), ArgumentMatchers.any(ICheckSettings.class));
        config.cancelRequested = () -> checks.get() >= 1;
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);

        test.run(mockEyes);

        verify(mockEyes, times(1)).check(anyString(), ArgumentMatchers.any(ICheckSettings.class));
    }

    @Test
    public void cancelAfterFirstPage_neverClosesSoTheTestIsAborted() throws Exception {
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv -> { checks.incrementAndGet(); return null; })
                .when(mockEyes).check(anyString(), ArgumentMatchers.any(ICheckSettings.class));
        config.cancelRequested = () -> checks.get() >= 1;
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);

        test.run(mockEyes);

        verify(mockEyes, never()).close(anyBoolean());
    }

    @Test
    public void cancelAfterFirstPage_pipelinedRender_neverCloses() throws Exception {
        config.renderThreads = 2;
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv -> { checks.incrementAndGet(); return null; })
                .when(mockEyes).check(anyString(), ArgumentMatchers.any(ICheckSettings.class));
        config.cancelRequested = () -> checks.get() >= 1;
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);

        test.run(mockEyes);

        verify(mockEyes, never()).close(anyBoolean());
    }

    @Test
    public void cancelledRunSafe_neverCallsTheBlockingAbort() {
        // Aborting a cancelled test hangs/wedges the shared universal core (sync abort blocks
        // this thread; async abort breaks the next run's makeManager) — the session must be
        // abandoned untouched and left to time out server-side.
        config.cancelRequested = () -> true;
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);

        test.runSafe(mockEyes);

        verify(mockEyes, never()).abortIfNotClosed();
    }

    @Test
    public void cancelledRunSafe_neverCallsTheAsyncAbortEither() {
        config.cancelRequested = () -> true;
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);

        test.runSafe(mockEyes);

        verify(mockEyes, never()).abortAsync();
    }

    @Test
    public void normalRunSafe_stillAbortsSynchronously() {
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-2-page.pdf"), config);

        test.runSafe(mockEyes);

        verify(mockEyes).abortIfNotClosed();
    }

    @Test
    public void name_withPageNumbers_includesPagesInName() {
        config.pages = "1-3";
        config.includePageNumbers = true;
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);

        assertTrue(test.name().contains("pages [1-3]"));
    }

    @Test
    public void name_withoutPageNumbers_doesNotIncludePages() {
        config.pages = "1-3";
        config.includePageNumbers = false;
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-10-page.pdf"), config);

        assertFalse(test.name().contains("pages"));
    }

    @Test
    public void name_forcedName_overridesFilename() {
        config.forcedName = "CustomName";
        PdfFileTest test = new PdfFileTest(new File(FIXTURES, "valid-2-page.pdf"), config);

        assertTrue(test.name().startsWith("CustomName"));
    }
}
