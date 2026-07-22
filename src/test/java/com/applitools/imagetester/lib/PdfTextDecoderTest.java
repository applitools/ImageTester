package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Test;

public class PdfTextDecoderTest {

    @Test
    public void should_decode_simple_font_string() {
        PDFont helvetica = PDType1Font.HELVETICA;

        String decoded = PdfTextDecoder.decode(new COSString("Hello"), helvetica);

        assertEquals("Hello", decoded);
    }

    @Test
    public void should_decode_type0_japanese_string_roundtrip() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font noto = NotoFontProvider.load(doc);
            COSString encoded = new COSString(noto.encode("日本語"));

            String decoded = PdfTextDecoder.decode(encoded, noto);

            assertEquals("日本語", decoded);
        }
    }

    @Test
    public void should_decode_tj_array_skipping_kern_numbers() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font noto = NotoFontProvider.load(doc);
            COSArray array = new COSArray();
            array.add(new COSString(noto.encode("日本")));
            array.add(COSInteger.get(-120));
            array.add(new COSString(noto.encode("語")));

            String decoded = PdfTextDecoder.decode(array, noto);

            assertEquals("日本語", decoded);
        }
    }
}
