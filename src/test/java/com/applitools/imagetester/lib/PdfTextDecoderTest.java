package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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
        byte[] encoded;
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDType0Font noto = NotoFontProvider.load(doc);
            encoded = noto.encode("日本語");
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(noto, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("日本語");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            pdfBytes = out.toByteArray();
        }

        try (PDDocument reloaded = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDResources resources = reloaded.getPage(0).getResources();
            PDFont font = resources.getFont(resources.getFontNames().iterator().next());

            String decoded = PdfTextDecoder.decode(new COSString(encoded), font);

            assertEqualsNfkc("日本語", decoded);
        }
    }

    @Test
    public void should_decode_tj_array_skipping_kern_numbers() throws IOException {
        byte[] part1;
        byte[] part2;
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDType0Font noto = NotoFontProvider.load(doc);
            part1 = noto.encode("日本");
            part2 = noto.encode("語");
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(noto, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("日本語");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            pdfBytes = out.toByteArray();
        }

        try (PDDocument reloaded = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDResources resources = reloaded.getPage(0).getResources();
            PDFont font = resources.getFont(resources.getFontNames().iterator().next());
            COSArray array = new COSArray();
            array.add(new COSString(part1));
            array.add(COSInteger.get(-120));
            array.add(new COSString(part2));

            String decoded = PdfTextDecoder.decode(array, font);

            assertEqualsNfkc("日本語", decoded);
        }
    }

    /**
     * PDFBox builds ToUnicode CMaps by reverse cmap lookup; ideographs that share
     * a glyph with a Kangxi radical (e.g. 日 vs U+2F47) may decode to the radical
     * codepoint. NFKC folds radicals back to the unified ideographs.
     */
    private static void assertEqualsNfkc(String expected, String actual) {
        assertNotNull(actual);
        assertEquals(Normalizer.normalize(expected, Normalizer.Form.NFKC),
                Normalizer.normalize(actual, Normalizer.Form.NFKC));
    }
}
