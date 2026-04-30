package com.applitools.imagetester.lib.converters;

import org.junit.Test;
import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkipTrackerTest {

    @Test
    public void newTrackerIsEmpty() {
        SkipTracker tracker = new SkipTracker();
        assertTrue(tracker.isEmpty());
        assertFalse(tracker.hasLibreOfficeMissing());
    }

    @Test
    public void recordLibreOfficeMissingFlagsIt() {
        SkipTracker tracker = new SkipTracker();
        tracker.record(new File("a.docx"), SkipTracker.REASON_LIBREOFFICE_MISSING);
        assertTrue(tracker.hasLibreOfficeMissing());
    }

    @Test
    public void recordOtherReasonDoesNotFlagLibreOfficeMissing() {
        SkipTracker tracker = new SkipTracker();
        tracker.record(new File("a.docx"), "conversion failed: soffice exited 127");
        assertFalse(tracker.hasLibreOfficeMissing());
    }

    @Test
    public void skipsReturnsRecordsInInsertionOrder() {
        SkipTracker tracker = new SkipTracker();
        tracker.record(new File("a.docx"), "reason-a");
        tracker.record(new File("b.pptx"), "reason-b");
        List<SkipTracker.SkipRecord> records = tracker.skips();
        assertEquals(2, records.size());
        assertEquals("a.docx", records.get(0).file.getName());
        assertEquals("reason-a", records.get(0).reason);
        assertEquals("b.pptx", records.get(1).file.getName());
    }
}
