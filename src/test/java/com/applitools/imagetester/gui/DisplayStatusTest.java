package com.applitools.imagetester.gui;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.TestResultsStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Maps Eyes TestResults to the display status shown in the GUI Tests list. */
public class DisplayStatusTest {

    private static TestResults resultWithStatus(TestResultsStatus status) {
        TestResults r = mock(TestResults.class);
        when(r.getStatus()).thenReturn(status);
        return r;
    }

    @Test
    public void nullResultMapsToError() {
        assertEquals("error", RunController.toDisplayStatus(null));
    }

    @Test
    public void abortedResultMapsToAborted() {
        TestResults r = resultWithStatus(TestResultsStatus.Unresolved);
        when(r.isAborted()).thenReturn(true);
        assertEquals("aborted", RunController.toDisplayStatus(r));
    }

    @Test
    public void newBaselineMapsToNew() {
        TestResults r = resultWithStatus(TestResultsStatus.Unresolved);
        when(r.isNew()).thenReturn(Boolean.TRUE);
        assertEquals("new", RunController.toDisplayStatus(r));
    }

    @Test
    public void unresolvedMapsToMismatch() {
        assertEquals("mismatch", RunController.toDisplayStatus(resultWithStatus(TestResultsStatus.Unresolved)));
    }

    @Test
    public void failedMapsToFailed() {
        assertEquals("failed", RunController.toDisplayStatus(resultWithStatus(TestResultsStatus.Failed)));
    }

    @Test
    public void passedMapsToPassed() {
        assertEquals("passed", RunController.toDisplayStatus(resultWithStatus(TestResultsStatus.Passed)));
    }

    @Test
    public void nullStatusWithDiffsFallsBackToMismatch() {
        TestResults r = mock(TestResults.class);
        when(r.isDifferent()).thenReturn(true);
        assertEquals("mismatch", RunController.toDisplayStatus(r));
    }

    @Test
    public void nullStatusWithoutDiffsFallsBackToPassed() {
        TestResults r = mock(TestResults.class);
        when(r.isDifferent()).thenReturn(false);
        assertEquals("passed", RunController.toDisplayStatus(r));
    }
}
