package com.applitools.imagetester.lib;

import org.junit.Test;
import static org.junit.Assert.*;

public class PatternsTest {

    @Test
    public void image_matchesJpg() {
        assertTrue(Patterns.IMAGE.matcher("photo.jpg").matches());
    }

    @Test
    public void image_matchesJpeg() {
        assertTrue(Patterns.IMAGE.matcher("photo.jpeg").matches());
    }

    @Test
    public void image_matchesPng() {
        assertTrue(Patterns.IMAGE.matcher("photo.png").matches());
    }

    @Test
    public void image_matchesGif() {
        assertTrue(Patterns.IMAGE.matcher("photo.gif").matches());
    }

    @Test
    public void image_matchesBmp() {
        assertTrue(Patterns.IMAGE.matcher("photo.bmp").matches());
    }

    @Test
    public void image_matchesTif() {
        assertTrue(Patterns.IMAGE.matcher("photo.tif").matches());
    }

    @Test
    public void image_matchesTiff() {
        assertTrue(Patterns.IMAGE.matcher("photo.tiff").matches());
    }

    @Test
    public void image_caseInsensitive() {
        assertTrue(Patterns.IMAGE.matcher("PHOTO.JPG").matches());
        assertTrue(Patterns.IMAGE.matcher("photo.PNG").matches());
        assertTrue(Patterns.IMAGE.matcher("photo.Tiff").matches());
    }

    @Test
    public void image_rejectsPdf() {
        assertFalse(Patterns.IMAGE.matcher("file.pdf").matches());
    }

    @Test
    public void image_rejectsTxt() {
        assertFalse(Patterns.IMAGE.matcher("file.txt").matches());
    }

    @Test
    public void image_rejectsNoName() {
        assertFalse(Patterns.IMAGE.matcher(".jpg").matches());
    }

    @Test
    public void image_rejectsDoubleExtension() {
        assertFalse(Patterns.IMAGE.matcher("file.jpgg").matches());
    }

    @Test
    public void pdf_matchesPdf() {
        assertTrue(Patterns.PDF.matcher("document.pdf").matches());
    }

    @Test
    public void pdf_caseInsensitive() {
        assertTrue(Patterns.PDF.matcher("DOCUMENT.PDF").matches());
        assertTrue(Patterns.PDF.matcher("document.Pdf").matches());
    }

    @Test
    public void pdf_rejectsImage() {
        assertFalse(Patterns.PDF.matcher("photo.jpg").matches());
    }

    @Test
    public void pdf_rejectsPartialMatch() {
        assertFalse(Patterns.PDF.matcher("file.pdfx").matches());
    }

    @Test
    public void pdf_rejectsNoName() {
        assertFalse(Patterns.PDF.matcher(".pdf").matches());
    }
}
