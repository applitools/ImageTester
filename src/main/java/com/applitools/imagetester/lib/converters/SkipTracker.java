package com.applitools.imagetester.lib.converters;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SkipTracker {

    public static final String REASON_LIBREOFFICE_MISSING = "LibreOffice not found";

    public static final class SkipRecord {
        public final File file;
        public final String reason;
        SkipRecord(File file, String reason) {
            this.file = file;
            this.reason = reason;
        }
    }

    private final List<SkipRecord> records = new CopyOnWriteArrayList<>();

    public void record(File file, String reason) {
        records.add(new SkipRecord(file, reason));
    }

    public List<SkipRecord> skips() {
        return Collections.unmodifiableList(records);
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    public boolean hasLibreOfficeMissing() {
        for (SkipRecord r : records) {
            if (REASON_LIBREOFFICE_MISSING.equals(r.reason)) return true;
        }
        return false;
    }
}
