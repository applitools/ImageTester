package com.applitools.imagetester.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.Normalizer;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
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

import com.applitools.imagetester.lib.testdata.NfTestPdfBuilder;

public class PdfTextDecoderTest {

    /**
     * A CID the subset font below never assigns to a real glyph. PDFBox's
     * auto-generated ToUnicode CMap only covers CIDs that were actually
     * encoded, so this one is guaranteed to have no Unicode mapping -
     * the "never-garble" contract's null case.
     */
    private static final int UNMAPPED_CID = 0xFFFE;

    @Test
    public void should_decode_simple_font_string() {
        PDFont helvetica = PDType1Font.HELVETICA;

        String decoded = PdfTextDecoder.decode(new COSString("Hello"), helvetica);

        assertEquals("Hello", decoded);
    }

    @Test
    public void should_return_null_for_null_string_or_font() {
        assertNull(PdfTextDecoder.decode((COSString) null, PDType1Font.HELVETICA));
        assertNull(PdfTextDecoder.decode(new COSString("x"), null));
    }

    @Test
    public void should_decode_type0_identity_h_string_roundtrip() throws IOException {
        byte[] encoded;
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDType0Font noto = NfTestPdfBuilder.loadNotoSans(doc);
            encoded = noto.encode("Subset text");
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(noto, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Subset text");
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

            assertEquals("Subset text", decoded);
        }
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
            PDType0Font noto = NfTestPdfBuilder.loadNotoSans(doc);
            part1 = noto.encode("Sub");
            part2 = noto.encode("set");
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(noto, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Subset");
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

            assertEquals("Subset", decoded);
        }
    }

    @Test
    public void should_decode_tj_array_japanese_payload() throws IOException {
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

    @Test
    public void should_return_null_when_code_has_no_unicode_mapping() throws IOException {
        byte[] pdfBytes = buildSubsetPdfWithUnmappedCid();

        try (PDDocument reloaded = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDResources resources = reloaded.getPage(0).getResources();
            PDFont font = resources.getFont(resources.getFontNames().iterator().next());
            byte[] unmappedBytes = { (byte) (UNMAPPED_CID >> 8), (byte) UNMAPPED_CID };

            String decoded = PdfTextDecoder.decode(new COSString(unmappedBytes), font);

            assertNull(decoded);
        }
    }

    /**
     * Builds a one-page PDF with an embedded Noto Sans subset, drawn through a
     * raw content stream: real text first (so the subset and its ToUnicode CMap
     * look like an ordinary document), then a raw CID the subsetter never
     * assigned to a glyph and therefore never mapped in the auto-generated
     * ToUnicode CMap. showText()/PDPageContentStream can't produce this - both
     * validate every codepoint against the font's cmap - so the payload is
     * written directly to the content stream.
     */
    private byte[] buildSubsetPdfWithUnmappedCid() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font noto = NfTestPdfBuilder.loadNotoSans(doc);
            byte[] realText = noto.encode("Subset text");
            byte[] unmappedBytes = { (byte) (UNMAPPED_CID >> 8), (byte) UNMAPPED_CID };

            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("F1"), noto);
            page.setResources(resources);

            COSStream contentStream = new COSStream();
            try (OutputStream out = contentStream.createOutputStream()) {
                out.write("BT /F1 12 Tf 72 700 Td (".getBytes("ISO-8859-1"));
                writeEscapedLiteral(out, realText);
                writeEscapedLiteral(out, unmappedBytes);
                out.write(") Tj ET".getBytes("ISO-8859-1"));
            }
            page.getCOSObject().setItem(COSName.CONTENTS, contentStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void writeEscapedLiteral(OutputStream out, byte[] bytes) throws IOException {
        for (byte b : bytes) {
            int unsigned = b & 0xff;
            if (unsigned == '(' || unsigned == ')' || unsigned == '\\') {
                out.write('\\');
            }
            out.write(unsigned);
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
