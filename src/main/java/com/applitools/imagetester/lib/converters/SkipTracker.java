package com.applitools.imagetester.lib.converters;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SkipTracker {

    public static final String REASON_LIBREOFFICE_MISSING = "LibreOffice not found";

    /** Printed whenever a run needed LibreOffice and couldn't find it; shared by the folder-scan
     *  skip summary and the compare-mode failure message so both tell the user the same thing. */
    public static final String LIBREOFFICE_INSTALL_HINT =
            "Install LibreOffice to enable support for .doc/.docx/.ppt/.pptx and other office formats:\n"
            + "  macOS:   brew install --cask libreoffice\n"
            + "  Windows: winget install TheDocumentFoundation.LibreOffice\n"
            + "  Linux:   sudo apt-get install libreoffice";

    public static final String REASON_POSTSCRIPT_XPS_UNSUPPORTED =
            "PostScript/XPS conversion is not supported - convert the file to PDF first";

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
