package com.applitools.imagetester.lib;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class PatternsMultiFormatTest {

    @Test
    public void textPatternMatchesTxtExtension() {
        assertTrue(Patterns.TEXT.matcher("notes.txt").matches());
    }

    @Test
    public void textPatternIsCaseInsensitive() {
        assertTrue(Patterns.TEXT.matcher("NOTES.TXT").matches());
    }

    @Test
    public void markdownPatternMatchesMdExtension() {
        assertTrue(Patterns.MARKDOWN.matcher("readme.md").matches());
    }

    @Test
    public void rtfPatternMatchesRtfExtension() {
        assertTrue(Patterns.RTF.matcher("note.rtf").matches());
    }

    @Test
    public void wordPatternMatchesAllOfficeWordVariants() {
        for (String ext : new String[]{"doc", "dot", "docx", "docm", "dotx", "dotm"}) {
            assertTrue("expected match for ." + ext,
                    Patterns.WORD.matcher("file." + ext).matches());
        }
    }

    @Test
    public void powerpointPatternMatchesAllPowerpointVariants() {
        for (String ext : new String[]{"ppt", "pptx", "pptm", "pps", "ppsx", "ppsm", "pot", "potx", "potm"}) {
            assertTrue("expected match for ." + ext,
                    Patterns.POWERPOINT.matcher("deck." + ext).matches());
        }
    }

    @Test
    public void vectorPatternMatchesPostscriptAndXps() {
        for (String ext : new String[]{"ps", "eps", "xps"}) {
            assertTrue("expected match for ." + ext,
                    Patterns.VECTOR.matcher("file." + ext).matches());
        }
    }

    @Test
    public void spreadsheetPatternMatchesAllSpreadsheetVariants() {
        for (String ext : new String[]{"xls", "xlsx", "xlsm", "xlt", "xltx", "xltm", "ods", "csv"}) {
            assertTrue("expected match for ." + ext,
                    Patterns.SPREADSHEET.matcher("data." + ext).matches());
        }
    }

    @Test
    public void spreadsheetPatternRejectsDocx() {
        assertFalse(Patterns.SPREADSHEET.matcher("file.docx").matches());
    }

    @Test
    public void textPatternRejectsPdf() {
        assertFalse(Patterns.TEXT.matcher("doc.pdf").matches());
    }

    @Test
    public void wordPatternRejectsPpt() {
        assertFalse(Patterns.WORD.matcher("deck.ppt").matches());
    }
}
